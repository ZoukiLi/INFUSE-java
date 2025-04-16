package com.constraint.resolution.bfunc

import com.constraint.resolution.*
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * BFuncExpression接口，表示可执行的表达式
 */
interface BFuncExpression {
    /**
     * 使用上下文环境评估表达式
     */
    fun evaluate(env: Map<Variable, Context?>): Boolean?
    
    /**
     * 使用Java Context环境评估表达式
     */
    fun evaluateWithCCContext(env: Map<Variable, com.CC.Contexts.Context?>): Boolean?
    
    /**
     * 生成使表达式为真或为假的Z3条件字符串
     * @param makeTrue 为true时生成使表达式为真的条件，为false时生成使表达式为假的条件
     * @param contextMap 变量和上下文的映射，用于生成变量标识符
     * @return Z3条件表达式字符串
     */
    fun toZ3Condition(makeTrue: Boolean, contextMap: Map<Variable, Context?> = mapOf()): String
    
    /**
     * 生成F2T修复方案
     * TODO: 待实现
     */
    fun generateRepairF2T(params: Map<String, Variable>, assignment: Assignment, weight: Double): RepairSuite {
        // 只需声明需要修改一个变量的
        return RepairSuite() // 暂时返回空修复方案
    }
    
    /**
     * 生成T2F修复方案
     * TODO: 待实现
     */
    fun generateRepairT2F(params: Map<String, Variable>, assignment: Assignment, weight: Double): RepairSuite {
        return RepairSuite() // 暂时返回空修复方案
    }
    
    /**
     * 生成F2T修复方案序列
     * TODO: 待实现
     */
    fun generateRepairF2TSeq(params: Map<String, Variable>, assignment: Assignment, weight: Double): Sequence<RepairCase> {
        return emptySequence() // 暂时返回空序列
    }
    
    /**
     * 生成T2F修复方案序列
     * TODO: 待实现
     */
    fun generateRepairT2FSeq(params: Map<String, Variable>, assignment: Assignment, weight: Double): Sequence<RepairCase> {
        return emptySequence() // 暂时返回空序列
    }
    
    /**
     * 应用修复方案到验证节点
     * TODO: 待实现
     */
    fun applyRepairCase(verifyNode: VerifyNode, repairCase: RepairCase, params: Map<String, Variable>) {
        // 暂时不做任何操作
    }

    /**
     * 获取所有context.attribute对，这些作为变量将来会被使用或者修改
     */
    fun collectAccessors(): Set<Pair<Pair<String, String>, ValueType>>

    /**
     * 获取所有含值的context.attribute对，这些作为变量条件送给Z3求解器
     */
    fun collectAccessorValues(params: Map<String, Variable>, assignment: Assignment): Set<Pair<Attribute, ValueType>> {
        return collectAccessors().map { (pr, cls) ->
            val (context, attribute) = pr
            val variable = params[context] ?: return@map null
            val contextObj = assignment[variable] ?: return@map null
            (contextObj to attribute) to cls
        }.filterNotNull().toSet()
    }
}

/**
 * 将BFuncParser中的Expression适配到BFuncExpression
 */
class AdapterBFuncExpression(private val expression: Expression) : BFuncExpression {
    override fun evaluate(env: Map<Variable, Context?>): Boolean? {
        return when (expression) {
            is BinaryExpression -> evaluateBinaryExpression(expression, env)
            is AccessorExpression -> evaluateAccessorExpression(expression, env)?.let { convertToBoolean(it) }
            is LiteralExpression -> convertToBoolean(evaluateLiteralExpression(expression))
        }
    }
    
    override fun evaluateWithCCContext(env: Map<Variable, com.CC.Contexts.Context?>): Boolean? {
        return when (expression) {
            is BinaryExpression -> evaluateBinaryExpressionWithCC(expression, env)
            is AccessorExpression -> evaluateAccessorExpressionWithCC(expression, env)?.let { convertToBoolean(it) }
            is LiteralExpression -> convertToBoolean(evaluateLiteralExpression(expression))
        }
    }
    
