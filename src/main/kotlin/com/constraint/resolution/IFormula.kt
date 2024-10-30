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
}

fun fromCCFormula(fml: Formula) : IFormula = when(fml) {
        is FAnd -> fromCCFormulaAnd(fml)
        is FBfunc -> fromCCFormulaBfunc(fml)
        is FExists -> fromCCFormulaExists(fml)
        is FForall -> fromCCFormulaForall(fml)
        is FImplies -> fromCCFormulaImplies(fml)
        is FNot -> fromCCFormulaNot(fml)
        is FOr -> fromCCFormulaOr(fml)
        else -> TODO("Unsupported formula type: ${fml.formula_type}")
    }

