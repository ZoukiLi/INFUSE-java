package com.constraint.resolution

import com.CC.Constraints.Rules.RuleHandler
import java.io.File
import kotlin.test.Test


class IFormulaKtTest {
    val testDir = "src/test/resources/IFormulaTest/"

    @Test
    fun smoke_test() {
        val fmlPath = "$testDir/smoke/formula1.xml"
        val resultPath = "$testDir/smoke/results.txt"
        val resultFile = File(resultPath)
        if (resultFile.exists()) resultFile.delete()
        val ruleHandler = RuleHandler()
        ruleHandler.buildRules(fmlPath)
        val patternA = setOf(
            makeContext(mapOf("x" to "1", "y" to "2", "z" to "1")),
            makeContext(mapOf("x" to "2", "y" to "1", "z" to "1"))
        )
        val patternB = setOf(
            makeContext(mapOf("x" to "1", "y" to "2", "z" to "2")),
            makeContext(mapOf("x" to "2", "y" to "1", "z" to "2"))
        )
        val patternMap = mapOf("A" to patternA, "B" to patternB)
        ruleHandler.ruleMap.forEach {
            println("Rule: ${it.key}")
            val formula = fromCCFormula(it.value.formula, null)
            val node = formula.createRCTNode(mapOf(), patternMap)
            val evalResult = node.getTruth()
            assert(evalResult == formula.evaluate(mapOf(), patternMap))
            println("Truth value: $evalResult")
            val repairSuite =
                when (evalResult) {
                    true -> node.repairT2F()
                    false -> node.repairF2T()
                }

            resultFile.appendText("Rule: ${it.key}\n")
            resultFile.appendText("Truth value: $evalResult\n")
            resultFile.appendText("Repair suite:\n")
            resultFile.appendText(repairSuite.display())
            resultFile.appendText("\n")
        }
    }

    @Test
    fun test_csv_dirs() {
        mapOf(
            "car_simp" to "car",
            "car_simp_filter" to "car",
            "filter_test" to "A",
        ).forEach { test_csv_dir(it.key, it.value) }
    }

    private fun test_csv_dir(testName: String, patternName: String) {
        val patternFiles =
            File("$testDir/$testName").listFiles { _, name -> name.startsWith("data") and name.endsWith(".csv") }
        val patternMaps = patternFiles?.map { it.nameWithoutExtension to read_csv_pattern_map(patternName, it.path) }

        val fmlFile = "$testDir/$testName/formula.xml"
        val ruleHandler = RuleHandler()
        ruleHandler.buildRules(fmlFile)

        val resultDir = File("$testDir/$testName/results")
        if (!resultDir.exists()) resultDir.mkdirs()

        patternMaps?.forEach { pair ->
            println("Pattern File: ${pair.first}")
            val resultPath = "$testDir/$testName/results/${pair.first}.txt"
            val resultFile = File(resultPath)
            if (resultFile.exists()) resultFile.delete()
            val patternMap = pair.second
            ruleHandler.ruleMap.forEach {
                val result = evaluate_and_display(it.key, fromCCFormula(it.value.formula, null), patternMap)
                println(result)
                resultFile.appendText(result)
                resultFile.appendText("\n")
            }
        }
    }
}

private fun evaluate_and_display(ruleName: String, formula: IFormula, patternMap: Map<String, Pattern>): String {
    val node = formula.createRCTNode(mapOf(), patternMap)
    val evalResult = node.getTruth()
    var result = "Rule: $ruleName\n"
    result += "Truth value: $evalResult\n"
    if (evalResult) return result
    val repairSuite = node.repairF2T()
    result += "Repair suite:\n"
    result += repairSuite.display()
    return result
}

private fun read_csv_pattern_map(patternName: String, patternPath: String): Map<String, Pattern> {
    val patternFile = File(patternPath)
    val patternLines = patternFile.readLines()
    val patternHeader = patternLines.first().split(",")
    val patternData = patternLines.drop(1)
    return mapOf(patternName to patternData.map {
        makeContext(patternHeader.zip(it.split(",")).toMap())
    }.toSet())
}
