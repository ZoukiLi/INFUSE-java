package com.constraint.resolution

import com.CC.Constraints.Formulas.*
import com.constraint.resolution.formulas.fromCCFormulaNot

interface IFormula {
    fun evaluate(assignment: Assignment, patternMap: PatternMap): Boolean
    fun repairF2T(assignment: Assignment, patternMap: PatternMap, lk: Boolean = false): RepairSuite
    fun repairT2F(assignment: Assignment, patternMap: PatternMap, lk: Boolean = false): RepairSuite
}

fun fromCCFormula(fml: Formula) : IFormula = when(fml) {
        is FAnd -> TODO()
        is FBfunc -> TODO()
        is FExists -> TODO()
        is FForall -> TODO()
        is FImplies -> TODO()
        is FNot -> fromCCFormulaNot(fml)
        else -> assert(false) { "Unsupported formula type: $fml" }
    } as IFormula

