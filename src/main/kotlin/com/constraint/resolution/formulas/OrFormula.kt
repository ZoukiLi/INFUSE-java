package com.constraint.resolution.formulas

import com.CC.Constraints.Formulas.FOr
import com.constraint.resolution.*

data class OrFormula(val left: IFormula, val right: IFormula) : IFormula {
    override fun evaluate(assignment: Assignment, patternMap: PatternMap) =
        left.evaluate(assignment, patternMap) or right.evaluate(assignment, patternMap)

    override fun repairF2T(assignment: Assignment, patternMap: PatternMap, lk: Boolean) =
        left.repairF2T(assignment, patternMap, lk) or right.repairF2T(assignment, patternMap, lk)

    override fun repairT2F(assignment: Assignment, patternMap: PatternMap, lk: Boolean): RepairSuite {
        if (!lk) {
            val leftTruth = left.evaluate(assignment, patternMap)
            val rightTruth = right.evaluate(assignment, patternMap)
            return when (leftTruth to rightTruth) {
                true to true -> left.repairT2F(assignment, patternMap, lk) and right.repairT2F(assignment, patternMap, lk)
                false to true -> right.repairT2F(assignment, patternMap, lk)
                true to false -> left.repairT2F(assignment, patternMap, lk)
                else -> RepairSuite(setOf())
            }
        }
        TODO("Locked repair not implemented for OrFormula")
    }
}

fun fromCCFormulaOr(fml: FOr) =
    OrFormula(fromCCFormula(fml.subformulas.first()), fromCCFormula(fml.subformulas.last()))
