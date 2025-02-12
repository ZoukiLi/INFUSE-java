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
import com.constraint.resolution.ContextManager
import com.constraint.resolution.IFormula
import com.constraint.resolution.RemovalRepairAction
import com.constraint.resolution.RepairCase
import com.constraint.resolution.RepairSuite
import com.constraint.resolution.fromCCFormula
import java.io.File
import kotlin.test.Test


private const val testDir = "src/test/resources/FunctionalTest/"

class FunctionalTestConfig(val subDir: String, val baseDir: String = testDir) {
    lateinit var checker: Checker
    lateinit var formulaMap: Map<String, IFormula>
    val manager = ContextManager()
    val resultFile = File("${baseDir}${subDir}/results.txt")

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
        val fmlPath = "${baseDir}${subDir}/formula.xml"
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
        val dataFile = File("${baseDir}${subDir}/data.csv")
        val lines = dataFile.readLines()
        val header = lines[0].split(",")
        val patIndex = header.indexOf("pattern")
        val attrIndices = header.filter { it != "pattern" }.map { header.indexOf(it) }

        return lines.subList(1, lines.size).flatMap { line ->
            val values = line.split(",")
            val pattern = values[patIndex]
            val attributes = attrIndices.associate { header[it] to values[it] }
            val context = manager.constructContext(attributes)
            nameToContext.put(context.attributes["name"]?.first?:"", context)
            manager.addContextToPattern(context, listOf(pattern))
        }
    }

    val nameToContext = mutableMapOf<String, Context>()

    fun context(name: String): Context {
        return nameToContext[name] ?: throw IllegalArgumentException("Context not found: $name")
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
        formula.repairF2T(mapOf(), manager.patternMap)
        return node.repairF2T()
    }

    fun repairRuleSeq(ruleName: String): Sequence<RepairCase> {
        val formula = formulaMap[ruleName] ?: throw IllegalArgumentException("Rule not found: $ruleName")
        return formula.repairF2TSeq(mapOf(), manager.patternMap)
    }

    fun applyRepair(ruleName: String, repairSuite: Sequence<RepairCase>) {
        resultFile.appendText("Rule: $ruleName\n")
//        val tree = displayCCT(checker.ruleHandler.ruleMap[ruleName]?.cctRoot!!, null)
//        resultFile.appendText(tree)
        resultFile.appendText("Testing Repair Cases:\n")

        // for each repair case in repair suite
        for ((i, case) in repairSuite.withIndex()) {
            resultFile.appendText("Case $i.\n")
            resultFile.appendText(case.display())
            resultFile.appendText("\n\n")
            val applyChanges = case.applyTo(manager)
            applyChanges.forEach {
                checker.ctxChangeCheckIMD(it)
    //                resultFile.appendText("$it\n")
            }
            // check evaluation
            val truth = checker.ruleHandler.ruleMap[ruleName]?.cctRoot?.isTruth
            //            val tree = displayCCT(checker.ruleHandler.ruleMap[ruleName]?.cctRoot!!, null)
            //            resultFile.appendText(tree)
            //            assert(truth == true)
            if (truth == true) {
                resultFile.appendText("PASSED\n")
            } else {
                resultFile.appendText("FAILED\n")
            }
            // reverse changes
            val reverseChanges = case.reverse(manager)
            reverseChanges.forEach {
                checker.ctxChangeCheckIMD(it)
    //                resultFile.appendText("$it\n")
            }
            resultFile.appendText("----------\n")
            if (truth == true) {
                break
            }
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
        test_formula("tree")
    }

    @Test
    fun testVerifyNode() {
        val test = FunctionalTestConfig("verify")

        test.setupChecker()
        test.readCSVPatterns().forEach { test.applyChange(it) }

        println(test.nameToContext)
        val cases = listOf(
            RepairCase(RemovalRepairAction(test.context("a1"), "A"), .0),
            RepairCase(setOf(RemovalRepairAction(test.context("a2"), "A"), RemovalRepairAction(test.context("a3"), "A")), .0),
            RepairCase(setOf(RemovalRepairAction(test.context("a1"), "A"), RemovalRepairAction(test.context("a4"), "A")), .0),
        )
        test.checker.ruleHandler.ruleMap.forEach {
            val formula = test.formulaMap[it.key] ?: throw IllegalArgumentException("Rule not found: ${it.key}")
            val cctNode = it.value.cctRoot
            val verifyNode = formula.initVerifyNode(cctNode)
            println("Rule: ${it.key}")
            cases.forEachIndexed { i, case ->
                println("Case $i: ${verifyNode.checkCase(case)}")
            }
        }
    }

    fun test_formula(name: String) {
        val test = FunctionalTestConfig(name)
        test.setupChecker()
        test.readCSVPatterns().forEach { test.applyChange(it) }
        test.displayEvaluation()
        test.checker.ruleHandler.ruleMap.forEach {
            val repairSeq = test.repairRuleSeq(it.key)
            test.applyRepair(it.key, repairSeq)
            val repairSuite = test.repairRule(it.key)
            test.resultFile.appendText("Rule: ${it.key}\n")
            test.resultFile.appendText("----Repair suite----\n")
            if (repairSuite != null && repairSuite.cases.isNotEmpty()) {
                test.resultFile.appendText(repairSuite.display() + "\n")
                test.applyRepair(it.key, repairSuite.cases.asSequence())
            } else {
                test.resultFile.appendText("No repair suite found.\n\n")
            }
//            test.resultFile.appendText("----Lazy repair----\n")
//            val repairSeq = test.repairRuleSeq(it.key)
//            repairSeq.forEachIndexed { i, case ->
//                test.resultFile.appendText("Case $i.\n")
//                test.resultFile.appendText(case.display())
//                test.resultFile.appendText("\n\n")
//            }
        }
        // test.displayEvaluation()
    }

    private val tests = listOf("and", "or", "not", "implies", "tree", "nested")

    @Test
    fun test_formulas() {
        tests.forEach { test_formula(it) }
    }

//    private fun test_formula(subDir: String) {
//        val test = FunctionalTestConfig(subDir)
//        println("Testing $subDir")
//        test.setupChecker()
//        test.readCSVPatterns().forEach { test.applyChange(it) }
//        // test.displayEvaluation()
//        test.checker.ruleHandler.ruleMap.forEach {
//            println("Testing rule: ${it.key}")
//            val repairSeq = test.repairRuleSeq(it.key)
//            test.applyRepair(it.key, repairSeq)
//        }
//    }
}