    private fun evaluateBinaryExpression(expression: BinaryExpression, env: Map<Variable, Context?>): Boolean? {
        val args = expression.args.map { 
            when (it) {
                is BinaryExpression -> evaluateBinaryExpression(it, env)
                is AccessorExpression -> evaluateAccessorExpression(it, env)
                is LiteralExpression -> evaluateLiteralExpression(it)
            } 
        }
        
        // 确保所有参数都不为null
        if (args.any { it == null }) return null
        
        // 根据操作符计算结果
        return when (expression.operator) {
            "==" -> (args[0] == args[1])
            "!=" -> (args[0] != args[1])
            "<" -> compareValues(args[0], args[1])
            ">" -> compareValues(args[1], args[0])
            "<=" -> compareValues(args[0], args[1], true)
            ">=" -> compareValues(args[1], args[0], true)
            "&&", "and" -> (args.all { convertToBoolean(it) == true })
            "||", "or" -> (args.any { convertToBoolean(it) == true })
            else -> null // 不支持的操作符
        }
    }
    
    private fun evaluateBinaryExpressionWithCC(expression: BinaryExpression, env: Map<Variable, com.CC.Contexts.Context?>): Boolean? {
        val args = expression.args.map { 
            when (it) {
                is BinaryExpression -> evaluateBinaryExpressionWithCC(it, env)
                is AccessorExpression -> evaluateAccessorExpressionWithCC(it, env)
                is LiteralExpression -> evaluateLiteralExpression(it)
            } 
        }
        
        // 确保所有参数都不为null
        if (args.any { it == null }) return null
        
        // 根据操作符计算结果
        return when (expression.operator) {
            "==" -> (args[0] == args[1])
            "!=" -> (args[0] != args[1])
            "<" -> compareValues(args[0], args[1])
            ">" -> compareValues(args[1], args[0])
            "<=" -> compareValues(args[0], args[1], true)
            ">=" -> compareValues(args[1], args[0], true)
            "&&", "and" -> (args.all { convertToBoolean(it) == true })
            "||", "or" -> (args.any { convertToBoolean(it) == true })
            else -> null // 不支持的操作符
        }
    }
    
    private fun evaluateAccessorExpression(expression: AccessorExpression, env: Map<Variable, Context?>): Any? {
        val context = env[expression.context] ?: return null
        val attribute = expression.attribute
        val attributeValue = context.attributes[attribute]
        
        if (attributeValue == null || !attributeValue.second) {
            return null // 属性不存在或值无效
        }
        
        // 根据valueType转换值
        return when (expression.valueType) {
            "int" -> attributeValue.first.toIntOrNull()
            "bool" -> attributeValue.first.toBoolean()
            "string" -> attributeValue.first
            else -> attributeValue.first
        }
    }
    
    private fun evaluateAccessorExpressionWithCC(expression: AccessorExpression, env: Map<Variable, com.CC.Contexts.Context?>): Any? {
        val context = env[expression.context] ?: return null
        val attribute = expression.attribute
        val attributeValue = context.ctx_fields[attribute] ?: return null
        
        // 根据valueType转换值
        return when (expression.valueType) {
            "int" -> attributeValue.toIntOrNull()
            "bool" -> attributeValue.toBoolean()
            "string" -> attributeValue
            else -> attributeValue
        }
    }
    
    private fun evaluateLiteralExpression(expression: LiteralExpression): Any {
        return expression.value
    }
    
    private fun compareValues(a: Any?, b: Any?, includeEqual: Boolean = false): Boolean? {
        if (a !is Comparable<*> || b !is Comparable<*>) return null
        
        @Suppress("UNCHECKED_CAST")
        val result = try {
            (a as Comparable<Any>).compareTo(b)
        } catch (e: ClassCastException) {
            return null
        }
        
        return if (includeEqual) result <= 0 else result < 0
    }
    
    /**
     * 将任意值转换为Boolean
     */
    private fun convertToBoolean(value: Any?): Boolean? {
        return when (value) {
            is Boolean -> value
            is String -> value.toBoolean()
            is Number -> value != 0
            null -> null
            else -> null
        }
    }
    
    /**
     * 生成使表达式为真或为假的Z3条件字符串
     */
    override fun toZ3Condition(makeTrue: Boolean, contextMap: Map<Variable, Context?>): String {
        val exprStr = expressionToZ3(expression, contextMap)
        return if (makeTrue) exprStr else "Not($exprStr)"
    }
    
