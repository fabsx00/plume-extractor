package za.ac.sun.plume

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import za.ac.sun.plume.TestConstants.testGraph
import za.ac.sun.plume.domain.enums.EdgeLabel
import za.ac.sun.plume.domain.enums.VertexLabel
import za.ac.sun.plume.drivers.DriverFactory
import za.ac.sun.plume.drivers.GraphDatabase
import za.ac.sun.plume.drivers.TinkerGraphDriver
import za.ac.sun.plume.util.TestQueryBuilderUtil
import java.io.File

class ExtractorTest {
    @AfterEach
    fun tearDown() {
        driver.clearGraph()
    }

    @Test
    fun validSourceFileTest() {
        extractor.load(validSourceFile)
        extractor.project()
        driver.exportGraph(TEST_GRAPH)
    }

    @Test
    fun validClassFileTest() {
        extractor.load(validClassFile)
        extractor.project()
        driver.exportGraph(TEST_GRAPH)
    }

    @Test
    fun validDirectoryTest() {
        extractor.load(validDirectory)
        extractor.project()
        driver.exportGraph(TEST_GRAPH)
    }

    @Disabled
    @Test
    fun validJarTest() {
        val g = TinkerGraph.open().traversal()
        extractor.load(validJarFile)
        extractor.project()
        driver.exportGraph(TEST_GRAPH)
        g.io<Any>(TEST_GRAPH).read().iterate()

        // This is za.ac.sun.plume.intraprocedural.Basic6's test in a JAR
        val intraNamespaceTraversal = g.V().has(VertexLabel.NAMESPACE_BLOCK.toString(), "fullName", "intraprocedural")
        assertTrue(intraNamespaceTraversal.hasNext())
        val intraNamespaceVertex = intraNamespaceTraversal.next()
        val basicNamespaceTraversal = TestQueryBuilderUtil.getVertexAlongEdge(g, EdgeLabel.AST, intraNamespaceVertex, VertexLabel.NAMESPACE_BLOCK, "fullName", "intraprocedural.basic")
        assertTrue(basicNamespaceTraversal.hasNext())
        val basicNamespaceVertex = basicNamespaceTraversal.next()
        val basic6NamespaceTraversal = TestQueryBuilderUtil.getVertexAlongEdge(g, EdgeLabel.AST, basicNamespaceVertex, VertexLabel.NAMESPACE_BLOCK, "fullName", "intraprocedural.basic.basic6")
        assertTrue(basic6NamespaceTraversal.hasNext())
        val basic6NamespaceVertex = basic6NamespaceTraversal.next()
        val basicMethodTraversal = TestQueryBuilderUtil.getVertexAlongEdge(g, EdgeLabel.AST, basicNamespaceVertex, VertexLabel.METHOD, "name", "main")
        assertTrue(basicMethodTraversal.hasNext())
        val basic6MethodTraversal = TestQueryBuilderUtil.getVertexAlongEdge(g, EdgeLabel.AST, basic6NamespaceVertex, VertexLabel.METHOD, "name", "main")
        assertTrue(basic6MethodTraversal.hasNext())
        assertEquals(6, TestQueryBuilderUtil.buildStoreTraversal(g, EdgeLabel.AST, intraNamespaceVertex).count().next())
    }

    @Test
    fun validPy2Test() {
        extractor.load(validPy2File)
        extractor.project()
        driver.exportGraph(TEST_GRAPH)
    }

    @Test
    fun validJsTest() {
        extractor.load(validJsFile)
        extractor.project()
        driver.exportGraph(TEST_GRAPH)
    }

    @Test
    fun loadFileThatDoesNotExistTest() {
        Assertions.assertThrows(NullPointerException::class.java) { extractor.load(File("dne.class")) }
    }

    companion object {
        private val logger: Logger = LogManager.getLogger(ExtractorTest::class.java)
        private val TEST_GRAPH = testGraph
        private val TEST_PATH = "extractor_tests${File.separator}"
        private lateinit var CLS_PATH: File
        private lateinit var extractor: Extractor
        private lateinit var validSourceFile: File
        private lateinit var validClassFile: File
        private lateinit var validDirectory: File
        private lateinit var validJarFile: File
        private lateinit var validPy2File: File
        private lateinit var validJsFile: File
        private lateinit var driver: TinkerGraphDriver

        private fun getTestResource(dir: String): File {
            val resourceURL = ExtractorTest::class.java.classLoader.getResource(dir)
                    ?: throw java.lang.NullPointerException("Unable to obtain test resource")
            return File(resourceURL.file)
        }

        @BeforeAll
        @JvmStatic
        fun setUpAll() {
            validSourceFile = getTestResource("${TEST_PATH}Test1.java")
            validClassFile = getTestResource("${TEST_PATH}Test2.class")
            validJarFile = getTestResource("${TEST_PATH}Test3.jar")
            validPy2File = getTestResource("${TEST_PATH}Test4.py")
            validJsFile = getTestResource("${TEST_PATH}Test5.js")
            validDirectory = getTestResource("${TEST_PATH}dir_test")
            CLS_PATH = File(getTestResource(TEST_PATH).absolutePath.replace(System.getProperty("user.dir") + File.separator, "").removeSuffix(TEST_PATH.replace(File.separator, "")))
            driver = (DriverFactory(GraphDatabase.TINKER_GRAPH) as TinkerGraphDriver).apply { connect() }
            extractor = Extractor(driver, CLS_PATH)
        }

        @AfterAll
        @JvmStatic
        fun tearDownAll() {
            val f = File(TEST_GRAPH)
            if (f.exists() && !f.delete()) {
                logger.warn("Could not clear ${ExtractorTest::javaClass.name}'s test resources.")
            }
        }
    }
}