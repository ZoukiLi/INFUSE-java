package com.constraint.resolution.formulas

import com.CC.Constraints.Formulas.FNot
import com.constraint.resolution.Assignment
import com.constraint.resolution.IFormula
import com.constraint.resolution.PatternMap
import com.constraint.resolution.fromCCFormula

data class NotFormula(val node: IFormula) : IFormula {
    override fun evaluate(assignment: Assignment, patternMap: PatternMap) = !node.evaluate(assignment, patternMap)

    override fun repairF2T(
        assignment: Assignment,
        patternMap: PatternMap,
        lk: Boolean
    ) = node.repairT2F(assignment, patternMap, lk)

    override fun repairT2F(
        assignment: Assignment,
        patternMap: PatternMap,
        lk: Boolean
    ) = node.repairF2T(assignment, patternMap, lk)
}

fun fromCCFormulaNot(fml: FNot) = NotFormula(fromCCFormula(fml.subformula))
