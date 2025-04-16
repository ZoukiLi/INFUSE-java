package com.constraint.resolution

import com.CC.Constraints.Formulas.*
import com.CC.Constraints.Runtime.RuntimeNode
import com.constraint.resolution.formulas.*
import com.constraint.resolution.bfunc.BFuncRegistry

interface IFormula {
    fun evaluate(assignment: Assignment, patternMap: PatternMap): Boolean
    fun repairF2T(assignment: Assignment, patternMap: PatternMap, lk: Boolean = false): RepairSuite
    fun repairT2F(assignment: Assignment, patternMap: PatternMap, lk: Boolean = false): RepairSuite

    fun createRCTNode(assignment: Assignment, patternMap: PatternMap, ccRtNode: RuntimeNode? = null): RCTNode
    fun createBranches(rctNode: RCTNode): List<RCTNode>
    fun evalRCTNode(rctNode: RCTNode): Boolean
    fun repairNodeF2T(rctNode: RCTNode, lk: Boolean = false): RepairSuite
    fun repairNodeT2F(rctNode: RCTNode, lk: Boolean = false): RepairSuite

    fun repairF2TSeq(assignment: Assignment, patternMap: PatternMap, lk: Boolean = false): Sequence<RepairCase>
    fun repairT2FSeq(assignment: Assignment, patternMap: PatternMap, lk: Boolean = false): Sequence<RepairCase>

    fun initVerifyNode(ccRtNode: RuntimeNode): VerifyNode
    fun applyCaseToVerifyNode(verifyNode: VerifyNode, repairCase: RepairCase)
    fun evalVerifyNode(verifyNode: VerifyNode): Boolean
    
    /**
     * 生成使公式为真的Z3条件字符串
     * @param assignment 变量赋值
     * @param patternMap 模式映射
     * @return Z3 Python条件表达式字符串
     */
    fun Z3CondTrue(assignment: Assignment, patternMap: PatternMap): String
    
    /**
     * 生成使公式为假的Z3条件字符串
     * @param assignment 变量赋值
     * @param patternMap 模式映射
     * @return Z3 Python条件表达式字符串
     */
    fun Z3CondFalse(assignment: Assignment, patternMap: PatternMap): String

    /**
     * 获取所有使用到的变量属性
     */
    fun getUsedAttributes(assignment: Assignment, patternMap: PatternMap): Set<Pair<Attribute, ValueType>>
}

/**
 * 解析CC Formula为Kotlin Formula
 * @param fml CC的Formula对象
 * @param manager 上下文管理器
 * @param registry BFunc注册表，用于解析通用bfunc
 */
fun fromCCFormula(fml: Formula, manager: ContextManager?, registry: BFuncRegistry? = null) : IFormula = when(fml) {
    is FAnd -> fromCCFormulaAnd(fml, manager, registry)
    is FBfunc -> {
        // 如果提供了registry，尝试使用通用bfunc解析
        if (registry != null) {
            fromCCFormulaBfuncGeneral(fml, manager, registry)
        } else {
            // 否则使用原有方式解析
            fromCCFormulaBfunc(fml, manager, registry)
        }
    }
    is FExists -> fromCCFormulaExists(fml, manager, registry)
    is FForall -> fromCCFormulaForall(fml, manager, registry)
    is FImplies -> fromCCFormulaImplies(fml, manager, registry)
    is FNot -> fromCCFormulaNot(fml, manager, registry)
    is FOr -> fromCCFormulaOr(fml, manager, registry)
    else -> throw IllegalArgumentException("Unsupported formula type: ${fml.formula_type}")
}

