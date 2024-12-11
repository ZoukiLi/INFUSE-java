package com.constraint.resolution.formulas

import com.CC.Constraints.Formulas.FForall
import com.CC.Constraints.Runtime.RuntimeNode
import com.constraint.resolution.*

data class ForallFormula(
    val variable: Variable,
    val subFormula: IFormula,
    val pattern: String,
    val weight: Double,
    val filter: String?,
    val filterDep: String?,
    val manager: ContextManager?,
    val userConfig: List<RepairDisableConfigItem>? = null
) : IFormula {
    override fun evaluate(assignment: Assignment, patternMap: PatternMap): Boolean {
        val filteredMap =
            patternMap + (pattern to patternMap[pattern]!!.filterBy(filter, filterDep?.let { assignment[it] }))
        return filteredMap.getValue(pattern).all { subFormula.evaluate(bind(assignment, variable, it), filteredMap) }
    }

    override fun repairF2T(assignment: Assignment, patternMap: PatternMap, lk: Boolean): RepairSuite {
        val filteredMap =
            patternMap + (pattern to patternMap[pattern]!!.filterBy(filter, filterDep?.let { assignment[it] }))
        val result =
            filteredMap.getValue(pattern).filter { !subFormula.evaluate(bind(assignment, variable, it), filteredMap) }
                // For each context in the pattern that does not satisfy the sub formula,
                .map {
                    // Remove this context from the pattern
                    RepairSuite(RemovalRepairAction(it, pattern), weight) or
                            // Or repair the sub formula for that context
                            subFormula.repairF2T(bind(assignment, variable, it), filteredMap, lk)
                }
                // Combine all the repair suites for each context that does not satisfy
                .fold(RepairSuite()) { acc, suite -> acc and suite }
        return result.filterImmutable(userConfig, manager)
    }

    override fun repairT2F(assignment: Assignment, patternMap: PatternMap, lk: Boolean): RepairSuite {
        val filteredMap =
            patternMap + (pattern to patternMap[pattern]!!.filterBy(filter, filterDep?.let { assignment[it] }))
        // Construct a default context from the pattern
        val newContext = manager?.constructContext(mapOf("name" to "new")) ?: makeContext(mapOf())
        val repairSuite = filteredMap.getValue(pattern).map {
            subFormula.repairT2F(bind(assignment, variable, it), filteredMap, lk)
        }.fold(RepairSuite()) { acc, suite -> acc or suite } or
                // Add the default context to the pattern
                when (subFormula.evaluate(bind(assignment, variable, newContext), filteredMap)) {
                    true -> RepairSuite(
                        AdditionRepairAction(newContext, pattern), weight
                    ) and subFormula.repairT2F(bind(assignment, variable, newContext), filteredMap, lk)

                    false -> RepairSuite(AdditionRepairAction(newContext, pattern), weight)
                }
        return repairSuite.filterImmutable(userConfig, manager)
    }

    override fun createRCTNode(assignment: Assignment, patternMap: PatternMap, ccRtNode: RuntimeNode?) =
        RCTNode(this, assignment, patternMap, ccRtNode)

    override fun createBranches(rctNode: RCTNode) =
        quantifierCreateRCTBranches(rctNode, subFormula, variable, pattern, filter, filterDep, manager)

    override fun evalRCTNode(rctNode: RCTNode) = rctNode.children.all { it.getTruth() }

    override fun repairNodeF2T(rctNode: RCTNode, lk: Boolean): RepairSuite {
        val result = rctNode.children.filter { !it.getTruth() }.map {
            // For each child that is false, repair the sub formula
            // Or remove the context from the pattern
            it.repairF2T(lk) or RepairSuite(
                RemovalRepairAction(it.assignment.getValue(variable), pattern), weight
            )
        }.fold(RepairSuite()) { acc, suite -> acc and suite }
        return result.filterImmutable(userConfig, manager)
    }

    override fun repairNodeT2F(rctNode: RCTNode, lk: Boolean): RepairSuite {
        val repairSuite = rctNode.children.map {
            // Repair the sub formula for each child
            it.repairT2F(lk)
        }.fold(RepairSuite()) { acc, suite -> acc or suite } or
                // Add the default context to the pattern
                repairByDefaultContext(rctNode, lk)
        return repairSuite.filterImmutable(userConfig, manager)
    }

    private fun repairByDefaultContext(rctNode: RCTNode, lk: Boolean): RepairSuite {
        val newContext = manager?.constructContext(mapOf("name" to "new")) ?: makeContext(mapOf())
        val addRepair = RepairSuite(AdditionRepairAction(newContext, pattern), weight)
        val addNode = subFormula.createRCTNode(bind(rctNode.assignment, variable, newContext), rctNode.patternMap)
        return when (addNode.getTruth()) {
            true -> addRepair and addNode.repairT2F(lk)
            false -> addRepair
        }
    }
}

fun fromCCFormulaForall(fml: FForall, manager: ContextManager?) = ForallFormula(
    fml.`var`,
    fromCCFormula(fml.subformula, manager),
    fml.pattern_id,
    1.0,
    fml.filter,
    fml.filterDep,
    manager,
    fml.disableConfigItems
)

fun quantifierCreateRCTBranches(
    rctNode: RCTNode,
    subFormula: IFormula,
    variable: Variable,
    pattern: String,
    filter: String?,
    filterDep: String?,
    manager: ContextManager?
) = rctNode.ccRtNode?.children?.map {
    val ccContext = it.varEnv.getValue(variable)
    val context = manager?.findContext(ccContext) ?: fromCCContext(ccContext)
    subFormula.createRCTNode(
        bind(rctNode.assignment, variable, context), rctNode.patternMap, it
    )
} ?: rctNode.patternMap.getValue(pattern).filterBy(filter, filterDep?.let { rctNode.assignment[it] })
    .map { bind(rctNode.assignment, variable, it) }.map {
        subFormula.createRCTNode(it, rctNode.patternMap)
    }
