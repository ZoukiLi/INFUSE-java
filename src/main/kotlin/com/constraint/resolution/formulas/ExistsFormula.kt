package com.constraint.resolution.formulas

import com.CC.Constraints.Formulas.FExists
import com.CC.Constraints.Runtime.RuntimeNode
import com.constraint.resolution.*

data class ExistsFormula(
    val variable: Variable,
    val subFormula: IFormula,
    val pattern: String,
    val weight: Double,
    val filter: String?,
    val filterDep: String?,
    val manager: ContextManager?,
    val immutablePattern: List<String>? = null
) : IFormula {
    override fun evaluate(assignment: Assignment, patternMap: PatternMap): Boolean {
        val filteredMap =
            patternMap + (pattern to patternMap[pattern]!!.filterBy(filter, filterDep?.let { assignment[it] }))
        return filteredMap.getValue(pattern).any { subFormula.evaluate(bind(assignment, variable, it), filteredMap) }
    }

    override fun repairF2T(assignment: Assignment, patternMap: PatternMap, lk: Boolean): RepairSuite {
        val filteredMap =
            patternMap + (pattern to patternMap[pattern]!!.filterBy(filter, filterDep?.let { assignment[it] }))
        val newContext = makeContext(mapOf())
        val repairSuite = filteredMap.getValue(pattern).map {
            subFormula.repairF2T(bind(assignment, variable, it), filteredMap, lk)
        }.fold(RepairSuite()) { acc, suite -> acc or suite } or when (subFormula.evaluate(
            bind(
                assignment, variable, newContext
            ), filteredMap
        )) {
            true -> RepairSuite(AdditionRepairAction(newContext, pattern), weight)
            false -> RepairSuite(AdditionRepairAction(newContext, pattern), weight) and subFormula.repairF2T(
                bind(
                    assignment, variable, newContext
                ), filteredMap, lk
            )
        }
        return repairSuite.filterImmutable(immutablePattern, manager)
    }

    override fun repairT2F(assignment: Assignment, patternMap: PatternMap, lk: Boolean): RepairSuite {
        val filteredMap =
            patternMap + (pattern to patternMap[pattern]!!.filterBy(filter, filterDep?.let { assignment[it] }))
        return filteredMap.getValue(pattern).filter { subFormula.evaluate(bind(assignment, variable, it), filteredMap) }
            .map {
                RepairSuite(RemovalRepairAction(it, pattern), weight) or subFormula.repairT2F(
                    bind(
                        assignment, variable, it
                    ), filteredMap, lk
                )
            }.fold(RepairSuite()) { acc, suite -> acc and suite }.filterImmutable(immutablePattern, manager)
    }

    override fun createRCTNode(assignment: Assignment, patternMap: PatternMap, ccRtNode: RuntimeNode?) =
        RCTNode(this, assignment, patternMap, ccRtNode)

    override fun createBranches(rctNode: RCTNode) =
        quantifierCreateRCTBranches(rctNode, subFormula, variable, pattern, filter, filterDep, manager)

    override fun evalRCTNode(rctNode: RCTNode) = rctNode.children.any { it.getTruth() }

    override fun repairNodeF2T(rctNode: RCTNode, lk: Boolean): RepairSuite {
        val repairSuite = rctNode.children.map {
            // Repair the sub formula for that context
            it.repairF2T(lk)
        }.fold(RepairSuite()) { acc, suite -> acc or suite } or repairByDefaultContext(rctNode, lk)
        return repairSuite.filterImmutable(
            immutablePattern, manager
        )
    }

    override fun repairNodeT2F(rctNode: RCTNode, lk: Boolean) = rctNode.children.filter { it.getTruth() }.map {
        it.repairT2F(lk) or RepairSuite(
            RemovalRepairAction(it.assignment.getValue(variable), pattern), weight
        )
    }.fold(RepairSuite()) { acc, suite -> acc and suite }.filterImmutable(immutablePattern, manager)

    private fun repairByDefaultContext(rctNode: RCTNode, lk: Boolean): RepairSuite {
        val newContext = makeContext(mapOf())
        val addRepair = RepairSuite(AdditionRepairAction(newContext, pattern), weight)
        val addNode = subFormula.createRCTNode(bind(rctNode.assignment, variable, newContext), rctNode.patternMap)
        return when (addNode.getTruth()) {
            true -> addRepair
            false -> addRepair and addNode.repairF2T(lk)
        }
    }
}

fun fromCCFormulaExists(fml: FExists, manager: ContextManager?) = ExistsFormula(
    fml.`var`,
    fromCCFormula(fml.subformula, manager),
    fml.pattern_id,
    1.0,
    fml.filter,
    fml.filterDep,
    manager,
    fml.immutablePattern
)
