package com.constraint.resolution.formulas

import com.CC.Constraints.Formulas.FExists
import com.constraint.resolution.*

data class ExistsFormula(val variable: Variable, val subFormula: IFormula, val pattern: String, val weight: Double) :
    IFormula {
    override fun evaluate(assignment: Assignment, patternMap: PatternMap) =
        patternMap.getValue(pattern).any { subFormula.evaluate(bind(assignment, variable, it), patternMap) }

    override fun repairF2T(assignment: Assignment, patternMap: PatternMap, lk: Boolean): RepairSuite {
        val newContext = makeContext(mapOf())
        return patternMap.getValue(pattern).map {
            subFormula.repairF2T(bind(assignment, variable, it), patternMap, lk)
        }.reduce { acc, suite -> acc or suite } or
                when (subFormula.evaluate(bind(assignment, variable, newContext), patternMap)) {
                    true -> RepairSuite(AdditionRepairAction(newContext, pattern), weight)
                    false -> RepairSuite(AdditionRepairAction(newContext, pattern), weight) and
                            subFormula.repairF2T(bind(assignment, variable, newContext), patternMap, lk)
                }
    }

    override fun repairT2F(assignment: Assignment, patternMap: PatternMap, lk: Boolean) =
        patternMap.getValue(pattern).filter { subFormula.evaluate(bind(assignment, variable, it), patternMap) }
            .map {
                RepairSuite(RemovalRepairAction(it, pattern), weight) or
                        subFormula.repairT2F(bind(assignment, variable, it), patternMap, lk)
            }.reduce { acc, suite -> acc and suite }
}

fun fromCCFormulaExists(fml: FExists) = ExistsFormula(
    fml.`var`,
    fromCCFormula(fml.subformula),
    fml.pattern_id,
    1.0
)
