package com.constraint.resolution.formulas

import com.CC.Constraints.Formulas.FBfunc
import com.CC.Constraints.Runtime.RuntimeNode
import com.CC.Contexts.Context
import com.constraint.resolution.*
import com.constraint.resolution.bfunc.BFuncRegistry
import kotlin.math.absoluteValue

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

    override fun repairF2TSeq(assignment: Assignment, patternMap: PatternMap, lk: Boolean): Sequence<RepairCase> {
        val action = EqualizationRepairAction(assignment.getValue(var1), attr1, assignment.getValue(var2), attr2)
        val action2 = EqualizationRepairAction(assignment.getValue(var2), attr2, assignment.getValue(var1), attr1)
        val rst = sequenceOf(RepairCase(action, weight), RepairCase(action2, weight))
        return filterImmutable(disableConfigItems, manager, rst)
    }

    override fun repairT2FSeq(assignment: Assignment, patternMap: PatternMap, lk: Boolean): Sequence<RepairCase> {
        val action = DifferentiationRepairAction(assignment.getValue(var1), attr1, assignment.getValue(var2), attr2)
        val action2 = DifferentiationRepairAction(assignment.getValue(var2), attr2, assignment.getValue(var1), attr1)
        val rst = sequenceOf(RepairCase(action, weight), RepairCase(action2, weight))
        return filterImmutable(disableConfigItems, manager, rst)
    }

    override fun initVerifyNode(ccRtNode: RuntimeNode): VerifyNode {
        ccRtNode.verifyNode?.let { return it }
        val verifyNode = VerifyNode(this, ccRtNode)
        ccRtNode.verifyNode = verifyNode
        return verifyNode
    }

    override fun applyCaseToVerifyNode(
        verifyNode: VerifyNode,
        repairCase: RepairCase
    ) {
        val curVarEnv = verifyNode.ccRtNode?.varEnv ?: verifyNode.varEnv ?: return
        repairCase.actions.forEach { action ->
            when (action) {
                is EqualizationRepairAction -> {
                    // Update the variable environment with the repair action
                    if (curVarEnv[var1] == action.context1 && attr1 == action.attributeName1) {
                        // todo
                    }
                    val newVarEnv = mutableMapOf<Variable, Context>()
                    val newCtx1 = Context()
                    val ctx1 = action.context1.ccContext ?: return
                    val ctx2 = action.context2.ccContext ?: return
                    val fields = ctx1.ctx_fields + (attr1 to ctx2.ctx_fields?.get(attr2))
                    newCtx1.ctx_fields.putAll(fields)
                    newVarEnv[var1] = newCtx1
                    newVarEnv[var2] = ctx2
                    verifyNode.varEnv = action.varEnv()
                    verifyNode.affected = true
                }
            }
        }
    }

    override fun evalVerifyNode(verifyNode: VerifyNode): Boolean {
        verifyNode.unaffectedTruth()?.let { return it }
        val varEnv = verifyNode.ccRtNode?.varEnv ?: verifyNode.varEnv
        val value1 = varEnv?.get(var1)?.ctx_fields?.get(attr1) ?: return false
        val value2 = varEnv?.get(var2)?.ctx_fields?.get(attr2) ?: return false
        val result = value1 == value2
        return result
    }

    /**
     * 生成使EQUAL公式为真的Z3条件
     */
    override fun Z3CondTrue(assignment: Assignment, patternMap: PatternMap): String {
        val ctx1 = assignment[var1]
        val ctx2 = assignment[var2]
        
        val ctx1Id = ctx1?.hashCode()?.absoluteValue ?: 0
        val ctx2Id = ctx2?.hashCode()?.absoluteValue ?: 0
        
        val var1Name = "${var1}_${ctx1Id}_${attr1}"
        val var2Name = "${var2}_${ctx2Id}_${attr2}"
        
        return "($var1Name == $var2Name)"
    }
    
    /**
     * 生成使EQUAL公式为假的Z3条件
     */
    override fun Z3CondFalse(assignment: Assignment, patternMap: PatternMap): String {
        val ctx1 = assignment[var1]
        val ctx2 = assignment[var2]
        
        val ctx1Id = ctx1?.hashCode()?.absoluteValue ?: 0
        val ctx2Id = ctx2?.hashCode()?.absoluteValue ?: 0
        
        val var1Name = "${var1}_${ctx1Id}_${attr1}"
        val var2Name = "${var2}_${ctx2Id}_${attr2}"
        
        return "($var1Name != $var2Name)"
    }

    override fun getUsedAttributes(assignment: Assignment, patternMap: PatternMap): Set<Pair<Attribute, ValueType>> {
        return setOf()
    }
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

    override fun repairF2TSeq(assignment: Assignment, patternMap: PatternMap, lk: Boolean): Sequence<RepairCase> {
        val action = EqualizationConstRepairAction(assignment.getValue(var1), attr1, value)
        val rst = sequenceOf(RepairCase(action, weight))
        return filterImmutable(disableConfigItems, manager, rst)
    }

    override fun repairT2FSeq(assignment: Assignment, patternMap: PatternMap, lk: Boolean): Sequence<RepairCase> {
        val action = DifferentiationConstRepairAction(assignment.getValue(var1), attr1, value)
        val rst = sequenceOf(RepairCase(action, weight))
        return filterImmutable(disableConfigItems, manager, rst)
    }

    override fun initVerifyNode(ccRtNode: RuntimeNode): VerifyNode {
        ccRtNode.verifyNode?.let { return it }
        val verifyNode = VerifyNode(this, ccRtNode)
        ccRtNode.verifyNode = verifyNode
        return verifyNode
    }

    override fun applyCaseToVerifyNode(
        verifyNode: VerifyNode,
        repairCase: RepairCase
    ) {
        // just do nothing
    }

    override fun evalVerifyNode(verifyNode: VerifyNode): Boolean {
        verifyNode.unaffectedTruth()?.let { return it }
        val varEnv = verifyNode.ccRtNode?.varEnv ?: verifyNode.varEnv
        val value1 = varEnv?.get(var1)?.ctx_fields?.get(attr1) ?: return false
        val result = value1 == value
        return result
    }

    /**
     * 生成使EQUAL_CONST公式为真的Z3条件
     */
    override fun Z3CondTrue(assignment: Assignment, patternMap: PatternMap): String {
        val ctx1 = assignment[var1]
        val ctx1Id = ctx1?.hashCode()?.absoluteValue ?: 0
        val varName = "${var1}_${ctx1Id}_${attr1}"
        
        return "($varName == \"$value\")"
    }
    
    /**
     * 生成使EQUAL_CONST公式为假的Z3条件
     */
    override fun Z3CondFalse(assignment: Assignment, patternMap: PatternMap): String {
        val ctx1 = assignment[var1]
        val ctx1Id = ctx1?.hashCode()?.absoluteValue ?: 0
        val varName = "${var1}_${ctx1Id}_${attr1}"
        
        return "($varName != \"$value\")"
    }

    override fun getUsedAttributes(assignment: Assignment, patternMap: PatternMap): Set<Pair<Attribute, ValueType>> {
        return setOf()
    }
}

fun fromCCFormulaBfunc(fml: FBfunc, manager: ContextManager?, registry: BFuncRegistry? = null): IFormula {
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
