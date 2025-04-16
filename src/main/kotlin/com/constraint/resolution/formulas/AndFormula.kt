package com.constraint.resolution.formulas

import com.CC.Constraints.Formulas.FAnd
import com.CC.Constraints.Runtime.RuntimeNode
import com.constraint.resolution.*
import com.constraint.resolution.bfunc.BFuncRegistry

data class AndFormula(
    val left: IFormula, val right: IFormula, val manager: ContextManager?, val userConfig: List<RepairDisableConfigItem>? = null
) : IFormula {
    override fun evaluate(assignment: Assignment, patternMap: PatternMap) =
        left.evaluate(assignment, patternMap) and right.evaluate(assignment, patternMap)

    override fun repairF2T(assignment: Assignment, patternMap: PatternMap, lk: Boolean): RepairSuite {
        if (!lk) {
            val leftTruth = left.evaluate(assignment, patternMap)
            val rightTruth = right.evaluate(assignment, patternMap)
            val result = when (Pair(leftTruth, rightTruth)) {
                // Both true, nothing to do
                Pair(true, true) -> RepairSuite()
                // Repair the branched formula that is false
                Pair(false, true) -> left.repairF2T(assignment, patternMap, lk)
                Pair(true, false) -> right.repairF2T(assignment, patternMap, lk)
                // Repair both branched formulas when both are false
                Pair(false, false) -> left.repairF2T(assignment, patternMap, lk) and right.repairF2T(
                    assignment, patternMap, lk
                )

                else -> RepairSuite()
            }
            return result.filterImmutable(userConfig, manager)
        }
        TODO("Locked repair not implemented for AndFormula")
    }

    override fun repairT2F(assignment: Assignment, patternMap: PatternMap, lk: Boolean) =
        // Repair both branched formulas when both are true
        (left.repairT2F(assignment, patternMap, lk) or right.repairT2F(assignment, patternMap, lk)).filterImmutable(
                userConfig,
                manager
            )

    override fun createRCTNode(assignment: Assignment, patternMap: PatternMap, ccRtNode: RuntimeNode?) =
        RCTNode(this, assignment, patternMap, ccRtNode)

    override fun createBranches(rctNode: RCTNode) = listOf(
        left.createRCTNode(rctNode.assignment, rctNode.patternMap, rctNode.ccRtNode?.children?.get(0)),
        right.createRCTNode(rctNode.assignment, rctNode.patternMap, rctNode.ccRtNode?.children?.get(1))
    )

    override fun evalRCTNode(rctNode: RCTNode) = rctNode.children.all { it.getTruth() }

    override fun repairNodeF2T(rctNode: RCTNode, lk: Boolean): RepairSuite {
        if (!lk) {
            val leftNode = rctNode.children[0]
            val rightNode = rctNode.children[1]
            val result = when (Pair(leftNode.getTruth(), rightNode.getTruth())) {
                // Repair the branched formula that is false
                Pair(false, true) -> leftNode.repairF2T(lk)
                Pair(true, false) -> rightNode.repairF2T(lk)
                // Repair both branched formulas when both are false
                Pair(false, false) -> leftNode.repairF2T(lk) and rightNode.repairF2T(lk)
                // Both true, nothing to do
                else -> RepairSuite(setOf())
            }
            return result.filterImmutable(userConfig, manager)
        }
        TODO("Not yet implemented")
    }

    override fun repairNodeT2F(rctNode: RCTNode, lk: Boolean) =
        rctNode.children[0].repairT2F(lk) or rctNode.children[1].repairT2F(lk)
            .filterImmutable(userConfig, manager)

    override fun repairF2TSeq(assignment: Assignment, patternMap: PatternMap, lk: Boolean): Sequence<RepairCase> {
        if (!lk) {
            val leftTruth = left.evaluate(assignment, patternMap)
            val rightTruth = right.evaluate(assignment, patternMap)
            val result = when (Pair(leftTruth, rightTruth)) {
                // Repair the branched formula that is false
                Pair(false, true) -> left.repairF2TSeq(assignment, patternMap, lk)
                Pair(true, false) -> right.repairF2TSeq(assignment, patternMap, lk)
                // Repair both branched formulas when both are false
                Pair(false, false) -> cartesianProduct(
                    left.repairF2TSeq(assignment, patternMap, lk),
                    right.repairF2TSeq(assignment, patternMap, lk)
                )

                // Both true, nothing to do
                else -> sequenceOf()
            }
            return filterImmutable(userConfig, manager, result)
        }
        TODO("Lock repair not implemented for AndFormula")
    }

    override fun repairT2FSeq(assignment: Assignment, patternMap: PatternMap, lk: Boolean): Sequence<RepairCase> {
        val leftSeq = left.repairT2FSeq(assignment, patternMap, lk)
        val rightSeq = right.repairT2FSeq(assignment, patternMap, lk)
        return chain(leftSeq, rightSeq)
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
        val result = childLeft && childRight
        verifyNode.truth = result
        verifyNode.affected = false
        return result
    }

    /**
     * 生成使AND公式为真的Z3条件
     */
    override fun Z3CondTrue(assignment: Assignment, patternMap: PatternMap): String {
        val leftCond = left.Z3CondTrue(assignment, patternMap)
        val rightCond = right.Z3CondTrue(assignment, patternMap)
        return "And($leftCond, $rightCond)"
    }
    
    /**
     * 生成使AND公式为假的Z3条件
     */
    override fun Z3CondFalse(assignment: Assignment, patternMap: PatternMap): String {
        val leftCond = left.Z3CondFalse(assignment, patternMap)
        val rightCond = right.Z3CondFalse(assignment, patternMap)
        return "Or($leftCond, $rightCond)"
    }

    override fun getUsedAttributes(assignment: Assignment, patternMap: PatternMap) =
        left.getUsedAttributes(assignment, patternMap) + right.getUsedAttributes(assignment, patternMap)
}

fun fromCCFormulaAnd(fml: FAnd, manager: ContextManager?, registry: BFuncRegistry? = null) = AndFormula(
    fromCCFormula(fml.subformulas.first(), manager, registry),
    fromCCFormula(fml.subformulas.last(), manager, registry),
    manager,
    fml.disableConfigItems
)
