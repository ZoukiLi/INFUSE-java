package com.constraint.resolution.formulas

import com.CC.Constraints.Formulas.FImplies
import com.CC.Constraints.Runtime.RuntimeNode
import com.constraint.resolution.*

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
        TODO("Not yet implemented")
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
}

fun fromCCFormulaImplies(fml: FImplies, manager: ContextManager?) =
    ImpliesFormula(
        fromCCFormula(fml.subformulas.first(), manager),
        fromCCFormula(fml.subformulas.last(), manager),
        manager,
        fml.disableConfigItems
    )
