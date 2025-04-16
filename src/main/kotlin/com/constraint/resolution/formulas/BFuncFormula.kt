package com.constraint.resolution.formulas

import com.CC.Constraints.Formulas.FBfunc
import com.CC.Constraints.Runtime.RuntimeNode
import com.constraint.resolution.*
import com.constraint.resolution.bfunc.BFuncDefinition
import com.constraint.resolution.bfunc.BFuncExpression
import com.constraint.resolution.bfunc.BFuncRegistry

/**
 * 通用BFuncFormula，支持任意复杂的函数表达式
 */
data class BFuncFormula(
    val funcName: String,
    val parameters: Map<String, Variable>,
    val expression: BFuncExpression,
    val weight: Double = 1.0,
    val manager: ContextManager?,
    val disableConfigItems: List<RepairDisableConfigItem>? = null
) : IFormula {
    
    override fun evaluate(assignment: Assignment, patternMap: PatternMap): Boolean {
        // 创建变量环境，将Variable映射到Context
        val env = parameters.mapValues { (_, variable) -> 
            assignment[variable]
        }
        return expression.evaluate(env) == true
    }

    override fun repairF2T(assignment: Assignment, patternMap: PatternMap, lk: Boolean): RepairSuite {
        // 基于Z3求解生成修复方案
        val repairs = expression.generateRepairF2T(parameters, assignment, weight)
        return repairs.filterImmutable(disableConfigItems, manager)
    }

    override fun repairT2F(assignment: Assignment, patternMap: PatternMap, lk: Boolean): RepairSuite {
        // 基于Z3求解生成修复方案
        val repairs = expression.generateRepairT2F(parameters, assignment, weight)
        return repairs.filterImmutable(disableConfigItems, manager)
    }

    override fun createRCTNode(assignment: Assignment, patternMap: PatternMap, ccRtNode: RuntimeNode?): RCTNode {
        return RCTNode(this, assignment, patternMap, ccRtNode)
    }

    override fun createBranches(rctNode: RCTNode): List<RCTNode> {
        return listOf() // bfunc是叶节点，没有子节点
    }

    override fun evalRCTNode(rctNode: RCTNode): Boolean {
        val env = parameters.mapValues { (_, variable) -> 
            rctNode.assignment[variable]
        }
        return expression.evaluate(env) == true
    }

    override fun repairNodeF2T(rctNode: RCTNode, lk: Boolean): RepairSuite {
        val repairs = expression.generateRepairF2T(parameters, rctNode.assignment, weight)
        return repairs.filterImmutable(disableConfigItems, manager)
    }

    override fun repairNodeT2F(rctNode: RCTNode, lk: Boolean): RepairSuite {
        val repairs = expression.generateRepairT2F(parameters, rctNode.assignment, weight)
        return repairs.filterImmutable(disableConfigItems, manager)
    }

    override fun repairF2TSeq(assignment: Assignment, patternMap: PatternMap, lk: Boolean): Sequence<RepairCase> {
        val repairs = expression.generateRepairF2TSeq(parameters, assignment, weight)
        return filterImmutable(disableConfigItems, manager, repairs)
    }

    override fun repairT2FSeq(assignment: Assignment, patternMap: PatternMap, lk: Boolean): Sequence<RepairCase> {
        val repairs = expression.generateRepairT2FSeq(parameters, assignment, weight)
        return filterImmutable(disableConfigItems, manager, repairs)
    }

    override fun initVerifyNode(ccRtNode: RuntimeNode): VerifyNode {
        ccRtNode.verifyNode?.let { return it }
        val verifyNode = VerifyNode(this, ccRtNode)
        ccRtNode.verifyNode = verifyNode
        return verifyNode
    }

    override fun applyCaseToVerifyNode(verifyNode: VerifyNode, repairCase: RepairCase) {
        // 对于通用bfunc的验证节点应用，需要更新环境
        expression.applyRepairCase(verifyNode, repairCase, parameters)
    }

    override fun evalVerifyNode(verifyNode: VerifyNode): Boolean {
        verifyNode.unaffectedTruth()?.let { return it }
        
        // 从RuntimeNode中提取环境变量
        val varEnv = verifyNode.ccRtNode?.varEnv ?: verifyNode.varEnv
        if (varEnv == null) return false
        
        val contextEnv = parameters.mapValues { (_, variable) -> 
            varEnv[variable]
        }
        
        val result = expression.evaluateWithCCContext(contextEnv) == true
        verifyNode.truth = result
        verifyNode.affected = false
        return result
    }

    /**
     * 生成使BFunc公式为真的Z3条件
     */
    override fun Z3CondTrue(assignment: Assignment, patternMap: PatternMap): String {
        // 为变量创建上下文映射
        val contextMap = parameters.mapValues { (_, variable) -> 
            assignment[variable]
        }
        
        return """
        ${expression.toZ3Condition(true, contextMap)}
        """.trimIndent()
    }
    
    /**
     * 生成使BFunc公式为假的Z3条件
     */
    override fun Z3CondFalse(assignment: Assignment, patternMap: PatternMap): String {
        // 为变量创建上下文映射
        val contextMap = parameters.mapValues { (_, variable) -> 
            assignment[variable]
        }
        
        return """
        ${expression.toZ3Condition(false, contextMap)}
        """.trimIndent()
    }

    override fun getUsedAttributes(assignment: Assignment, patternMap: PatternMap): Set<Pair<Attribute, ValueType>> {
        return expression.collectAccessorValues(parameters, assignment)
    }
}

/**
 * 从FBfunc和bfunc定义创建BFuncFormula
 */
fun createBFuncFormula(fml: FBfunc, bfuncDef: BFuncDefinition, manager: ContextManager?): BFuncFormula {
    return BFuncFormula(
        funcName = fml.func,
        parameters = fml.params,
        expression = bfuncDef.parseExpression(),
        weight = 1.0,
        manager = manager,
        disableConfigItems = fml.disableConfigItems
    )
}

/**
 * 更新后的fromCCFormulaBfunc函数，支持通用bfunc
 */
fun fromCCFormulaBfuncGeneral(fml: FBfunc, manager: ContextManager?, registry: BFuncRegistry): IFormula {
    // 首先尝试使用原始解析方式（向后兼容）
    if (fml.func.startsWith("equal")) {
        val names = fml.func.split('_')
        if (names.first() == "equal") {
            return if (names.size > 1 && names[1] == "const") {
                val var1 = fml.params.getValue("var1")
                val attr1 = names[2]
                val value = names[3]
                val weight = 1.0
                EqualConstFormula(var1, attr1, value, weight, manager, fml.disableConfigItems)
            } else {
                val var1 = fml.params.getValue("var1")
                val var2 = fml.params.getValue("var2")
                val attr1 = names[1]
                val attr2 = names[2]
                val weight = 1.0
                EqualFormula(var1, attr1, var2, attr2, weight, manager, fml.disableConfigItems)
            }
        }
    }
    
    // 如果不是简单的equal函数，则使用通用bfunc解析
    val bfuncDef = registry.getBFuncDefinition(fml.func)
        ?: throw IllegalArgumentException("Unknown bfunc: ${fml.func}")
    
    return createBFuncFormula(fml, bfuncDef, manager)
} 