package com.constraint.resolution.formulas

import com.CC.Constraints.Formulas.FAnd
import com.constraint.resolution.*

data class AndFormula(val left: IFormula, val right: IFormula) : IFormula {
    override fun evaluate(assignment: Assignment, patternMap: PatternMap) =
        left.evaluate(assignment, patternMap) and right.evaluate(assignment, patternMap)

    override fun repairF2T(assignment: Assignment, patternMap: PatternMap, lk: Boolean): RepairSuite {
        if (!lk) {
            val leftTruth = left.evaluate(assignment, patternMap)
            val rightTruth = right.evaluate(assignment, patternMap)
            when (Pair(leftTruth, rightTruth)) {
                // Both true, nothing to do
                Pair(true, true) -> return RepairSuite(setOf())
                // Repair the branched formula that is false
                Pair(false, true) -> return left.repairF2T(assignment, patternMap, lk)
                Pair(true, false) -> return right.repairF2T(assignment, patternMap, lk)
                // Repair both branched formulas when both are false
                Pair(false, false) ->
                    return left.repairF2T(assignment, patternMap, lk) and right.repairF2T(assignment, patternMap, lk)
            }
        }
        TODO("Locked repair not implemented for AndFormula")
    }

    override fun repairT2F(assignment: Assignment, patternMap: PatternMap, lk: Boolean) =
        // Repair both branched formulas when both are true
        left.repairT2F(assignment, patternMap, lk) and right.repairT2F(assignment, patternMap, lk)
}

fun fromCCFormulaAnd(fml: FAnd) =
    AndFormula(fromCCFormula(fml.subformulas.first()), fromCCFormula(fml.subformulas.last()))
