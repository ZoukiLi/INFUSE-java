package com.constraint.resolution.formulas

import com.CC.Constraints.Rules.RuleHandler
import com.constraint.resolution.Context
import com.constraint.resolution.ContextManager
import com.constraint.resolution.bfunc.BFuncParser
import com.constraint.resolution.bfunc.BFuncDefinition
import com.constraint.resolution.bfunc.BFuncRegistry
import com.constraint.resolution.fromCCFormula
import com.constraint.resolution.genZ3PyCode
import com.constraint.resolution.runPyCode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BFuncFormulaTest {
    
    private val testJsonLessThan = """
    {
        "name": "less_than_x_y",
        "params": ["var1", "var2"],
        "body": {
            "kind": "binary",
            "operator": "<",
            "args": [
                {
                    "kind": "accessor",
                    "context": "var1",
                    "attribute": "x",
                    "valueType": "int"
                },
                {
                    "kind": "accessor",
                    "context": "var2",
                    "attribute": "y",
                    "valueType": "int"
                }
            ]   
        }
    }
    """.trimIndent()
    
    @Test
    fun testBFuncFormulaEvaluation() {
        // 创建BFuncDefinition
        val parser = BFuncParser()
        val bfunc = parser.parse(testJsonLessThan)
        val definition = BFuncDefinition(bfunc)
        
        // 创建BFuncFormula
        val formula = BFuncFormula(
            funcName = "less_than_x_y",
            parameters = mapOf(
                "var1" to "a",
                "var2" to "b"
            ),
            expression = definition.parseExpression(),
            manager = null
        )
        
        // 创建上下文
        val context1 = Context(1, mapOf("x" to ("5" to true), "name" to ("a" to true)), null)
        val context2 = Context(2, mapOf("y" to ("10" to true), "name" to ("b" to true)), null)
        val context3 = Context(3, mapOf("y" to ("3" to true), "name" to ("c" to true)), null)
        
        // 测试条件成立的情况
        val assignment1 = mapOf("a" to context1, "b" to context2)
        assertTrue(formula.evaluate(assignment1, emptyMap()))
        
        // 测试条件不成立的情况
        val assignment2 = mapOf("a" to context1, "b" to context3)
        assertFalse(formula.evaluate(assignment2, emptyMap()))
    }
    
    @Test
    fun testBFuncRegistry() {
        // 创建注册表
        val registry = BFuncRegistry()
        
        // 注册函数定义
        val parser = BFuncParser()
        val bfunc = parser.parse(testJsonLessThan)
        val definition = BFuncDefinition(bfunc)
        registry.registerBFunc(definition)
        
        // 获取函数定义
        val retrievedDef = registry.getBFuncDefinition("less_than_x_y")
        assertEquals("less_than_x_y", retrievedDef?.name)
    }

}

class BFuncBaseTest {

    private val testPath = "src/test/resources/TransTest/"
    private val xmlPath = "src/test/resources/TransTest/rules_ori.xml"
    private val csvPath = "src/test/resources/TransTest/data.csv"
    private val resultPyPath = "src/test/resources/bfunc/result.py"
    private val resultPath = "src/test/resources/TransTest/result.txt"

    private var rstFile = File(resultPath)
    private var pyFile = File(resultPyPath)

    fun initResult() {
        if (rstFile.exists()) {
            rstFile.delete()
        }
        if (pyFile.exists()) {
            pyFile.delete()
        }
    }

    @Test
    fun testParse() {
        val handler = RuleHandler()
        handler.buildRules(xmlPath)
        val bfuncRegistry = BFuncRegistry()
        bfuncRegistry.loadFromDirectory(testPath)

        val manager = ContextManager()
        val formula = fromCCFormula(handler.ruleMap["rule1"]!!.formula, null, bfuncRegistry)

        loadFromCSV(csvPath, manager)

        val eval = formula.evaluate(mapOf(), manager.patternMap)
        println("Eval: $eval")

        println("Z3CondTrue:")
        println(formula.Z3CondTrue(mapOf(), manager.patternMap))
    }

