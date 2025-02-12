package com.constraint.resolution

import com.CC.Constraints.Runtime.RuntimeNode
import com.CC.Contexts.Context

class VerifyNode(
    private val formula: IFormula,
    val ccRtNode: RuntimeNode? = null,
) {
    // Children of new added nodes
    val newChildren: MutableList<VerifyNode> = mutableListOf()
    var valid = true
    var affected = false
    var truth: Boolean? = null
    var varEnv: Map<String, Context>? = null

    fun getValidChildren(): Sequence<VerifyNode> {
        // get valid children of ccRtNode
        val ccChildren = ccRtNode?.children?.asSequence()?.mapNotNull { it.verifyNode }?.filter { it.valid }
        // get valid children of new added nodes
        val newChildren = newChildren.asSequence().filter { it.valid }
        return ccChildren?.plus(newChildren) ?: newChildren
    }

    fun updateAffected(): Boolean {
        if (!valid || newChildren.any()) {
            affected = true
            return true
        }
        affected = ccRtNode?.children?.any { it.verifyNode.updateAffected() } == true
        return affected
    }

    fun reset() {
        valid = true
        affected = false
        truth = null
        newChildren.clear()
        ccRtNode?.children?.forEach {
            it.verifyNode?.reset()
        }
    }

    fun unaffectedTruth(): Boolean? {
       if (!affected) {
            if (truth != null) {
                return truth
            }
            if (ccRtNode != null) {
                truth = ccRtNode.isTruth
                return truth
            }
        }
        return null
    }

    fun getChild(index: Int): VerifyNode? {
        return ccRtNode?.children?.getOrNull(index)?.verifyNode ?: newChildren.getOrNull(index)
    }

    fun checkCase(repairCase: RepairCase): Boolean {
        formula.applyCaseToVerifyNode(this, repairCase)
        updateAffected()
        val result = formula.evalVerifyNode(this)
        reset()
        return result
    }
}

