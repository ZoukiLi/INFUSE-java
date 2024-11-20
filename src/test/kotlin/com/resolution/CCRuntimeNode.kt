package com.resolution

import com.CC.Constraints.Rules.RuleHandler
import com.CC.Constraints.Runtime.RuntimeNode
import com.CC.Contexts.ContextPool
import com.CC.Middleware.Checkers.PCC
import com.constraint.resolution.fromCCFormula
import com.constraint.resolution.makeContext
import com.constraint.resolution.toContextChanges
import java.io.File
import kotlin.test.Test


class CCRuntimeNode {
    val testDir = "src/test/resources/CCRuntimeTest/"

    // test patterns
    val patternA = setOf(
        makeContext(mapOf("name" to "a1", "x" to "0", "y" to "0", "z" to "0")),
        makeContext(mapOf("name" to "a2", "x" to "0", "y" to "1", "z" to "1"))
    )
    val patternB = setOf(
        makeContext(mapOf("name" to "b1", "x" to "1", "y" to "0", "z" to "0")),
        makeContext(mapOf("name" to "b2", "x" to "1", "y" to "1", "z" to "1"))
    )
    val patternMap = mapOf("A" to patternA, "B" to patternB)
    val ctxChanges = toContextChanges(patternMap)

    // Bfunc Object
    object DefaultBfunc {
        fun bfunc(funcName: String, vcMap: Map<String, Map<String, String>>): Boolean {
            return when (funcName) {
                "equal_const_x_0" -> vcMap["var1"]?.get("x") == "0"
                "equal_const_y_1" -> vcMap["var1"]?.get("y") == "1"
                else -> throw IllegalArgumentException("Unsupported function: $funcName")
            }
        }
    }

    @Test
    fun cctNodeTest() {
        val fmlPath = "${testDir}/formula.xml"
        val resultPath = "${testDir}/results.txt"
        val resultFile = File(resultPath)
        if (resultFile.exists()) resultFile.delete()
        val ruleHandler = RuleHandler()
        ruleHandler.buildRules(fmlPath)
        val pool = initPool(ruleHandler)
        val checker = PCC(ruleHandler, pool, DefaultBfunc, false)
        checker.checkInit()
        ctxChanges.forEach {
            checker.ctxChangeCheckIMD(it)
            println("Context change: $it")
        }
        checker.ruleHandler.ruleMap.forEach {
            println("Rule: ${it.key}")
            println(printNodeTree(it.value.cctRoot))
            resultFile.appendText("Rule: ${it.key}\n")
            resultFile.appendText(printNodeTree(it.value.cctRoot).toString())
        }
        checker.ruleHandler.ruleMap.forEach {
            println("Rule: ${it.key}")
            resultFile.appendText("Rule: ${it.key}\n")

            val cctNode = it.value.cctRoot
            val formula = fromCCFormula(it.value.formula)
            val node = formula.createRCTNode(mapOf(), patternMap, cctNode)
            val evalResult = node.getTruth()
            assert(evalResult == cctNode.isTruth)

            println("Truth value: $evalResult")
            resultFile.appendText("Truth value: $evalResult\n")

            if (!evalResult) {
                val repairSuite = node.repairF2T()

                println("Repair suite:")
                println(repairSuite.display())

                resultFile.appendText("Repair suite:\n")
                resultFile.appendText(repairSuite.display())
                resultFile.appendText("\n")
            }
        }
    }

    private fun printNodeTree(node: RuntimeNode, depth: Int = 0): StringBuilder {
        val sb = StringBuilder()
        for (i in 0 until depth) {
            sb.append("  ")
        }
        sb.append("Node: ${node.formula.formula_type} ")
        sb.appendLine(node.isTruth)
        node.children.forEach {
            sb.append(printNodeTree(it, depth + 1))
        }
        return sb
    }

    private fun initPool(ruleHandler: RuleHandler): ContextPool {
        val contextPool = ContextPool()
        ruleHandler.ruleMap.forEach {
            contextPool.poolInit(it.value)
        }
        return contextPool
    }
}