    fun loadFromCSV(csvPath: String, manager: ContextManager) {
        val csvFile = File(csvPath)
        // get data.csv
        // format:
        // pattern,attribute1,attribute2,attribute3
        // A,value1,value2,value3
        // A,value4,value5,value6
        val lines = csvFile.readLines()
        val header = lines[0].split(",")
        val patIndex = header.indexOf("pattern")
        val attrIndices = header.filter { it != "pattern" }.map { header.indexOf(it) }

        lines.subList(1, lines.size).filter { it.startsWith("#") }.filterNot { it.isBlank() }
            .forEach { line ->
            val values = line.split(",")
            val pattern = values[patIndex].trim('#')
            val attributes = attrIndices.associate { header[it] to values[it] }
            val context = manager.constructContext(attributes)
            manager.addContextToPattern(context, listOf(pattern))
        }
    }

    @Test
    fun testRepairF2TDrop() {
        initResult()
        val handler = RuleHandler()
        handler.buildRules(xmlPath)
        val bfuncRegistry = BFuncRegistry()
        bfuncRegistry.loadFromDirectory(testPath)

        val manager = ContextManager()
        loadFromCSV(csvPath, manager)

        handler.ruleMap.forEach { name, it ->
            val formula = fromCCFormula(it.formula, manager, bfuncRegistry)
            val eval = formula.evaluate(mapOf(), manager.patternMap)
            println("$name: $eval")
            if (!eval) {
                val repairSuite = formula.repairF2TSeq(mapOf(), manager.patternMap)
                println("Repair suite: $repairSuite")
                if (repairSuite.any()) {
                    val cases = repairSuite.take(10)
                    val case = cases.minBy { it.actions.size }
                    println(case.display())
                    rstFile.appendText(case.display())
                    rstFile.appendText("\n")
                }
            }
        }
    }

    @Test
    fun testRepairF2T() {
        initResult()
        // 与原来的testParse()相同的设置
        val handler = RuleHandler()
        handler.buildRules(xmlPath)
        val bfuncRegistry = BFuncRegistry()
        bfuncRegistry.loadFromDirectory(testPath)

        val manager = ContextManager()
        val formula = fromCCFormula(handler.ruleMap["rule_order1"]!!.formula, null, bfuncRegistry)

        loadFromCSV(csvPath, manager)

        // 评估公式
        val eval = formula.evaluate(mapOf(), manager.patternMap)
        println("Formula evaluation result: $eval")

        // 如果公式为假，尝试修复
        if (!eval) {
            val repairSuite = formula.repairF2TSeq(mapOf(), manager.patternMap)

            println("Generated repair suite:")
            if (repairSuite.any()) {
                // 输出修复动作
                val case = repairSuite.first()
                println(case.display())
                val list = repairSuite.take(1000).sortedBy { it.actions.size }
                list.take(10).forEachIndexed { index, repairCase ->
                    rstFile.appendText("Case $index:\n")
                    rstFile.appendText(repairCase.display())
                    rstFile.appendText("\n")

                    if (repairCase.conflict()) {
                        rstFile.appendText("Conflict detected, skipping Z3 code generation\n")
                        return@forEachIndexed
                    }

                    // 保存Z3 Python代码
                    val pyCode = genZ3PyCode(formula, repairCase, manager.patternMap)
                    rstFile.appendText(pyCode)
                    rstFile.appendText("\n")

                    // 运行Z3代码
                    val result = runPyCode(pyCode)
                    rstFile.appendText("Z3 result:\n$result\n")

                    rstFile.appendText("\n")

                }
                // 保存Z3 Python代码

            } else {
                println("No repair actions generated")
            }
        } else {
            println("Formula is already true, no repair needed")
        }
    }
}