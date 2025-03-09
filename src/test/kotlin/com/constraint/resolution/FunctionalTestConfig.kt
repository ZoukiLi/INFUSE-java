package com.constraint.resolution

import com.CC.Constraints.Formulas.FExists
import com.CC.Constraints.Formulas.FForall
import com.CC.Constraints.Formulas.Formula
import com.CC.Constraints.Rules.RuleHandler
import com.CC.Constraints.Runtime.RuntimeNode
import com.CC.Contexts.ContextChange
import com.CC.Contexts.ContextPool
import com.CC.Middleware.Checkers.Checker
import com.CC.Middleware.Checkers.PCC
import java.io.File
import kotlin.test.Test
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}
private const val testDir = "src/test/resources/FunctionalTest/"

class FunctionalTestConfig(val subDir: String, val baseDir: String = testDir) {
    lateinit var checker: Checker
    lateinit var formulaMap: Map<String, IFormula>
    val manager = ContextManager()
    val resultFile = File("${baseDir}${subDir}/results.txt")

    internal fun writeToResult(vararg lines: String) {
        lines.forEach { line ->
            resultFile.appendText("$line\n")
        }
    }

    private fun writeToResultWithSeparator(lines: List<String>, separator: String = "----------") {
        lines.forEach { line ->
            writeToResult(line)
        }
        writeToResult(separator)
    }

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
            nameToContext.put(context.attributes["name"]?.first ?: "", context)
            manager.addContextToPattern(context, listOf(pattern))
        }
    }

    val nameToContext = mutableMapOf<String, Context>()

    fun context(name: String): Context {
        return nameToContext[name] ?: throw IllegalArgumentException("Context not found: $name")
    }

    fun applyChange(change: ContextChange) {
        checker.ctxChangeCheckIMD(change)
        writeToResult(change.toString())
        logger.debug { "Applied change: $change" }
    }

    fun displayEvaluation() {
        checker.ruleHandler.ruleMap.forEach {
            writeToResult("Rule: ${it.key}")
            writeToResult(displayCCT(it.value.cctRoot, null))
            logger.debug { "Displayed evaluation for rule: ${it.key}" }
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

    fun verifyCase(ruleName: String, case: RepairCase): Boolean {
        val formula = formulaMap[ruleName] ?: throw IllegalArgumentException("Rule not found: $ruleName")
        val cctNode = checker.ruleHandler.ruleMap[ruleName]?.cctRoot
            ?: throw IllegalArgumentException("CCT node not found for rule: $ruleName")
        val verifyNode = formula.initVerifyNode(cctNode)
        return verifyNode.checkCase(case, manager)
    }

    fun tryApplyCase(ruleName: String, case: RepairCase): Boolean {
        // apply changes and reverse changes
        case.applyTo(manager).forEach {
            checker.ctxChangeCheckIMD(it)
        }
        val truth = checker.ruleHandler.ruleMap[ruleName]?.cctRoot?.isTruth
        case.reverse(manager).forEach {
            checker.ctxChangeCheckIMD(it)
        }
        return truth == true
    }

    fun applyRepair(ruleName: String, repairSuite: Sequence<RepairCase>) {
        writeToResult("Rule: $ruleName")
        logger.debug { "Starting repair for rule: $ruleName" }
        writeToResult("Testing Repair Cases:")

        // for each repair case in repair suite
        for ((i, case) in repairSuite.withIndex()) {
            writeToResult(
                "Case $i.",
                case.display(),
                ""
            )
            val applyChanges = case.applyTo(manager)
            applyChanges.forEach {
                checker.ctxChangeCheckIMD(it)
                logger.debug { "Applied change: $it" }
            }
            // check evaluation
            val truth = checker.ruleHandler.ruleMap[ruleName]?.cctRoot?.isTruth
            if (truth == true) {
                writeToResult("PASSED")
                logger.info { "Repair case $i passed for rule: $ruleName" }
            } else {
                writeToResult("FAILED")
                logger.warn { "Repair case $i failed for rule: $ruleName" }
            }
            // reverse changes
            val reverseChanges = case.reverse(manager)
            reverseChanges.forEach {
                checker.ctxChangeCheckIMD(it)
                logger.debug { "Reversed change: $it" }
            }
            writeToResult("----------")
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

        logger.debug { "Context map: ${test.nameToContext}" }
        val newContext = test.manager.constructContext(mapOf("name" to "a5", "x" to "0"))
        val newContext2 = test.manager.constructContext(mapOf("name" to "a6", "x" to "1"))
        val cases = listOf(
            RepairCase(RemovalRepairAction(test.context("a1"), "A"), .0),
            RepairCase(
                setOf(
                    RemovalRepairAction(test.context("a2"), "A"),
                    RemovalRepairAction(test.context("a3"), "A")
                ), .0
            ),
            RepairCase(
                setOf(
                    RemovalRepairAction(test.context("a2"), "A"),
                    RemovalRepairAction(test.context("a4"), "A")
                ), .0
            ),
            RepairCase(
                setOf(
                    RemovalRepairAction(test.context("a1"), "A"),
                    RemovalRepairAction(test.context("a4"), "A")
                ), .0
            ),
            RepairCase(setOf(RemovalRepairAction(test.context("a1"), "A"), AdditionRepairAction(newContext, "A")), .0),
            RepairCase(
                setOf(
                    RemovalRepairAction(test.context("a1"), "A"),
                    RemovalRepairAction(test.context("a4"), "A"),
                    AdditionRepairAction(newContext, "A")
                ), .0
            ),
            RepairCase(
                setOf(
                    RemovalRepairAction(test.context("a1"), "A"),
                    RemovalRepairAction(test.context("a4"), "A"),
                    AdditionRepairAction(newContext2, "A")
                ), .0
            ),
        )
        test.displayEvaluation()
        test.checker.ruleHandler.ruleMap.forEach {
            val formula = test.formulaMap[it.key] ?: throw IllegalArgumentException("Rule not found: ${it.key}")
            val cctNode = it.value.cctRoot
            val verifyNode = formula.initVerifyNode(cctNode)
            logger.debug { "Rule: ${it.key}" }
            test.writeToResult("Rule: ${it.key}")
            cases.forEachIndexed { i, case ->
                val result = verifyNode.checkCase(case, test.manager)
                logger.debug { "Case $i: $result" }
                test.writeToResult("Case $i: $result")
            }
        }
    }

    @Test
    fun test_formula_relation() {
        test_formula_relation("tree")
    }

    fun test_formula_relation(name: String) {
        val test = FunctionalTestConfig(name)
        test.setupChecker()
        test.readCSVPatterns().forEach { test.applyChange(it) }
        test.displayEvaluation()
        val casesForRules =
            test.checker.ruleHandler.ruleMap.mapValues { rule ->
                val cases = test.repairRuleSeq(rule.key).toList()
                cases.take(20).filter {
                    test.writeToResult("Testing case:\n${it.display()}")
                    val rst = test.verifyCase(rule.key, it)
                    test.writeToResult("Result: $rst")
//                    val rst2 = test.tryApplyCase(rule.key, it)
//                    test.writeToResult("Result2: $rst2")
                    rst
                }.take(5).asSequence()
            }
        casesForRules.forEach { (ruleName, cases) ->
            test.writeToResult("Rule: $ruleName")
            cases.forEachIndexed { i, case ->
                test.writeToResult(
                    "Case $i.",
                    case.display()
                )
                logger.debug { "Processing case $i for rule $ruleName" }
            }
        }

        test.writeToResult("")
        val laterCases = mutableListOf<RepairCase>()
        cartesianProductCases(casesForRules.values.asSequence()).filterNot {
            test.writeToResult("${it.count()} cases")
            it.forEach {
                test.writeToResult(
                    it.display(),
                    "----"
                )
                logger.debug { "Processing case: ${it.display()}" }
            }
            test.writeToResult("")
            // conflict
            hasConflict(it) or it.none()
        }.forEach {
            if (countTotalEquals(it) > 0) {
                // test it first
                test.writeToResult("Testing equal cases(${countTotalEquals(it)}):")
                val testCase = it.reduce { acc, case -> acc and case }
                test.writeToResult(testCase.display())
                logger.debug { "Testing equal cases: ${testCase.display()}" }
                test.checker.ruleHandler.ruleMap.forEach {
                    val truth = test.tryApplyCase(it.key, testCase)
                    test.writeToResult("${it.key}: $truth")
                    logger.debug { "Rule ${it.key} result: $truth" }
                    if (truth) {
                        return@test_formula_relation
                    }
                }
            } else {
                laterCases.add(it.reduce { acc, case -> acc and case })
            }
        }
        laterCases.forEach { case ->
            test.writeToResult(
                "",
                "Testing Later Repair Cases:",
                case.display()
            )
            logger.debug { "Testing later repair case: ${case.display()}" }
            test.checker.ruleHandler.ruleMap.forEach {
                val truth = test.tryApplyCase(it.key, case)
                test.writeToResult("${it.key}: $truth")
                logger.debug { "Rule ${it.key} result: $truth" }
                if (truth) {
                    return@test_formula_relation
                }
            }
        }
        cartesianProduct(casesForRules.values.asSequence()).forEach { case ->
            test.writeToResult(
                "",
                "Testing Repair Cases:",
                case.display()
            )
            logger.debug { "Testing repair case: ${case.display()}" }
            test.checker.ruleHandler.ruleMap.forEach {
                val truth = test.tryApplyCase(it.key, case)
                test.writeToResult("${it.key}: $truth")
                logger.debug { "Rule ${it.key} result: $truth" }
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
            test.writeToResult(
                "Rule: ${it.key}",
                "----Repair suite----"
            )
            if (repairSuite != null && repairSuite.cases.isNotEmpty()) {
                test.writeToResult(repairSuite.display())
                test.applyRepair(it.key, repairSuite.cases.asSequence())
            } else {
                test.writeToResult("No repair suite found.", "")
            }
        }
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
