package com.constraint.resolution.formulas

import com.CC.Constraints.Formulas.FImplies
import com.constraint.resolution.*

data class ImpliesFormula(val left: IFormula, val right: IFormula) : IFormula {
    override fun evaluate(assignment: Assignment, patternMap: PatternMap) =
        !left.evaluate(assignment, patternMap) or right.evaluate(assignment, patternMap)

    override fun repairF2T(assignment: Assignment, patternMap: PatternMap, lk: Boolean) =
        left.repairT2F(assignment, patternMap, lk) or right.repairF2T(assignment, patternMap, lk)

    override fun repairT2F(assignment: Assignment, patternMap: PatternMap, lk: Boolean): RepairSuite {
        if (!lk) {
            val leftTruth = left.evaluate(assignment, patternMap)
            val rightTruth = right.evaluate(assignment, patternMap)
            return when (leftTruth to rightTruth) {
                true to true -> right.repairT2F(assignment, patternMap, lk)
                false to true -> left.repairF2T(assignment, patternMap, lk) and right.repairT2F(assignment, patternMap, lk)
                false to false -> left.repairF2T(assignment, patternMap, lk)
                else -> RepairSuite(setOf())
            }
        }
        TODO("Locked repairs not implemented for ImpliesFormula")
    }
}

fun fromCCFormulaImplies(fml: FImplies) =
    ImpliesFormula(fromCCFormula(fml.subformulas.first()), fromCCFormula(fml.subformulas.last()))
