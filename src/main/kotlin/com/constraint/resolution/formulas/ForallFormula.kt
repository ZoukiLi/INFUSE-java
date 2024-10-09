package com.constraint.resolution.formulas

import com.CC.Constraints.Formulas.FForall
import com.constraint.resolution.*

data class ForallFormula(
    val variable: Variable,
    val subFormula: IFormula,
    val pattern: String,
    val weight: Double,
    val filter: String?,
    val filterDep: String?
) :
    IFormula {
    override fun evaluate(assignment: Assignment, patternMap: PatternMap): Boolean {
        val filteredMap =
            patternMap + (pattern to patternMap[pattern]!!.filterBy(filter, filterDep?.let { assignment[it] }))
        return filteredMap.getValue(pattern)
            .all { subFormula.evaluate(bind(assignment, variable, it), filteredMap) }
    }

    override fun repairF2T(assignment: Assignment, patternMap: PatternMap, lk: Boolean): RepairSuite {
        val filteredMap =
            patternMap + (pattern to patternMap[pattern]!!.filterBy(filter, filterDep?.let { assignment[it] }))
        return filteredMap.getValue(pattern).filter { !subFormula.evaluate(bind(assignment, variable, it), filteredMap) }
            // For each context in the pattern that does not satisfy the sub formula,
            .map {
                // Remove this context from the pattern
                RepairSuite(RemovalRepairAction(it, pattern), weight) or
                        // Or repair the sub formula for that context
                        subFormula.repairF2T(bind(assignment, variable, it), filteredMap, lk)
            }
            // Combine all the repair suites for each context that does not satisfy
            .fold(RepairSuite()) { acc, suite -> acc and suite }
    }

    override fun repairT2F(assignment: Assignment, patternMap: PatternMap, lk: Boolean): RepairSuite {
        val filteredMap =
            patternMap + (pattern to patternMap[pattern]!!.filterBy(filter, filterDep?.let { assignment[it] }))
        // Construct a default context from the pattern
        val newContext = makeContext(mapOf())
        return filteredMap.getValue(pattern).map {
            subFormula.repairT2F(bind(assignment, variable, it), filteredMap, lk)
        }.fold(RepairSuite()) { acc, suite -> acc or suite } or
                // Add the default context to the pattern
                when (subFormula.evaluate(bind(assignment, variable, newContext), filteredMap)) {
                    true -> RepairSuite(AdditionRepairAction(newContext, pattern), weight) and
                            subFormula.repairT2F(bind(assignment, variable, newContext), filteredMap, lk)

                    false -> RepairSuite(AdditionRepairAction(newContext, pattern), weight)
                }
    }
}

fun fromCCFormulaForall(fml: FForall) = ForallFormula(
    fml.`var`,
    fromCCFormula(fml.subformula),
    fml.pattern_id,
    1.0,
    fml.filter,
    fml.filterDep
)
