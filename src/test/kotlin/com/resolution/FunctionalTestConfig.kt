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
import com.constraint.resolution.*
import java.io.File
import kotlin.test.Test


private const val testDir = "src/test/resources/FunctionalTest/"

class FunctionalTestConfig(val subDir: String) {
    lateinit var checker: Checker
    lateinit var formulaMap: Map<String, IFormula>
    val manager = ContextManager()
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
        formulaMap = checker.ruleHandler.ruleMap.mapValues { fromCCFormula(it.value.formula, manager) }

        if (resultFile.exists()) resultFile.delete()
    }

    private fun initPool(ruleHandler: RuleHandler): ContextPool {
        val pool = ContextPool()
        ruleHandler.ruleMap.forEach {
            pool.poolInit(it.value)
        }
        return pool
    }

    fun readCSVPatterns(): List<ContextChange> {
        // get data.csv
        // format:
        // pattern,attribute1,attribute2,attribute3
        // A,value1,value2,value3
        // A,value4,value5,value6
        val dataFile = File("${testDir}${subDir}/data.csv")
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

    fun applyChange(change: ContextChange) {
        checker.ctxChangeCheckIMD(change)
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
        val node = formula.createRCTNode(mapOf(), manager.patternMap, cctNode)
        val evalResult = node.getTruth()
        assert(evalResult == cctNode.isTruth)

        if (evalResult) {
            return null
        }
        return node.repairF2T()
    }

    fun applyRepair(ruleName: String, repairSuite: RepairSuite) {
        resultFile.appendText("Rule: $ruleName\n")
        resultFile.appendText("Repair suite:\n")
        resultFile.appendText(repairSuite.display())
        resultFile.appendText("\n")

        // adopt the first repair case
        resultFile.appendText("Adopting repair case\n")
        val changes = manager.applyRepairCase(repairSuite.firstCase())
        if (changes == null) {
            resultFile.appendText("No available repair case\n")
        }
        changes?.forEach {
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
        test.readCSVPatterns().forEach { test.applyChange(it) }
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
//        toContextChanges(patMap).forEach { test.applyChange(it) }
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
