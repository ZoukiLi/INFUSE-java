package com.constraint.resolution.formulas

import com.CC.Constraints.Formulas.FBfunc
import com.CC.Constraints.Runtime.RuntimeNode
import com.constraint.resolution.*

data class EqualFormula(
    val var1: Variable, val attr1: String, val var2: Variable, val attr2: String, val weight: Double = 1.0,
    val manager: ContextManager?, val disableConfigItems: List<RepairDisableConfigItem>? = null
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

    ).filterImmutable(disableConfigItems, manager)

    override fun repairT2F(assignment: Assignment, patternMap: PatternMap, lk: Boolean) = RepairSuite(
        // One repair case: differentiate the values of the two attributes
        DifferentiationRepairAction(
            assignment.getValue(var1), attr1,
            assignment.getValue(var2), attr2,
        ), weight
    ).filterImmutable(disableConfigItems, manager)

    override fun createRCTNode(assignment: Assignment, patternMap: PatternMap, ccRtNode: RuntimeNode?) =
        RCTNode(this, assignment, patternMap, ccRtNode)

    override fun createBranches(rctNode: RCTNode) = listOf<RCTNode>()
    override fun evalRCTNode(rctNode: RCTNode): Boolean {
        val value1 = rctNode.assignment[var1]?.attributes?.get(attr1) ?: return false
        val value2 = rctNode.assignment[var2]?.attributes?.get(attr2) ?: return false
        if (!value1.second || !value2.second) {
            return false
        }
        return value1.first == value2.first
    }

    override fun repairNodeF2T(rctNode: RCTNode, lk: Boolean) = RepairSuite(
        // One repair case: equalize the values of the two attributes
        EqualizationRepairAction(
            rctNode.assignment.getValue(var1), attr1,
            rctNode.assignment.getValue(var2), attr2,
        ), weight
    ).filterImmutable(disableConfigItems, manager)

    override fun repairNodeT2F(rctNode: RCTNode, lk: Boolean) = RepairSuite(
        // One repair case: differentiate the values of the two attributes
        DifferentiationRepairAction(
            rctNode.assignment.getValue(var1), attr1,
            rctNode.assignment.getValue(var2), attr2,
        ), weight
    ).filterImmutable(disableConfigItems, manager)
}

data class EqualConstFormula(val var1: Variable, val attr1: String, val value: String, val weight: Double = 1.0,
    val manager: ContextManager?, val disableConfigItems: List<RepairDisableConfigItem>? = null
) : IFormula {
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
    ).filterImmutable(disableConfigItems, manager)

    override fun repairT2F(assignment: Assignment, patternMap: PatternMap, lk: Boolean) = RepairSuite(
        // One repair case: differentiate the constant value with the value of the attribute
        DifferentiationConstRepairAction(
            assignment.getValue(var1), attr1,
            value,
        ), weight
    ).filterImmutable(disableConfigItems, manager)

    override fun createRCTNode(assignment: Assignment, patternMap: PatternMap, ccRtNode: RuntimeNode?) =
        RCTNode(this, assignment, patternMap, ccRtNode)

    override fun createBranches(rctNode: RCTNode) = listOf<RCTNode>()
    override fun evalRCTNode(rctNode: RCTNode): Boolean {
        val value1 = rctNode.assignment[var1]?.attributes?.get(attr1) ?: return false
        if (!value1.second) {
            return false
        }
        return value1.first == value
    }

    override fun repairNodeF2T(rctNode: RCTNode, lk: Boolean) = RepairSuite(
        // One repair case: equalize the constant value with the value of the attribute
        EqualizationConstRepairAction(
            rctNode.assignment.getValue(var1), attr1,
            value,
        ), weight
    ).filterImmutable(disableConfigItems, manager)

    override fun repairNodeT2F(rctNode: RCTNode, lk: Boolean) = RepairSuite(
        // One repair case: differentiate the constant value with the value of the attribute
        DifferentiationConstRepairAction(
            rctNode.assignment.getValue(var1), attr1,
            value,
        ), weight
    ).filterImmutable(disableConfigItems, manager)
}

fun fromCCFormulaBfunc(fml: FBfunc, manager: ContextManager?): IFormula {
    val names = fml.func.split('_')
    assert(names.first() == "equal")
    return if (names[1] == "const") {
        val var1 = fml.params.getValue("var1")
        val attr1 = names[2]
        val value = names[3]
        val weight = 1.0
        EqualConstFormula(var1, attr1, value, weight, manager, fml.disableConfigItems)
    }
    else {
        val var1 = fml.params.getValue("var1")
        val var2 = fml.params.getValue("var2")
        val attr1 = names[1]
        val attr2 = names[2]
        val weight = 1.0
        EqualFormula(var1, attr1, var2, attr2, weight, manager, fml.disableConfigItems)
    }
}
