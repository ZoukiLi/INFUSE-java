package com.constraint.resolution

import com.CC.Constraints.Formulas.*
import com.CC.Constraints.Runtime.RuntimeNode
import com.constraint.resolution.formulas.*

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
}

fun fromCCFormula(fml: Formula, manager: ContextManager?) : IFormula = when(fml) {
        is FAnd -> fromCCFormulaAnd(fml, manager)
        is FBfunc -> fromCCFormulaBfunc(fml, manager)
        is FExists -> fromCCFormulaExists(fml, manager)
        is FForall -> fromCCFormulaForall(fml, manager)
        is FImplies -> fromCCFormulaImplies(fml, manager)
        is FNot -> fromCCFormulaNot(fml, manager)
        is FOr -> fromCCFormulaOr(fml, manager)
        else -> TODO("Unsupported formula type: ${fml.formula_type}")
    }

