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

    // Get the truth value of the node
    fun getTruth(): Boolean {
        if (isTruth != null) {
            return isTruth!!
        }
        if (ccRtNode != null) {
            isTruth = ccRtNode.isTruth
            return isTruth!!
        }
        children = formula.createBranches(this)
        // Eval based on children
        isTruth = formula.evalRCTNode(this)
        return isTruth!!
    }

    fun repairF2T(lk: Boolean = false) = formula.repairNodeF2T(this, lk)
    fun repairT2F(lk: Boolean = false) = formula.repairNodeT2F(this, lk)
}