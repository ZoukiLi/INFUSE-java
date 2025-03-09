package com.constraint.resolution

import com.CC.Constraints.Rules.RuleHandler
import com.CC.Contexts.ContextChange
import com.CC.Contexts.ContextPool
import com.CC.Middleware.Checkers.PCC
import java.io.File
import java.time.Duration
import kotlin.test.Test
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.*

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

    private val testSizes = (2..18 step 2) + (20..120 step 20)

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
            val skipRules2 = maxSize2.filter { it.value <= size }.keys
            val result = performanceTest("data_${size}", resultFile, skipRules, skipRules2)
            val oom1 = result.first
            val tmo1 = result.second
            oom1.forEach {
                maxSize[it] = size
            }
            tmo1.forEach {
                maxSize2[it] = size
            }
            resultFile.appendText("\n")

            resultFile.appendText("--- Test data: data_${size}(one_side) ---\n")
            val skipRulesOneSide = maxSizeOneSide.filter { it.value <= size }.keys
            val skipRulesOneSide2 = maxSizeOneSide2.filter { it.value <= size }.keys
            val result2 = performanceTest("data_${size}_one_side", resultFile, skipRulesOneSide, skipRulesOneSide2)
            val oom2 = result2.first
            val tmo2 = result2.second
            oom2.forEach {
                maxSizeOneSide[it] = size
            }
            tmo2.forEach {
                maxSizeOneSide2[it] = size
            }
        }
        println("Max size:")
        maxSize.toList().sortedBy { it.first }.forEach {
            println("${it.first} to ${it.second},")
        }
        println("Max size (one side):")
        maxSizeOneSide.toList().sortedBy { it.first }.forEach {
            println("${it.first} to ${it.second},")
        }
    }

    private val maxSize: MutableMap<String, Int> = mutableMapOf(
        "rule_nested" to 12,
    )
    private val maxSize2: MutableMap<String, Int> = mutableMapOf()
    private val maxSizeOneSide: MutableMap<String, Int> = mutableMapOf()
    private val maxSizeOneSide2: MutableMap<String, Int> = mutableMapOf()

    fun performanceTest(testData: String, resultFile: File, skipRules1: Set<String>, skipRules2: Set<String>): Pair<Set<String>, Set<String>> {
        println("Test data: $testData")
        resultFile.appendText(
            String.format(
                "%-26s%-16s%-11s%-16s%-11s\n",
                "rule",
                "size(collected)",
                "size(lazy)",
                "time(collected)",
                "time(lazy)"
            )
        )
//        resultFile.appendText("rule,\tsize(recursive),\tsize(lazy),\ttime(recursive),\ttime(lazy)\n")

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
        val timeout = 20*60*1000L
        val tmoRules = mutableSetOf<String>()
        checker.ruleHandler.ruleMap
            .toList().sortedBy { it.first }.forEach {
                println("Repairing rule: ${it.first}")
                val formula = fromCCFormula(it.second.formula, manager)
                val cctNode = it.second.cctRoot
                val data = manager.patternMap
                val node = formula.createRCTNode(mapOf(), data, cctNode)
                var size = -1
                var size2 = -1
                var t1 = measureTime("Repair time (tree)") {
                    // val result = node.repairF2T()
                    // size = result.cases.size
                }
                var t2 = -1L
                if (!skipRules1.contains(it.first)) {
                    t2 = measureTimeWithTimeout("Repair time (recursive)", timeout) {
                        try {
                            val r2 = formula.repairF2T(mapOf(), data)
                            size = r2.cases.count()
                        } catch (e: OutOfMemoryError) {
                            println("Out of memory: $e")
                            oomRules.add(it.first)
                        }
                    }
                    if (size == -1) {
                        t2 = -1
                    }
                }
                var t3 = -1L
                if (!skipRules2.contains(it.first)) {
                    t3 = measureTimeWithTimeout("Repair time (lazy)", timeout) {
                        try {
                            val r3 = formula.repairF2TSeq(mapOf(), data)
                            size2 = r3.count()
                        } catch (e: OutOfMemoryError) {
                            println("Out of memory: $e")
                            oomRules.add(it.first)
                        }
                    }
                }
                if (t3 == timeout) {
                    tmoRules.add(it.first)
                    t3 = -1
                }
                resultFile.appendText(
                    String.format(
                        "%-26s%-16s%-11s%-16s%-11s\n",
                        it.first,
                        size,
                        size2,
                        t2,
                        t3
                    )
                )
            }
        return oomRules to tmoRules
    }

    private fun displayTime(start: Long, end: Long, message: String) {
        val duration = Duration.ofMillis(end - start)
        println("$message: ${duration.toKotlinDuration()}")
    }

    // do something and measure time
    private fun measureTime(description: String, block: () -> Unit): Long {
        val start = System.currentTimeMillis()
        block()
        val end = System.currentTimeMillis()
        displayTime(start, end, description)
        return end - start
    }
    // Measure time with timeout
    private fun measureTimeWithTimeoutCoroutine(
        description: String,
        timeoutMillis: Long,
        block: suspend () -> Unit
    ): Long {
        var result = 0L
        val start = System.currentTimeMillis()
        runBlocking {
            try {
                withTimeout(timeoutMillis) {
                    block()
                }
            } catch (e: TimeoutCancellationException) {
                println("Function exceeded $timeoutMillis ms: $description")
            }
        }
        val end = System.currentTimeMillis()
        displayTime(start, end, description)
        result = end - start
        return result
    }
    fun measureTimeWithTimeout(description: String, timeoutMillis: Long, block: () -> Unit): Long {
        val start = System.currentTimeMillis()
        val thread = Thread {
            block()
        }
        thread.start()
        thread.join(timeoutMillis)
        if (thread.isAlive) {
            thread.interrupt()
            println("Function exceeded $timeoutMillis ms: $description")
        }
        val end = System.currentTimeMillis()
        displayTime(start, end, description)
        return end - start
    }

    @Test
    fun testRepairSeq() {
        testSizes.forEach { size ->
            val testConfig = FunctionalTestConfig("data_${size}")
            testConfig.setupChecker()
            testConfig.readCSVPatterns().forEach { testConfig.applyChange(it) }
            
            testConfig.checker.ruleHandler.ruleMap.forEach { ruleEntry ->
                val ruleName = ruleEntry.key
                val repairSeq = testConfig.repairRuleSeq(ruleName)
                testConfig.applyRepair(ruleName, repairSeq)
            }
        }
    }

}