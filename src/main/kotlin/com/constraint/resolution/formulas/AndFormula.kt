package com.constraint.resolution.formulas

import com.CC.Constraints.Formulas.FAnd
import com.CC.Constraints.Runtime.RuntimeNode
import com.constraint.resolution.*

data class AndFormula(val left: IFormula, val right: IFormula) : IFormula {
    override fun evaluate(assignment: Assignment, patternMap: PatternMap) =
        left.evaluate(assignment, patternMap) and right.evaluate(assignment, patternMap)

    override fun repairF2T(assignment: Assignment, patternMap: PatternMap, lk: Boolean): RepairSuite {
        if (!lk) {
            val leftTruth = left.evaluate(assignment, patternMap)
            val rightTruth = right.evaluate(assignment, patternMap)
            when (Pair(leftTruth, rightTruth)) {
                // Both true, nothing to do
                Pair(true, true) -> return RepairSuite(setOf())
                // Repair the branched formula that is false
                Pair(false, true) -> return left.repairF2T(assignment, patternMap, lk)
                Pair(true, false) -> return right.repairF2T(assignment, patternMap, lk)
                // Repair both branched formulas when both are false
                Pair(false, false) ->
                    return left.repairF2T(assignment, patternMap, lk) and right.repairF2T(assignment, patternMap, lk)
            }
        }
        TODO("Locked repair not implemented for AndFormula")
    }

    override fun repairT2F(assignment: Assignment, patternMap: PatternMap, lk: Boolean) =
        // Repair both branched formulas when both are true
        left.repairT2F(assignment, patternMap, lk) or right.repairT2F(assignment, patternMap, lk)

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
            return when (Pair(leftNode.getTruth(), rightNode.getTruth())) {
                // Repair the branched formula that is false
                Pair(false, true) -> leftNode.repairF2T(lk)
                Pair(true, false) -> rightNode.repairF2T(lk)
                // Repair both branched formulas when both are false
                Pair(false, false) -> leftNode.repairF2T(lk) and rightNode.repairF2T(lk)
                // Both true, nothing to do
                else -> RepairSuite(setOf())
            }
        }
        TODO("Not yet implemented")
    }

    override fun repairNodeT2F(rctNode: RCTNode, lk: Boolean) =
        rctNode.children[0].repairT2F(lk) or rctNode.children[1].repairT2F(lk)
}

fun fromCCFormulaAnd(fml: FAnd) =
    AndFormula(fromCCFormula(fml.subformulas.first()), fromCCFormula(fml.subformulas.last()))
