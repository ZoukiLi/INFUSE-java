package com.constraint.resolution.formulas

import com.CC.Constraints.Formulas.FImplies
import com.CC.Constraints.Runtime.RuntimeNode
import com.constraint.resolution.*
import com.constraint.resolution.bfunc.BFuncRegistry

data class ImpliesFormula(
    val left: IFormula,
    val right: IFormula,
    val manager: ContextManager?,
    val userConfig: List<RepairDisableConfigItem>? = null
) : IFormula {
    override fun evaluate(assignment: Assignment, patternMap: PatternMap) =
        !left.evaluate(assignment, patternMap) or right.evaluate(assignment, patternMap)

    override fun repairF2T(assignment: Assignment, patternMap: PatternMap, lk: Boolean) =
        (left.repairT2F(assignment, patternMap, lk) or right.repairF2T(assignment, patternMap, lk)).filterImmutable(
            userConfig,
            manager
        )

    override fun repairT2F(assignment: Assignment, patternMap: PatternMap, lk: Boolean): RepairSuite {
        if (!lk) {
            val leftTruth = left.evaluate(assignment, patternMap)
            val rightTruth = right.evaluate(assignment, patternMap)
            val suite = when (leftTruth to rightTruth) {
                true to true -> right.repairT2F(assignment, patternMap, lk)
                false to true -> left.repairF2T(assignment, patternMap, lk) and right.repairT2F(
                    assignment,
                    patternMap,
                    lk
                )

                false to false -> left.repairF2T(assignment, patternMap, lk)
                else -> RepairSuite(setOf())
            }
            return suite.filterImmutable(userConfig, manager)
        }
        TODO("Locked repairs not implemented for ImpliesFormula")
    }

    override fun repairF2TSeq(assignment: Assignment, patternMap: PatternMap, lk: Boolean): Sequence<RepairCase> {
        val leftSeq = left.repairT2FSeq(assignment, patternMap, lk)
        val rightSeq = right.repairF2TSeq(assignment, patternMap, lk)
        return chain(leftSeq, rightSeq)
    }

    override fun repairT2FSeq(assignment: Assignment, patternMap: PatternMap, lk: Boolean): Sequence<RepairCase> {
        if (!lk) {
            val leftTruth = left.evaluate(assignment, patternMap)
            val rightTruth = right.evaluate(assignment, patternMap)
            val result = when (leftTruth to rightTruth) {
                true to true -> right.repairT2FSeq(assignment, patternMap, lk)
                false to true -> cartesianProduct(
                    left.repairF2TSeq(assignment, patternMap, lk),
                    right.repairT2FSeq(assignment, patternMap, lk)
                )
                false to false -> left.repairF2TSeq(assignment, patternMap, lk)
                else -> sequenceOf()
            }
            return filterImmutable(userConfig, manager, result)
        }
        TODO("Locked repairs not implemented for ImpliesFormula")
    }

    override fun initVerifyNode(ccRtNode: RuntimeNode): VerifyNode {
        if (ccRtNode.verifyNode != null) {
            return ccRtNode.verifyNode
        }
        val node = VerifyNode(this, ccRtNode)
        ccRtNode.verifyNode = node
        left.initVerifyNode(ccRtNode.children[0])
        right.initVerifyNode(ccRtNode.children[1])
        return node
    }

    override fun applyCaseToVerifyNode(
        verifyNode: VerifyNode,
        repairCase: RepairCase
    ) {
        // pass the repair case to the branched formulas
        val childLeft = verifyNode.getChild(0)
        val childRight = verifyNode.getChild(1)
        childLeft?.let { left.applyCaseToVerifyNode(it, repairCase) }
        childRight?.let { right.applyCaseToVerifyNode(it, repairCase) }
    }

    override fun evalVerifyNode(verifyNode: VerifyNode): Boolean {
        verifyNode.unaffectedTruth()?.let { return it }
        val childLeft = verifyNode.getChild(0)?.let { left.evalVerifyNode(it) } == true
        val childRight = verifyNode.getChild(1)?.let { right.evalVerifyNode(it) } == true
        val result = !childLeft or childRight
        verifyNode.truth = result
        verifyNode.affected = false
        return result
    }

    override fun createRCTNode(assignment: Assignment, patternMap: PatternMap, ccRtNode: RuntimeNode?) =
        RCTNode(this, assignment, patternMap, ccRtNode)

    override fun createBranches(rctNode: RCTNode) = listOf(
        left.createRCTNode(rctNode.assignment, rctNode.patternMap, rctNode.ccRtNode?.children?.get(0)),
        right.createRCTNode(rctNode.assignment, rctNode.patternMap, rctNode.ccRtNode?.children?.get(1))
    )

    override fun evalRCTNode(rctNode: RCTNode) = !rctNode.children[0].getTruth() or rctNode.children[1].getTruth()

    override fun repairNodeF2T(rctNode: RCTNode, lk: Boolean) =
        (rctNode.children[1].repairF2T(lk) or rctNode.children[0].repairT2F(lk)).filterImmutable(userConfig, manager)

    override fun repairNodeT2F(rctNode: RCTNode, lk: Boolean): RepairSuite {
        if (!lk) {
            val leftNode = rctNode.children[0]
            val rightNode = rctNode.children[1]
            val suite = when (Pair(leftNode.getTruth(), rightNode.getTruth())) {
                Pair(true, true) -> rightNode.repairT2F(lk)
                Pair(false, true) -> leftNode.repairF2T(lk) and rightNode.repairT2F(lk)
                Pair(false, false) -> leftNode.repairF2T(lk)
                else -> RepairSuite(setOf())
            }
            return suite.filterImmutable(userConfig, manager)
        }
        TODO("Not yet implemented")
    }

    /**
     * 生成使IMPLIES公式为真的Z3条件
     */
    override fun Z3CondTrue(assignment: Assignment, patternMap: PatternMap): String {
        val leftFalse = left.Z3CondFalse(assignment, patternMap)
        val rightTrue = right.Z3CondTrue(assignment, patternMap)
        return "Or($leftFalse, $rightTrue)"
    }
    
    /**
     * 生成使IMPLIES公式为假的Z3条件
     */
    override fun Z3CondFalse(assignment: Assignment, patternMap: PatternMap): String {
        val leftTrue = left.Z3CondTrue(assignment, patternMap)
        val rightFalse = right.Z3CondFalse(assignment, patternMap)
        return "And($leftTrue, $rightFalse)"
    }

    override fun getUsedAttributes(assignment: Assignment, patternMap: PatternMap) =
        left.getUsedAttributes(assignment, patternMap) + right.getUsedAttributes(assignment, patternMap)
}

fun fromCCFormulaImplies(fml: FImplies, manager: ContextManager?, registry: BFuncRegistry? = null) =
    ImpliesFormula(
        fromCCFormula(fml.subformulas.first(), manager, registry),
        fromCCFormula(fml.subformulas.last(), manager, registry),
        manager,
        fml.disableConfigItems
    )
