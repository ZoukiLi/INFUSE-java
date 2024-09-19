package com.resolution

import com.CC.Constraints.Rules.RuleHandler
import com.constraint.resolution.fromCCFormula
import com.constraint.resolution.makeContext
import kotlin.test.Test

const val testDir = "src/test/resources/IFormulaTest/"

class IFormulaKtTest {
    @Test
    fun load_formula() {
        val fmlFile = "$testDir/formula1.xml"
        val ruleHandler = RuleHandler()
        ruleHandler.buildRules(fmlFile)
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
            val formula = fromCCFormula(it.value.formula)
            val evalResult = formula.evaluate(mapOf(), patternMap)
            println("Truth value: $evalResult")
            val repairSuite =
                when (evalResult) {
                    true -> formula.repairT2F(mapOf(), patternMap)
                    false -> formula.repairF2T(mapOf(), patternMap)
                }
            println("Repair suite:")
            println(repairSuite.display())
        }

    }
}