package com.constraint.resolution.formulas

import com.CC.Constraints.Formulas.FBfunc
import com.constraint.resolution.*

data class EqualFormula(
    val var1: Variable, val attr1: String, val var2: Variable, val attr2: String, val weight: Double = 1.0
) : IFormula {
    override fun evaluate(assignment: Assignment, patternMap: PatternMap): Boolean {
        val value1 = assignment[var1]?.attributes?.get(attr1) ?: return false
        val value2 = assignment[var2]?.attributes?.get(attr2) ?: return false
        if (!value1.second || !value2.second) {
            return false
        }
        return value1.first == value2.first
    }

    override fun repairF2T(assignment: Assignment, patternMap: PatternMap, lk: Boolean) = RepairSuite(
        // One repair case: equalize the values of the two attributes
        EqualizationRepairAction(
            assignment.getValue(var1), attr1,
            assignment.getValue(var2), attr2,
        ), weight

    )

    override fun repairT2F(assignment: Assignment, patternMap: PatternMap, lk: Boolean) = RepairSuite(
        // One repair case: differentiate the values of the two attributes
        DifferentiationRepairAction(
            assignment.getValue(var1), attr1,
            assignment.getValue(var2), attr2,
        ), weight
    )
}

data class EqualConstFormula(val var1: Variable, val attr1: String, val value: String, val weight: Double = 1.0) : IFormula {
    override fun evaluate(assignment: Assignment, patternMap: PatternMap): Boolean {
        val value1 = assignment[var1]?.attributes?.get(attr1) ?: return false
        if (!value1.second) {
            return false
        }
        return value1.first == value
    }
    override fun repairF2T(assignment: Assignment, patternMap: PatternMap, lk: Boolean) = RepairSuite(
        // One repair case: equalize the constant value with the value of the attribute
        EqualizationConstRepairAction(
            assignment.getValue(var1), attr1,
            value,
        ), weight
    )

    override fun repairT2F(assignment: Assignment, patternMap: PatternMap, lk: Boolean) = RepairSuite(
        // One repair case: differentiate the constant value with the value of the attribute
        DifferentiationConstRepairAction(
            assignment.getValue(var1), attr1,
            value,
        ), weight
    )
}

fun fromCCFormulaBfunc(fml: FBfunc): IFormula {
    val names = fml.func.split('_')
    assert(names.first() == "equal")
    return if (names[1] == "const") {
        val var1 = fml.params.getValue("var1")
        val attr1 = names[2]
        val value = names[3]
        val weight = 1.0
        EqualConstFormula(var1, attr1, value, weight)
    }
    else {
        val var1 = fml.params.getValue("var1")
        val var2 = fml.params.getValue("var2")
        val attr1 = names[1]
        val attr2 = names[2]
        val weight = 1.0
        EqualFormula(var1, attr1, var2, attr2, weight)
    }
}
