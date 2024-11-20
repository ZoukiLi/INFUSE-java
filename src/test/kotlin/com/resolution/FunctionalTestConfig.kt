package com.resolution

import com.CC.Constraints.Formulas.FExists
import com.CC.Constraints.Formulas.FForall
import com.CC.Constraints.Formulas.Formula
import com.CC.Constraints.Rules.RuleHandler
import com.CC.Constraints.Runtime.RuntimeNode
import com.CC.Contexts.ContextChange
import com.CC.Contexts.ContextPool
import com.CC.Middleware.Checkers.Checker
import com.CC.Middleware.Checkers.PCC
import com.constraint.resolution.Context
import com.constraint.resolution.IFormula
import com.constraint.resolution.MutablePatternMap
import com.constraint.resolution.PatternMap
import com.constraint.resolution.RepairSuite
import com.constraint.resolution.applyContextChange
import com.constraint.resolution.applyRepairSuite
import com.constraint.resolution.firstCase
import com.constraint.resolution.fromCCFormula
import com.constraint.resolution.makeContext
import com.constraint.resolution.toChanges
import com.constraint.resolution.toContextChanges
import java.io.File
import kotlin.test.Test


private const val testDir = "src/test/resources/FunctionalTest/"

class FunctionalTestConfig(val subDir: String) {
    lateinit var checker: Checker
    lateinit var patternMap: MutablePatternMap
    lateinit var formulaMap: Map<String, IFormula>
    val resultFile = File("${testDir}${subDir}/results.txt")

    // Bfunc Object
    object DefaultBfunc {
        fun bfunc(funcName: String, vcMap: Map<String, Map<String, String>>): Boolean {
            return when (funcName) {
                "equal_const_x_0" -> vcMap["var1"]?.get("x") == "0"
                "equal_const_x_1" -> vcMap["var1"]?.get("x") == "1"
                "equal_const_x_2" -> vcMap["var1"]?.get("x") == "2"
                "equal_const_y_0" -> vcMap["var1"]?.get("y") == "0"
                "equal_const_y_1" -> vcMap["var1"]?.get("y") == "1"
                "equal_const_y_2" -> vcMap["var1"]?.get("y") == "2"
                "equal_const_z_0" -> vcMap["var1"]?.get("z") == "0"
                "equal_const_z_1" -> vcMap["var1"]?.get("z") == "1"
                "equal_const_z_2" -> vcMap["var1"]?.get("z") == "2"
                "equal_x_x" -> vcMap["var1"]?.get("x") == vcMap["var2"]?.get("x")
                "equal_y_y" -> vcMap["var1"]?.get("y") == vcMap["var2"]?.get("y")
                "equal_z_z" -> vcMap["var1"]?.get("z") == vcMap["var2"]?.get("z")
                else -> throw IllegalArgumentException("Unsupported function: $funcName")
            }
        }
    }

    fun setupChecker() {
        val fmlPath = "${testDir}${subDir}/formula.xml"
        val ruleHandler = RuleHandler()
        ruleHandler.buildRules(fmlPath)
        val pool = initPool(ruleHandler)
        checker = PCC(ruleHandler, pool, DefaultBfunc, false)
        checker.checkInit()
        var formulaMap = mutableMapOf<String, IFormula>()
        checker.ruleHandler.ruleMap.forEach {
            formulaMap[it.key] = fromCCFormula(it.value.formula)
        }
        this.formulaMap = formulaMap

        if (resultFile.exists()) resultFile.delete()
    }

    private fun initPool(ruleHandler: RuleHandler): ContextPool {
        val pool = ContextPool()
        ruleHandler.ruleMap.forEach {
            pool.poolInit(it.value)
        }
        return pool
    }

    fun readCSVPatterns(): PatternMap {
        // get all csv files in the subDir
        val csvFiles = File("${testDir}${subDir}").listFiles { _, name -> name.endsWith(".csv") }
        val patternMap = mutableMapOf<String, Set<Context>>()
        csvFiles?.forEach {
            val patternName = it.nameWithoutExtension
            val patternLines = it.readLines()
            val patternHeader = patternLines.first().split(",")
            val pattern = patternLines.drop(1).map { line ->
                val values = line.split(",")
                makeContext(patternHeader.zip(values).toMap())
            }.toSet()
            patternMap[patternName] = pattern
        }
        // this.patternMap keep the name, but set the value to empty mutable set
        this.patternMap = patternMap.mapValues { mutableSetOf() }
        return patternMap
    }

