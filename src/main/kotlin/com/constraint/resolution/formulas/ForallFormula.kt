package com.constraint.resolution.formulas

import com.CC.Constraints.Formulas.FForall
import com.constraint.resolution.*

data class ForallFormula(val variable: Variable, val subFormula: IFormula, val pattern: String, val weight: Double) :
    IFormula {
    override fun evaluate(assignment: Assignment, patternMap: PatternMap) =
        patternMap.getValue(pattern).all { subFormula.evaluate(bind(assignment, variable, it), patternMap) }

    override fun repairF2T(assignment: Assignment, patternMap: PatternMap, lk: Boolean) =
        patternMap.getValue(pattern).filter { !subFormula.evaluate(bind(assignment, variable, it), patternMap) }
            // For each context in the pattern that does not satisfy the sub formula,
            .map {
                // Remove this context from the pattern
                RepairSuite(RemovalRepairAction(it, pattern), weight) or
                        // Or repair the sub formula for that context
                        subFormula.repairF2T(bind(assignment, variable, it), patternMap, lk)
            }
            // Combine all the repair suites for each context that does not satisfy
            .reduce { acc, suite -> acc and suite }

    override fun repairT2F(assignment: Assignment, patternMap: PatternMap, lk: Boolean): RepairSuite {
        // Construct a default context from the pattern
        val newContext = makeContext(mapOf())
        return patternMap.getValue(pattern).map {
            subFormula.repairT2F(bind(assignment, variable, it), patternMap, lk)
        }.reduce { acc, suite -> acc or suite } or
                // Add the default context to the pattern
                when (subFormula.evaluate(bind(assignment, variable, newContext), patternMap)) {
                    true -> RepairSuite(AdditionRepairAction(newContext, pattern), weight) and
                            subFormula.repairT2F(bind(assignment, variable, newContext), patternMap, lk)
                    false -> RepairSuite(AdditionRepairAction(newContext, pattern), weight)
                }
    }
}

fun fromCCFormulaForall(fml: FForall) = ForallFormula(
    fml.`var`,
    fromCCFormula(fml.subformula),
    fml.pattern_id,
    1.0
)
