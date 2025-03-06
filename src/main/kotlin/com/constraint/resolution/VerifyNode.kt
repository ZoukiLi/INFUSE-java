package com.constraint.resolution

import com.CC.Constraints.Runtime.RuntimeNode
import com.CC.Contexts.Context
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

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
        // Update all children first
        val ccChildrenAffected = ccRtNode?.children?.map {
            it.verifyNode?.updateAffected() == true
        }?.any { it } == true
        
        // Set affected if:
        // 1. Node is invalid OR
        // 2. Has new children OR
        // 3. Any CC children are affected
        affected = !valid || newChildren.any() || ccChildrenAffected
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

    fun checkCase(repairCase: RepairCase, manager: ContextManager): Boolean {
        val adds = repairCase.actions.filter { it.repairType() == RepairType.ADDITION }
        adds.forEach {
            it.applyTo(manager)
        }
        formula.applyCaseToVerifyNode(this, repairCase)
        adds.forEach {
            it.reverse(manager)
        }
        updateAffected()
        logger.debug { "VerifyTree:\n${display()}" }
        val result = formula.evalVerifyNode(this)
        logger.debug { "VerifyTreeEval:\n${display()}" }
        reset()
        return result
    }

    fun display(depth: Int = 0): String {
        val sb = StringBuilder()
        val indent = "  ".repeat(depth)
        
        // Display current node state
        sb.appendLine("$indent[VerifyNode]")
        sb.appendLine("$indent├─ Valid: $valid")
        sb.appendLine("$indent├─ Affected: $affected")
        sb.appendLine("$indent└─ Truth: $truth")

        // Display CCRtNode children
        ccRtNode?.children?.forEachIndexed { index, child ->
            child.verifyNode?.let { childNode ->
                sb.appendLine("$indent  Child $index (CC):")
                sb.append(childNode.display(depth + 2))
            }
        }

        // Display new children
        newChildren.forEachIndexed { index, child ->
            sb.appendLine("$indent  Child $index (New):")
            sb.append(child.display(depth + 2))
        }

        return sb.toString()
    }

    override fun toString(): String = display()
}
