package com.resolution

import com.CC.Constraints.Rules.RuleHandler
import com.CC.Contexts.ContextChange
import com.CC.Contexts.ContextPool
import com.CC.Middleware.Checkers.PCC
import com.constraint.resolution.Context
import com.constraint.resolution.ContextManager
import com.constraint.resolution.PatternMap
import com.constraint.resolution.fromCCFormula
import com.constraint.resolution.makeContext
import com.constraint.resolution.toContextChanges
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

    @Test
    fun genData() {
        genTestData("data_4", 4)
        genTestData("data_5", 5)
        genTestData("data_6", 6)
        genTestData("data_7", 7)
        genTestData("data_8", 8)
        genTestData("data_10", 10)
        genTestData("data_15", 15)
        genTestData("data_20", 20)
        genTestData("data_30", 30)
        genTestData("data_40", 40)
        genTestData("data_60", 60)
        genTestData("data_80", 80)
        genTestData("data_100", 100)
        genTestData("data_120", 120)

        genTestData("data_4_one_side", 4, true)
        genTestData("data_5_one_side", 5, true)
        genTestData("data_6_one_side", 6, true)
        genTestData("data_7_one_side", 7, true)
        genTestData("data_8_one_side", 8, true)
        genTestData("data_10_one_side", 10, true)
        genTestData("data_15_one_side", 15, true)
        genTestData("data_20_one_side", 20, true)
        genTestData("data_30_one_side", 30, true)
        genTestData("data_40_one_side", 40, true)
        genTestData("data_60_one_side", 60, true)
        genTestData("data_80_one_side", 80, true)
        genTestData("data_100_one_side", 100, true)
        genTestData("data_120_one_side", 120, true)
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

        return lines.subList(1, lines.size).flatMap{ line ->
            val values = line.split(",")
            val pattern = values[patIndex]
            val attributes = attrIndices.associate { header[it] to values[it] }
            val context = manager.constructContext(attributes)
            manager.addContextToPattern(context, listOf(pattern))
        }
    }
    @Test
    fun performanceTests() {
        genData()
        performanceTest("data_4", setOf())
        performanceTest("data_5", setOf())
//        performanceTest("data_6", setOf())
//        performanceTest("data_7", setOf())
        performanceTest("data_8", setOf())

        performanceTest("data_4_one_side", setOf())
        performanceTest("data_5_one_side", setOf())
//        performanceTest("data_6_one_side", setOf())
//        performanceTest("data_7_one_side", setOf())
        performanceTest("data_8_one_side", setOf())

        performanceTest("data_10", setOf())
        performanceTest("data_10_one_side", setOf())
//        performanceTest("data_15", setOf("rule_nested"))
//        performanceTest("data_15_one_side", setOf("rule_nested"))
        performanceTest("data_20", setOf("rule_nested"))
        performanceTest("data_20_one_side", setOf("rule_nested", "rule_nested_im"))
        performanceTest("data_30", setOf("rule_nested", "rule_nested_im"))
        performanceTest("data_30_one_side", setOf("rule_nested", "rule_nested_im"))

        performanceTest("data_40", setOf("rule_nested", "rule_nested_im"))
        performanceTest("data_40_one_side", setOf("rule_nested", "rule_nested_im"))
        performanceTest("data_60", setOf("rule_nested", "rule_nested_im"))
        performanceTest("data_60_one_side", setOf("rule_nested", "rule_nested_im"))
        performanceTest("data_80", setOf("rule_nested", "rule_nested_im"))
        performanceTest("data_80_one_side", setOf("rule_nested", "rule_nested_im"))
        performanceTest("data_100", setOf("rule_nested", "rule_nested_im"))
        performanceTest("data_100_one_side", setOf("rule_nested", "rule_nested_im"))
        performanceTest("data_120_one_side", setOf("rule_nested", "rule_nested_im"))
    }
    fun performanceTest(testData: String, exceptedRule: Set<String>) {
        println("Test data: $testData")
        val resultFile = File("${testDir}/result/${testData}_rst.txt")
        // make dir
        if (!resultFile.parentFile.exists()) {
            resultFile.parentFile.mkdirs()
        }
        if (resultFile.exists()) resultFile.delete()
        resultFile.appendText("rule, size, time(tree), time(recursive)\n")

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
        checker.ruleHandler.ruleMap
            .filterNot { exceptedRule.contains(it.key) }
            .forEach {
                println("Repairing rule: ${it.key}")
            val formula = fromCCFormula(it.value.formula, manager)
            val cctNode = it.value.cctRoot
            val data = manager.patternMap
            val node = formula.createRCTNode(mapOf(), data, cctNode)
            var size = 0
            val t1 = measureTime("Repair time (tree)") {
                val result = node.repairF2T()
                size = result.cases.size
            }
            val t2 = measureTime("Repair time (recursive)") {
                formula.repairF2T(mapOf(), data)
            }
            resultFile.appendText("${it.key}, ${size}, ${t1.toKotlinDuration()}, ${t2.toKotlinDuration()}\n")
        }
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