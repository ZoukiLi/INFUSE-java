package com.constraint.resolution

import com.CC.Constraints.Runtime.RuntimeNode

class RCTNode(
    // Origin Formula Node
    private val formula: IFormula,
    // Assignment
    val assignment: Assignment,
    // Pattern Map
    val patternMap: PatternMap,
    // Optional CCRuntimeNode
    val ccRtNode: RuntimeNode? = null
) {
    // Children of the node
    var children: List<RCTNode> = listOf()

    // Optional truth value of the node
    private var isTruth: Boolean? = null

    private var branchesCreated = false

    // Get the truth value of the node
    fun getTruth(): Boolean {
        if (isTruth != null) {
            return isTruth!!
        }
        if (ccRtNode != null) {
            isTruth = ccRtNode.isTruth
            return isTruth!!
        }
        // No available truth value
        // Eval based on children
        if (!branchesCreated) {
            createBranches()
        }
        isTruth = formula.evalRCTNode(this)
        return isTruth!!
    }

    fun repairF2T(lk: Boolean = false): RepairSuite {
        createBranches()
        return formula.repairNodeF2T(this, lk)
    }
    fun repairT2F(lk: Boolean = false): RepairSuite {
        createBranches()
        return formula.repairNodeT2F(this, lk)
    }
    fun createBranches() {
        if (branchesCreated) {
            return
        }
        children = formula.createBranches(this)
        branchesCreated = true
    }
}