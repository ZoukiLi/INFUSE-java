package com.constraint.resolution.formulas

import com.CC.Constraints.Formulas.FOr
import com.CC.Constraints.Runtime.RuntimeNode
import com.constraint.resolution.*

data class OrFormula(
    val left: IFormula,
    val right: IFormula,
    val manager: ContextManager?,
    val userConfig: List<RepairDisableConfigItem>? = null
) : IFormula {
    override fun evaluate(assignment: Assignment, patternMap: PatternMap) =
        left.evaluate(assignment, patternMap) or right.evaluate(assignment, patternMap)

    override fun repairF2T(assignment: Assignment, patternMap: PatternMap, lk: Boolean) =
        (left.repairF2T(assignment, patternMap, lk) or right.repairF2T(assignment, patternMap, lk)).filterImmutable(
            userConfig, manager
        )

    override fun repairT2F(assignment: Assignment, patternMap: PatternMap, lk: Boolean): RepairSuite {
        if (!lk) {
            val leftTruth = left.evaluate(assignment, patternMap)
            val rightTruth = right.evaluate(assignment, patternMap)
            return when (leftTruth to rightTruth) {
                true to true -> left.repairT2F(assignment, patternMap, lk) and right.repairT2F(
                    assignment, patternMap, lk
                )

                false to true -> right.repairT2F(assignment, patternMap, lk)
                true to false -> left.repairT2F(assignment, patternMap, lk)
                else -> RepairSuite(setOf())
            }.filterImmutable(userConfig, manager)
        }
        TODO("Locked repair not implemented for OrFormula")
    }

    override fun repairF2TSeq(assignment: Assignment, patternMap: PatternMap, lk: Boolean): Sequence<RepairCase> {
        val leftSeq = left.repairF2TSeq(assignment, patternMap, lk)
        val rightSeq = right.repairF2TSeq(assignment, patternMap, lk)
        return chain(leftSeq, rightSeq)
    }

    override fun repairT2FSeq(assignment: Assignment, patternMap: PatternMap, lk: Boolean): Sequence<RepairCase> {
        if (!lk) {
            val leftTruth = left.evaluate(assignment, patternMap)
            val rightTruth = right.evaluate(assignment, patternMap)
            return when (leftTruth to rightTruth) {
                true to true -> cartesianProduct(
                    left.repairT2FSeq(assignment, patternMap, lk),
                    right.repairT2FSeq(assignment, patternMap, lk)
                )
                false to true -> right.repairT2FSeq(assignment, patternMap, lk)
                true to false -> left.repairT2FSeq(assignment, patternMap, lk)
                else -> sequenceOf()
            }
        }
        return sequenceOf()
    }

    override fun createRCTNode(assignment: Assignment, patternMap: PatternMap, ccRtNode: RuntimeNode?) =
        RCTNode(this, assignment, patternMap, ccRtNode)

    override fun createBranches(rctNode: RCTNode) = listOf(
        left.createRCTNode(rctNode.assignment, rctNode.patternMap, rctNode.ccRtNode?.children?.get(0)),
        right.createRCTNode(rctNode.assignment, rctNode.patternMap, rctNode.ccRtNode?.children?.get(1))
    )

    override fun evalRCTNode(rctNode: RCTNode) = rctNode.children.any { it.getTruth() }

    override fun repairNodeF2T(rctNode: RCTNode, lk: Boolean) =
        rctNode.children[0].repairF2T(lk) or rctNode.children[1].repairF2T(lk)
            .filterImmutable(userConfig, manager)

    override fun repairNodeT2F(rctNode: RCTNode, lk: Boolean): RepairSuite {
        if (!lk) {
            val leftNode = rctNode.children[0]
            val rightNode = rctNode.children[1]
            return when (Pair(leftNode.getTruth(), rightNode.getTruth())) {
                Pair(true, true) -> leftNode.repairT2F(lk) and rightNode.repairT2F(lk)
                Pair(false, true) -> rightNode.repairT2F(lk)
                Pair(true, false) -> leftNode.repairT2F(lk)
                else -> RepairSuite(setOf())
            }.filterImmutable(userConfig, manager)
        }
        TODO("Not yet implemented")
    }

}

fun fromCCFormulaOr(fml: FOr, manager: ContextManager?) = OrFormula(
    fromCCFormula(fml.subformulas.first(), manager),
    fromCCFormula(fml.subformulas.last(), manager),
    manager,
    fml.disableConfigItems
)
