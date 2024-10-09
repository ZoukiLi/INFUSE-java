package com.constraint.resolution.formulas

import com.CC.Constraints.Formulas.FExists
import com.constraint.resolution.*

data class ExistsFormula(
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
        return filteredMap.getValue(pattern).any { subFormula.evaluate(bind(assignment, variable, it), filteredMap) }
    }

    override fun repairF2T(assignment: Assignment, patternMap: PatternMap, lk: Boolean): RepairSuite {
        val filteredMap =
            patternMap + (pattern to patternMap[pattern]!!.filterBy(filter, filterDep?.let { assignment[it] }))
        val newContext = makeContext(mapOf())
        return filteredMap.getValue(pattern).map {
            subFormula.repairF2T(bind(assignment, variable, it), filteredMap, lk)
        }.fold(RepairSuite()) { acc, suite -> acc or suite } or
                when (subFormula.evaluate(bind(assignment, variable, newContext), filteredMap)) {
                    true -> RepairSuite(AdditionRepairAction(newContext, pattern), weight)
                    false -> RepairSuite(AdditionRepairAction(newContext, pattern), weight) and
                            subFormula.repairF2T(bind(assignment, variable, newContext), filteredMap, lk)
                }
    }

    override fun repairT2F(assignment: Assignment, patternMap: PatternMap, lk: Boolean): RepairSuite {
        val filteredMap =
            patternMap + (pattern to patternMap[pattern]!!.filterBy(filter, filterDep?.let { assignment[it] }))
        return filteredMap.getValue(pattern).filter { subFormula.evaluate(bind(assignment, variable, it), filteredMap) }
            .map {
                RepairSuite(RemovalRepairAction(it, pattern), weight) or
                        subFormula.repairT2F(bind(assignment, variable, it), filteredMap, lk)
            }.fold(RepairSuite()) { acc, suite -> acc and suite }
    }
}

fun fromCCFormulaExists(fml: FExists) = ExistsFormula(
    fml.`var`,
    fromCCFormula(fml.subformula),
    fml.pattern_id,
    1.0,
    fml.filter,
    fml.filterDep
)
