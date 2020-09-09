/*
 * Copyright 2020 David Baker Effendi
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package za.ac.sun.plume.graph

import org.apache.logging.log4j.LogManager
import soot.SootMethod
import soot.Unit
import soot.jimple.*
import soot.toolkits.graph.BriefUnitGraph
import za.ac.sun.plume.domain.enums.EdgeLabel
import za.ac.sun.plume.domain.models.PlumeVertex
import za.ac.sun.plume.domain.models.vertices.BlockVertex
import za.ac.sun.plume.domain.models.vertices.ControlStructureVertex
import za.ac.sun.plume.domain.models.vertices.JumpTargetVertex
import za.ac.sun.plume.domain.models.vertices.MethodReturnVertex
import za.ac.sun.plume.drivers.IDriver
import za.ac.sun.plume.util.ExtractorConst.FALSE_TARGET
import za.ac.sun.plume.util.ExtractorConst.TRUE_TARGET

/**
 * The [IGraphBuilder] that constructs the dependence edges in the graph.
 *
 * @param driver The driver to build the CFG with.
 * @param sootToPlume A pointer to the map that keeps track of the Soot object to its respective [PlumeVertex].
 */
class CFGBuilder(private val driver: IDriver, private val sootToPlume: MutableMap<Any, MutableList<PlumeVertex>>) : IGraphBuilder {
    private val logger = LogManager.getLogger(CFGBuilder::javaClass)
    private lateinit var graph: BriefUnitGraph
    private lateinit var currentMethod: SootMethod

    override fun build(mtd: SootMethod, graph: BriefUnitGraph) {
        logger.debug("Building CFG for ${mtd.declaration}")
        this.graph = graph
        this.currentMethod = mtd
        // Connect entrypoint to the first CFG vertex
        this.graph.heads.forEach { head ->
            graph.getSuccsOf(head).firstOrNull()?.let {
                driver.addEdge(
                        fromV = sootToPlume[mtd]?.first { mtdVertices -> mtdVertices is BlockVertex }!!,
                        toV = sootToPlume[it]?.first()!!,
                        edge = EdgeLabel.CFG
                )
            }
        }
        // Connect all units to their successors
        this.graph.body.units.forEach { projectUnit(it) }
    }

    private fun projectUnit(unit: Unit) {
        when (unit) {
            is GotoStmt -> projectUnit(unit.target)
            is IfStmt -> projectIfStatement(unit)
            is LookupSwitchStmt -> projectLookupSwitch(unit)
            is TableSwitchStmt -> projectTableSwitch(unit)
            is ReturnStmt -> projectReturnEdge(unit)
            is ReturnVoidStmt -> projectReturnEdge(unit)
            else -> {
                val sourceUnit = if (unit is GotoStmt) unit.target else unit
                val sourceVertex = sootToPlume[sourceUnit]?.firstOrNull()
                graph.getSuccsOf(sourceUnit).forEach {
                    val targetUnit = if (it is GotoStmt) it.target else it
                    if (sourceVertex != null) {
                        sootToPlume[targetUnit]?.let { vList -> driver.addEdge(sourceVertex, vList.first(), EdgeLabel.CFG) }
                    }
                }
            }
        }
    }

    private fun projectTableSwitch(unit: TableSwitchStmt) {
        val switchVertices = sootToPlume[unit]!!
        val switchVertex = switchVertices.first { it is ControlStructureVertex } as ControlStructureVertex
        // Handle default target jump
        projectSwitchDefault(unit, switchVertices, switchVertex)
        // Handle case jumps
        unit.targets.forEach { tgt ->
            val i = unit.targets.indexOf(tgt)
            if (unit.defaultTarget != tgt) projectSwitchTarget(switchVertices, i, switchVertex, tgt)
        }
    }

    private fun projectLookupSwitch(unit: LookupSwitchStmt) {
        val lookupVertices = sootToPlume[unit]!!
        val lookupVertex = lookupVertices.first { it is ControlStructureVertex } as ControlStructureVertex
        // Handle default target jump
        projectSwitchDefault(unit, lookupVertices, lookupVertex)
        // Handle case jumps
        for (i in 0 until unit.targetCount) {
            val tgt = unit.getTarget(i)
            val lookupValue = unit.getLookupValue(i)
            if (unit.defaultTarget != tgt) projectSwitchTarget(lookupVertices, lookupValue, lookupVertex, tgt)
        }
    }

    private fun projectSwitchTarget(lookupVertices: MutableList<PlumeVertex>, lookupValue: Int, lookupVertex: ControlStructureVertex, tgt: Unit) {
        val tgtV = lookupVertices.first { it is JumpTargetVertex && it.argumentIndex == lookupValue }
        driver.addEdge(lookupVertex, tgtV, EdgeLabel.CFG)
        sootToPlume[tgt]?.let { vList ->
            driver.addEdge(tgtV, vList.first(), EdgeLabel.CFG)
        }
    }

    private fun projectSwitchDefault(unit: SwitchStmt, switchVertices: MutableList<PlumeVertex>, switchVertex: ControlStructureVertex) {
        unit.defaultTarget.let { defaultUnit ->
            val tgtV = switchVertices.first { it is JumpTargetVertex && it.name == "DEFAULT" }
            driver.addEdge(switchVertex, tgtV, EdgeLabel.CFG)
            sootToPlume[defaultUnit]?.let { vList ->
                driver.addEdge(tgtV, vList.first(), EdgeLabel.CFG)
            }
        }
    }

    private fun projectIfStatement(unit: IfStmt) {
        val ifVertices = sootToPlume[unit]!!
        graph.getSuccsOf(unit).forEach {
            val srcVertex = if (it == unit.target) {
                ifVertices.first { vert -> vert is JumpTargetVertex && vert.name == FALSE_TARGET }
            } else {
                ifVertices.first { vert -> vert is JumpTargetVertex && vert.name == TRUE_TARGET }
            }
            val tgtVertices = if (it is GotoStmt) sootToPlume[it.target]
            else sootToPlume[it]
            tgtVertices?.let { vList ->
                driver.addEdge(ifVertices.first(), srcVertex, EdgeLabel.CFG)
                driver.addEdge(srcVertex, vList.first(), EdgeLabel.CFG)
            }
        }
    }

    private fun projectReturnEdge(unit: ReturnStmt) {
        sootToPlume[unit]?.firstOrNull()?.let { src ->
            sootToPlume[currentMethod]?.filterIsInstance<MethodReturnVertex>()?.firstOrNull()?.let { tgt ->
                driver.addEdge(src, tgt, EdgeLabel.CFG)
            }
        }
    }

    private fun projectReturnEdge(unit: ReturnVoidStmt) {
        sootToPlume[unit]?.firstOrNull()?.let { src ->
            sootToPlume[currentMethod]?.filterIsInstance<MethodReturnVertex>()?.firstOrNull()?.let { tgt ->
                driver.addEdge(src, tgt, EdgeLabel.CFG)
            }
        }
    }
}