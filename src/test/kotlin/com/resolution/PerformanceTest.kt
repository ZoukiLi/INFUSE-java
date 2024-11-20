package com.resolution

import com.CC.Constraints.Rules.RuleHandler
import com.CC.Contexts.ContextPool
import com.CC.Middleware.Checkers.PCC
import com.constraint.resolution.Context
import com.constraint.resolution.PatternMap
import com.constraint.resolution.fromCCFormula
import com.constraint.resolution.makeContext
import com.constraint.resolution.toContextChanges
import java.time.Duration
import kotlin.test.Test
import kotlin.time.toKotlinDuration

// Simple performance test to compare the performance
// forall a in A
//     forall b in B
//         forall c in C
//             a.x = b.x = c.x
class PerformanceTest {
    private fun generatePatternMap(number: Int, errorRate: Double): PatternMap {
        // Normal x = 0, but with error rate x = 1
        val patternMap = mapOf("A" to mutableSetOf<Context>(), "B" to mutableSetOf())
        for (i in 0 until number) {
            val contextA = makeContext(mapOf("x" to if (i == 0) "1" else "0"))
            patternMap["A"]?.add(contextA)
            val contextB = makeContext(mapOf("x" to if (i == 2) "1" else "0"))
            patternMap["B"]?.add(contextB)
        }
        return patternMap
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

    @Test
    fun performanceTest() {
        val data = generatePatternMap(9, 0.01)
        val fmlPath = "src/test/resources/PerformanceTest/formula.xml"
        val ruleHandler = RuleHandler()
        ruleHandler.buildRules(fmlPath)
        val pool = initPool(ruleHandler)
        val checker = PCC(ruleHandler, pool, DefaultBfunc, false)
        checker.checkInit()

        val checkStart = System.currentTimeMillis()
        toContextChanges(data).forEach {
            checker.ctxChangeCheckIMD(it)
        }
        val checkEnd = System.currentTimeMillis()
        displayTime(checkStart, checkEnd, "Check time")

        val formula = fromCCFormula(ruleHandler.ruleMap["rule1"]!!.formula)
        val cctNode = ruleHandler.ruleMap["rule1"]!!.cctRoot
        val repairStart = System.currentTimeMillis()
        val node = formula.createRCTNode(mapOf(), data, cctNode)
        val result = node.repairF2T()
        val repairEnd = System.currentTimeMillis()
        println("${result.cases.size} repair cases")
        displayTime(repairStart, repairEnd, "Repair time")

        val repairStart2 = System.currentTimeMillis()
        val result2 = formula.repairF2T(mapOf(), data)
        val repairEnd2 = System.currentTimeMillis()
        println("${result2.cases.size} repair cases")
        displayTime(repairStart2, repairEnd2, "Repair time 2")
    }

    private fun displayTime(start: Long, end: Long, message: String) {
        val duration = Duration.ofMillis(end - start)
        println("$message: ${duration.toKotlinDuration()}")
    }
}