    fun applyChange(change: ContextChange) {
        checker.ctxChangeCheckIMD(change)
        applyContextChange(patternMap, change)
        resultFile.appendText("$change\n")
    }

    fun displayEvaluation() {
        checker.ruleHandler.ruleMap.forEach {
            resultFile.appendText("Rule: ${it.key}\n")
            resultFile.appendText(displayCCT(it.value.cctRoot, null))
        }
    }

    fun repairRule(ruleName: String): RepairSuite? {
        val formula = formulaMap[ruleName] ?: throw IllegalArgumentException("Rule not found: $ruleName")
        val cctNode = checker.ruleHandler.ruleMap[ruleName]?.cctRoot
            ?: throw IllegalArgumentException("CCT node not found for rule: $ruleName")
        val node = formula.createRCTNode(mapOf(), patternMap, cctNode)
        val evalResult = node.getTruth()
        assert(evalResult == cctNode.isTruth)

        if (evalResult) {
            return null
        }
        return node.repairF2T()
    }

    fun applyRepair(ruleName: String, repairSuite: RepairSuite) {
        patternMap.applyRepairSuite(repairSuite)
        resultFile.appendText("Rule: $ruleName\n")
        resultFile.appendText("Repair suite:\n")
        resultFile.appendText(repairSuite.display())
        resultFile.appendText("\n")

        // adopt the first repair case
        resultFile.appendText("Adopting repair case\n")
        val changes = repairSuite.firstCase()?.toChanges() ?: emptyList()
        changes.forEach {
            checker.ctxChangeCheckIMD(it)
            resultFile.appendText("$it\n")
        }
    }
}

fun displayCCT(node: RuntimeNode, displayVar: String?, depth: Int = 0): String {
    val sb = StringBuilder()
    sb.append(" ".repeat(depth * 2))
    if (displayVar != null) {
        val varEnv = node.varEnv[displayVar]
        if (varEnv?.ctx_fields["name"] != null) {
            sb.append("($displayVar = ${varEnv.ctx_fields["name"]}) ")
        }
    }

    var varName: String? = null
    val type = node.formula.formula_type
    if (type == Formula.Formula_Type.FORALL) {
        varName = (node.formula as FForall).`var`
    } else if (type == Formula.Formula_Type.EXISTS) {
        varName = (node.formula as FExists).`var`
    }

    sb.appendLine("$type ${varName ?: ""}: ${node.isTruth}")
    node.children.forEach {
        sb.append(displayCCT(it, varName, depth + 1))
    }
    return sb.toString()
}

class FunctionalTest {

    @Test
    fun test_tree() {
        val test = FunctionalTestConfig("tree")
        test.setupChecker()
        val patMap = test.readCSVPatterns()
        toContextChanges(patMap).forEach { test.applyChange(it) }
        test.displayEvaluation()
        test.checker.ruleHandler.ruleMap.forEach {
            val repairSuite = test.repairRule(it.key)
            if (repairSuite != null) {
                test.applyRepair(it.key, repairSuite)
            }
        }
        test.displayEvaluation()
    }
    private val tests = listOf("and", "or", "not", "implies")
    @Test
    fun test_formulas() {
        tests.forEach { test_formula(it) }
    }

    private fun test_formula(subDir: String) {
        val test = FunctionalTestConfig(subDir)
        test.setupChecker()
        val patMap = test.readCSVPatterns()
        toContextChanges(patMap).forEach { test.applyChange(it) }
        test.displayEvaluation()
        test.checker.ruleHandler.ruleMap.forEach {
            val repairSuite = test.repairRule(it.key)
            test.resultFile.appendText("Rule: ${it.key}\n")
            if (repairSuite != null) {
                test.resultFile.appendText("Repair suite:\n")
                test.resultFile.appendText(repairSuite.display())
            }
        }
    }
}
