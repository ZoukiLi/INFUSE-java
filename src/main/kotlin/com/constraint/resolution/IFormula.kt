package com.constraint.resolution

import com.CC.Constraints.Formulas.*
import com.constraint.resolution.formulas.fromCCFormulaAnd
import com.constraint.resolution.formulas.fromCCFormulaBfunc
import com.constraint.resolution.formulas.fromCCFormulaForall
import com.constraint.resolution.formulas.fromCCFormulaNot

interface IFormula {

    fun evaluate(assignment: Assignment, patternMap: PatternMap): Boolean
    fun repairF2T(assignment: Assignment, patternMap: PatternMap, lk: Boolean = false): RepairSuite
    fun repairT2F(assignment: Assignment, patternMap: PatternMap, lk: Boolean = false): RepairSuite
}

fun fromCCFormula(fml: Formula) : IFormula = when(fml) {
        is FAnd -> fromCCFormulaAnd(fml)
        is FBfunc -> fromCCFormulaBfunc(fml)
        is FExists -> TODO()
        is FForall -> fromCCFormulaForall(fml)
        is FImplies -> TODO()
        is FNot -> fromCCFormulaNot(fml)
        is FOr -> TODO()
        else -> TODO("Unsupported formula type: ${fml.formula_type}")
    }

