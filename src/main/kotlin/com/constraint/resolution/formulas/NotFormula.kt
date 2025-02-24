package com.constraint.resolution.formulas

import com.CC.Constraints.Formulas.FNot
import com.CC.Constraints.Runtime.RuntimeNode
import com.constraint.resolution.*

data class NotFormula(
    val subFormula: IFormula, val manager: ContextManager?, val userConfig: List<RepairDisableConfigItem>? = null
) : IFormula {
    override fun evaluate(assignment: Assignment, patternMap: PatternMap) = !subFormula.evaluate(assignment, patternMap)

    override fun repairF2T(
        assignment: Assignment, patternMap: PatternMap, lk: Boolean
    ) = subFormula.repairT2F(assignment, patternMap, lk).filterImmutable(userConfig, manager)

    override fun repairT2F(
        assignment: Assignment, patternMap: PatternMap, lk: Boolean
    ) = subFormula.repairF2T(assignment, patternMap, lk).filterImmutable(userConfig, manager)

    override fun repairF2TSeq(assignment: Assignment, patternMap: PatternMap, lk: Boolean): Sequence<RepairCase> {
        return subFormula.repairT2FSeq(assignment, patternMap, lk)
    }

    override fun repairT2FSeq(assignment: Assignment, patternMap: PatternMap, lk: Boolean): Sequence<RepairCase> {
        return subFormula.repairF2TSeq(assignment, patternMap, lk)
    }

    override fun initVerifyNode(ccRtNode: RuntimeNode): VerifyNode {
        if (ccRtNode.verifyNode != null) {
            return ccRtNode.verifyNode
        }
        val node = VerifyNode(this, ccRtNode)
        ccRtNode.verifyNode = node
        subFormula.initVerifyNode(ccRtNode.children.first())
        return node
    }

    override fun applyCaseToVerifyNode(
        verifyNode: VerifyNode,
        repairCase: RepairCase
    ) {
        // pass the repair case to the child node
        verifyNode.getChild(0)?.let { subFormula.applyCaseToVerifyNode(it, repairCase) }
    }

    override fun evalVerifyNode(verifyNode: VerifyNode): Boolean {
        verifyNode.unaffectedTruth()?.let { return it }
        val child = verifyNode.getChild(0)?.let { subFormula.evalVerifyNode(it) } == true
        val result = !child
        verifyNode.truth = result
        verifyNode.affected = false
        return result
    }

    override fun createRCTNode(assignment: Assignment, patternMap: PatternMap, ccRtNode: RuntimeNode?) =
        RCTNode(this, assignment, patternMap, ccRtNode)

    override fun createBranches(rctNode: RCTNode) =
        listOf(subFormula.createRCTNode(rctNode.assignment, rctNode.patternMap, rctNode.ccRtNode?.children?.first()))

    override fun evalRCTNode(rctNode: RCTNode) = !rctNode.children.first().getTruth()

    override fun repairNodeF2T(rctNode: RCTNode, lk: Boolean) =
        rctNode.children.first().repairT2F(lk).filterImmutable(userConfig, manager)

    override fun repairNodeT2F(rctNode: RCTNode, lk: Boolean) =
        rctNode.children.first().repairF2T(lk).filterImmutable(userConfig, manager)
}

fun fromCCFormulaNot(fml: FNot, manager: ContextManager?) =
    NotFormula(fromCCFormula(fml.subformula, manager), manager, fml.disableConfigItems)