    /**
     * 将表达式转换为Z3表达式字符串
     */
    private fun expressionToZ3(expr: Expression, contextMap: Map<Variable, Context?> = mapOf()): String {
        return when (expr) {
            is BinaryExpression -> {
                val op = when (expr.operator) {
                    "+" -> "+"
                    "-" -> "-"
                    "*" -> "*"
                    "/" -> "/"
                    "<" -> "<"
                    "<=" -> "<="
                    ">" -> ">"
                    ">=" -> ">="
                    "==" -> "=="
                    "!=" -> "!="
                    "&&", "and" -> "And"
                    "||", "or" -> "Or"
                    else -> throw IllegalArgumentException("不支持的操作符: ${expr.operator}")
                }
                
                val args = expr.args.map { expressionToZ3(it, contextMap) }
                
                when (op) {
                    "+", "-", "*", "/", "<", "<=", ">", ">=", "==", "!=" -> {
                        // 二元运算符使用中缀表示
                        if (args.size == 2) {
                            "(${args[0]} $op ${args[1]})"
                        } else {
                            throw IllegalArgumentException("二元运算符需要两个参数")
                        }
                    }
                    "And", "Or" -> {
                        // 逻辑运算符使用函数表示
                        "$op(${args.joinToString(", ")})"
                    }
                    else -> throw IllegalArgumentException("不支持的操作符: ${expr.operator}")
                }
            }
            is AccessorExpression -> {
                // 访问器表达式，生成Z3变量引用
                val context = expr.context
                val contextObj = contextMap[context]
                contextObj ?: throw IllegalArgumentException("未找到上下文: $context")
                val valueType = expr.valueType.toValueType()
                val identifier = "${valueType.toZ3Type()}(\"ctx_${contextObj.id}_${expr.attribute}\")"
//                    when (expr.valueType) {
//                    "int" -> {
//                        val contextId = contextObj.id
//                        "Int(\"ctx_${contextId}_${expr.attribute}\")"
//                    }
//                    "bool" -> {
//                        val contextId = contextObj.id
//                        "Bool(\"ctx_${contextId}_${expr.attribute}\")"
//                    }
//                    "string" -> {
//                        val contextId = contextObj.id
//                        "String(\"ctx_${contextId}_${expr.attribute}\")"
//                    }
//                    else -> {
//                        // 使用context的hashCode作为唯一标识符
//                        val contextId = contextObj.id
//                        "ctx_${contextId}_${expr.attribute}"
//                    }
//                }
                identifier
            }
            is LiteralExpression -> {
                // 字面量表达式，生成Z3字面量值
                when (expr.valueType) {
                    "int" -> "${expr.value}"
                    "bool" -> if (expr.value as Boolean) "True" else "False"
                    "string" -> "\"${expr.value}\""
                    else -> "${expr.value}"
                }
            }
        }
    }

    private var accessors: Set<Pair<Pair<String, String>, ValueType>>? = null

    override fun collectAccessors(): Set<Pair<Pair<String, String>, ValueType>> {
        // 递归收集所有的context.attribute对
        if (accessors == null) {
            accessors = collectAccessors(expression)
        }
        logger.debug { "Accessors: ${accessors?.size}" }
        return accessors!!
    }

    private fun collectAccessors(expr: Expression): Set<Pair<Pair<String, String>, ValueType>> {
        return when (expr) {
            is AccessorExpression -> setOf((expr.context to expr.attribute) to expr.valueType.toValueType())
            is BinaryExpression -> expr.args.flatMap { collectAccessors(it) }.toSet()
            else -> emptySet()
        }
    }

    override fun generateRepairF2T(params: Map<String, Variable>, assignment: Assignment, weight: Double): RepairSuite {
        // 对于每个可能的context.attribute对，生成一个修复方案
        return RepairSuite(genAllRepairCases(params, assignment, weight).toSet())
    }

    override fun generateRepairT2F(params: Map<String, Variable>, assignment: Assignment, weight: Double): RepairSuite {
        // 对于每个可能的context.attribute对，生成一个修复方案
        return RepairSuite(genAllRepairCases(params, assignment, weight).toSet())
    }

    override fun generateRepairF2TSeq(params: Map<String, Variable>, assignment: Assignment, weight: Double): Sequence<RepairCase> {
        return genAllRepairCases(params, assignment, weight)
    }

    override fun generateRepairT2FSeq(params: Map<String, Variable>, assignment: Assignment, weight: Double): Sequence<RepairCase> {
        return genAllRepairCases(params, assignment, weight)
    }

    private fun genAllRepairCases(params: Map<String, Variable>, assignment: Assignment, weight: Double): Sequence<RepairCase> {
        return collectAccessors().asSequence().map { (pr, cls) ->
            val (context, attribute) = pr
            val variable = params[context] ?: return@map null
            val contextObj = assignment[variable] ?: return@map null
            RepairCase(BfuncRepairAction(contextObj, attribute, cls), weight)
        }.filterNotNull()
    }
} 