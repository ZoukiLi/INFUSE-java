package com.resolution

import com.CC.Constraints.Rules.RuleHandler
import com.CC.Contexts.ContextChange
import com.CC.Contexts.ContextPool
import com.CC.Middleware.Checkers.PCC
import com.constraint.resolution.ContextManager
import com.constraint.resolution.fromCCFormula
import java.io.File
import java.time.Duration
import kotlin.test.Test
import kotlin.time.toKotlinDuration

// Simple performance test to compare the performance
// Data like:
//pattern,name,x
//A,a1,0
//B,b1,0
//A,a2,0
//A,a3,0
//A,a4,1
//B,b2,0
//B,b3,0
class PerformanceTest {
    // generate data file for 'name.csv' with 'size' entries
    // if errorOneSide is true, then only one side of the equality will generate errors
    // when error, x is set to 1, otherwise 0, every 10th entry will have x set to 1
    fun genTestData(name: String, size: Int, errorOneSide: Boolean = false) {
        val dataFile = File("${testDir}/data/${name}.csv")
        if (!dataFile.parentFile.exists()) {
            dataFile.parentFile.mkdirs()
        }
        if (dataFile.exists()) dataFile.delete()
        dataFile.writeText("pattern,name,x\n")
        for (i in 0 until size) {
            val x = i % 10 == 0
            val a = if (x) 1 else 0
            val b = if (x && !errorOneSide) 1 else 0

            dataFile.appendText("A,a${i},${a}\n")
            dataFile.appendText("B,b${i},${b}\n")
        }
    }

    private val testSizes = (8..12 step 2) + (20..120 step 20)

    @Test
    fun genData() {
        testSizes.forEach {
            genTestData("data_${it}", it)
            genTestData("data_${it}_one_side", it, true)
        }
    }

    private fun initPool(ruleHandler: RuleHandler): ContextPool {
        val pool = ContextPool()
        ruleHandler.ruleMap.forEach {
            pool.poolInit(it.value)
        }
        return pool
    }

    object DefaultBfunc {
        fun bfunc(funcName: String, vcMap: Map<String, Map<String, String>>): Boolean {
            return when (funcName) {
                "equal_x_x" -> vcMap["var1"]?.get("x") == vcMap["var2"]?.get("x")
                "equal_const_x_0" -> vcMap["var1"]?.get("x") == "0"
                else -> throw IllegalArgumentException("Unsupported function: $funcName")
            }
        }
    }

    val testDir = "src/test/resources/PerformanceTest"
    fun readCSVPatterns(file: String, manager: ContextManager): List<ContextChange> {
        // get data.csv
        // format:
        // pattern,attribute1,attribute2,attribute3
        // A,value1,value2,value3
        // A,value4,value5,value6
        val dataFile = File("${testDir}/data/${file}.csv")
        val lines = dataFile.readLines()
        val header = lines[0].split(",")
        val patIndex = header.indexOf("pattern")
        val attrIndices = header.filter { it != "pattern" }.map { header.indexOf(it) }

        return lines.subList(1, lines.size).flatMap { line ->
            val values = line.split(",")
            val pattern = values[patIndex]
            val attributes = attrIndices.associate { header[it] to values[it] }
            val context = manager.constructContext(attributes)
            manager.addContextToPattern(context, listOf(pattern))
        }
    }

    fun printHeap() {
        val mb = 1024 * 1024
        val runtime = Runtime.getRuntime()
        println("##### Heap utilization statistics [MB] #####")
        println("Used Memory: " + (runtime.totalMemory() - runtime.freeMemory()) / mb)
        println("Free Memory: " + runtime.freeMemory() / mb)
        println("Total Memory: " + runtime.totalMemory() / mb)
        println("Max Memory: " + runtime.maxMemory() / mb)
    }

    @Test
    fun performanceTests() {
        genData()
        printHeap()
        testSizes.forEach { size ->
            val resultFile = File("${testDir}/result/size_${size}.txt")
            // make dir
            if (!resultFile.parentFile.exists()) {
                resultFile.parentFile.mkdirs()
            }
            if (resultFile.exists()) resultFile.delete()

            resultFile.appendText("--- Test data: data_${size} ---\n")
            val skipRules = maxSize.filter { it.value <= size }.keys
            val oom1 = performanceTest("data_${size}", resultFile, skipRules)
            oom1.forEach {
                maxSize[it] = size
            }
            resultFile.appendText("\n")

            resultFile.appendText("--- Test data: data_${size}(one_side) ---\n")
            val skipRulesOneSide = maxSizeOneSide.filter { it.value <= size }.keys
            val oom2 = performanceTest("data_${size}_one_side", resultFile, skipRulesOneSide)
            oom2.forEach {
                maxSizeOneSide[it] = size
            }
        }
        println("Max size:")
        maxSize.toList().sortedBy { it.first }.forEach {
            println("${it.first} to ${it.second}")
        }
        println("Max size (one side):")
        maxSizeOneSide.toList().sortedBy { it.first }.forEach {
            println("${it.first} to ${it.second}")
        }
    }

    private val maxSize: MutableMap<String, Int> = mutableMapOf()
    private val maxSizeOneSide: MutableMap<String, Int> = mutableMapOf()

    fun performanceTest(testData: String, resultFile: File, skipRules: Set<String>): Set<String> {
        println("Test data: $testData")
        resultFile.appendText("rule, size, time-tree(ms), time-recursive(ms)\n")

        val fmlPath = "${testDir}/formula.xml"
        val ruleHandler = RuleHandler()
        ruleHandler.buildRules(fmlPath)
        val pool = initPool(ruleHandler)
        val checker = PCC(ruleHandler, pool, DefaultBfunc, false)
        checker.checkInit()
        val manager = ContextManager()
        val changes = readCSVPatterns(testData, manager)

        measureTime("Check time") {
            changes.forEach {
                checker.ctxChangeCheckIMD(it)
            }
        }

        val oomRules = mutableSetOf<String>()
        checker.ruleHandler.ruleMap
            .filterNot { skipRules.contains(it.key) }
            .toList().sortedBy { pair -> pair.first }.forEach {
                println("Repairing rule: ${it.first}")
                val formula = fromCCFormula(it.second.formula, manager)
                val cctNode = it.second.cctRoot
                val data = manager.patternMap
                val node = formula.createRCTNode(mapOf(), data, cctNode)
                var size = 0
                try {
                    val t1 = measureTime("Repair time (tree)") {
                        val result = node.repairF2T()
                        size = result.cases.size
                    }
                    val t2 = measureTime("Repair time (recursive)") {
                        formula.repairF2T(mapOf(), data)
                    }
                    resultFile.appendText("${it.first}, ${size}, ${t1.toMillis()}, ${t2.toMillis()}\n")
                } catch (e: OutOfMemoryError) {
                    oomRules.add(it.first)
                    println("Out of memory: $e")
                    resultFile.appendText("${it.first}, OOM, OOM, OOM\n")
                }
            }
        return oomRules
    }

    private fun displayTime(start: Long, end: Long, message: String) {
        val duration = Duration.ofMillis(end - start)
        println("$message: ${duration.toKotlinDuration()}")
    }

    // do something and measure time
    private fun measureTime(description: String, block: () -> Unit): Duration {
        val start = System.currentTimeMillis()
        block()
        val end = System.currentTimeMillis()
        displayTime(start, end, description)
        return Duration.ofMillis(end - start)
    }
}