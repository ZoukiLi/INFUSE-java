package com.constraint.resolution.formulas

import com.CC.Constraints.Formulas.FForall
import com.CC.Constraints.Runtime.RuntimeNode
import com.constraint.resolution.*
import com.constraint.resolution.bfunc.BFuncRegistry
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

data class ForallFormula(
    val variable: Variable,
    val subFormula: IFormula,
    val pattern: String,
    val weight: Double,
    val filter: String?,
    val filterDep: String?,
    val manager: ContextManager?,
    val userConfig: RepairConfig? = null
) : IFormula {
    override fun evaluate(assignment: Assignment, patternMap: PatternMap): Boolean {
        val filteredPattern = patternMap[pattern]?.filterBy(filter, filterDep?.let { assignment[it] }) ?: return true
        return filteredPattern.all { subFormula.evaluate(bind(assignment, variable, it), patternMap) }
    }

    override fun repairF2T(assignment: Assignment, patternMap: PatternMap, lk: Boolean): RepairSuite {
        val filteredPattern =
            patternMap[pattern]?.filterBy(filter, filterDep?.let { assignment[it] }) ?: return RepairSuite()

        val repairSuite =
            filteredPattern.filterNot { subFormula.evaluate(bind(assignment, variable, it), patternMap) }.map {
                val removalSuite = RepairSuite(RemovalRepairAction(it, pattern), weight)
                val revertSuite = subFormula.repairF2T(bind(assignment, variable, it), patternMap, lk)
                removalSuite or revertSuite
            }.fold(RepairSuite()) { acc, suite -> acc and suite }
        return repairSuite.filterImmutable(userConfig?.items, manager)
    }

    override fun repairT2F(assignment: Assignment, patternMap: PatternMap, lk: Boolean): RepairSuite {
        val filteredPattern =
            patternMap[pattern]?.filterBy(filter, filterDep?.let { assignment[it] }) ?: return RepairSuite()
        val newContext = manager?.constructContext(mapOf("name" to "new")) ?: makeContext(mapOf())

        val revertSuite = filteredPattern.map { subFormula.repairT2F(bind(assignment, variable, it), patternMap, lk) }
            .fold(RepairSuite()) { acc, suite -> acc or suite }

        val addSuite = RepairSuite(AdditionRepairAction(newContext, pattern), weight)
        val bindNew = bind(assignment, variable, newContext)
        val newCtxSuite = when (subFormula.evaluate(bindNew, patternMap)) {
            true -> addSuite and subFormula.repairT2F(bindNew, patternMap, lk)
            false -> addSuite
        }

        val repairSuite = revertSuite or newCtxSuite
        return repairSuite.filterImmutable(userConfig?.items, manager)
    }

    override fun repairF2TSeq(assignment: Assignment, patternMap: PatternMap, lk: Boolean): Sequence<RepairCase> {
        val filteredPattern = patternMap[pattern]?.filterBy(filter, filterDep?.let { assignment[it] }) ?: return emptySequence()

        val repairSeqs = filteredPattern.filterNot { subFormula.evaluate(bind(assignment, variable, it), patternMap) }.map {
            val removalSeq = sequenceOf(RepairCase(RemovalRepairAction(it, pattern), weight))
            val revertSeq = subFormula.repairF2TSeq(bind(assignment, variable, it), patternMap, lk)
//          user config
            if (userConfig?.prefer == PREFER_BRANCH)
                chain(removalSeq, removalSeq)
            else
                chain(revertSeq, removalSeq)
        }
        val results = cartesianProduct(repairSeqs)

        return results.filterByConfig(userConfig, manager)
    }

    override fun repairT2FSeq(assignment: Assignment, patternMap: PatternMap, lk: Boolean): Sequence<RepairCase> {
        val filteredPattern = patternMap[pattern]?.filterBy(filter, filterDep?.let { assignment[it] }) ?: return emptySequence()
        val newContext = manager?.constructContext(mapOf("name" to "new")) ?: makeContext(mapOf())

        val revertSeq = filteredPattern.map { subFormula.repairT2FSeq(bind(assignment, variable, it), patternMap, lk) }
        val addSeq = sequenceOf(RepairCase(AdditionRepairAction(newContext, pattern), weight))
        val bindNew = bind(assignment, variable, newContext)
        val newCtxSeq = when (subFormula.evaluate(bindNew, patternMap)) {
            true -> cartesianProduct(addSeq, subFormula.repairT2FSeq(bindNew, patternMap, lk))
            false -> addSeq
        }

        val repairSeq =
            when (userConfig?.prefer) {
                PREFER_BRANCH -> chain(newCtxSeq, chain(revertSeq))
                else -> chain(chain(revertSeq), newCtxSeq)
            }
        return repairSeq.filterByConfig(userConfig, manager)
    }

    override fun initVerifyNode(
        ccRtNode: RuntimeNode
    ): VerifyNode {
        if (ccRtNode.verifyNode != null) {
            return ccRtNode.verifyNode
        }
        val node = VerifyNode(this, ccRtNode)
        ccRtNode.verifyNode = node
        ccRtNode.children.forEach {
            subFormula.initVerifyNode(it)
        }
        return node
    }

    override fun applyCaseToVerifyNode(verifyNode: VerifyNode, repairCase: RepairCase) {
        if (manager == null) {
            logger.debug { "Manager is null, skipping case application" }
            return
        }
        repairCase.actions.filter {
            it.inPattern(pattern, manager)
        }.forEach {
            when (it) {
                is AdditionRepairAction -> {
                    val ccContext = it.context.ccContext!!
                    val varEnv = verifyNode.ccRtNode?.varEnv ?: verifyNode.varEnv
                    val binding = variable to ccContext
                    val bindVarEnv = varEnv?.plus(binding) ?: mapOf(binding)
                    val newNode = VerifyNode(subFormula)
                    newNode.varEnv = bindVarEnv
                    newNode.affected = true
                    verifyNode.newChildren.add(newNode)
                }

                is RemovalRepairAction -> {
                    val ccContext = it.context.ccContext!!
                    verifyNode.ccRtNode?.children?.filter { it.varEnv[variable] == ccContext }?.forEach {
                        it.verifyNode?.valid = false
                    }
                }
            }
        }
        // pass the case down
        verifyNode.getValidChildren().forEach {
            subFormula.applyCaseToVerifyNode(it, repairCase)
        }
    }

    override fun evalVerifyNode(verifyNode: VerifyNode): Boolean {
        verifyNode.unaffectedTruth()?.let { return it }
        val childrenResults = verifyNode.getValidChildren().map { subFormula.evalVerifyNode(it) }
        val result = childrenResults.all { it }
        verifyNode.truth = result
        verifyNode.affected = false
        return result
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
        return result.filterImmutable(userConfig?.items, manager)
    }

    override fun repairNodeT2F(rctNode: RCTNode, lk: Boolean): RepairSuite {
        val repairSuite = rctNode.children.map {
            // Repair the sub formula for each child
            it.repairT2F(lk)
        }.fold(RepairSuite()) { acc, suite -> acc or suite } or
                // Add the default context to the pattern
                repairByDefaultContext(rctNode, lk)
        return repairSuite.filterImmutable(userConfig?.items, manager)
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

    /**
     * 生成使FORALL公式为真的Z3条件
     */
    override fun Z3CondTrue(assignment: Assignment, patternMap: PatternMap): String {
        val filteredPattern = patternMap[pattern]?.filterBy(filter, filterDep?.let { assignment[it] })?.toList() ?: return "True"

        // 如果没有实例，FORALL为真（所有都满足）
        if (filteredPattern.isEmpty()) {
            return "True"
        }
        
        // 为每个模式实例生成条件
        val conditions = filteredPattern.map { 
            val boundAssignment = bind(assignment, variable, it)
            subFormula.Z3CondTrue(boundAssignment, patternMap)
        }
        
        // 所有为真，FORALL为真
        return if (conditions.size == 1) {
            conditions.first()
        } else {
            "And(${conditions.joinToString(", ")})"
        }
    }
    
    /**
     * 生成使FORALL公式为假的Z3条件
     */
    override fun Z3CondFalse(assignment: Assignment, patternMap: PatternMap): String {
        val filteredPattern = patternMap[pattern]?.filterBy(filter, filterDep?.let { assignment[it] })?.toList() ?: return "False"
        
        // 如果没有实例，FORALL无法为假
        if (filteredPattern.isEmpty()) {
            return "False"
        }
        
        // 为每个模式实例生成条件
        val conditions = filteredPattern.map { 
            val boundAssignment = bind(assignment, variable, it)
            subFormula.Z3CondFalse(boundAssignment, patternMap)
        }
        
        // 任一为假，FORALL为假
        return if (conditions.size == 1) {
            conditions.first()
        } else {
            "Or(${conditions.joinToString(", ")})"
        }
    }

    override fun getUsedAttributes(assignment: Assignment, patternMap: PatternMap): Set<Pair<Attribute, ValueType>> {
        val filteredPattern = patternMap[pattern]?.filterBy(filter, filterDep?.let { assignment[it] }) ?: return emptySet()
        return filteredPattern.flatMap { subFormula.getUsedAttributes(bind(assignment, variable, it), patternMap) }.toSet()
    }
}

fun fromCCFormulaForall(fml: FForall, manager: ContextManager?, registry: BFuncRegistry? = null) = ForallFormula(
    fml.`var`,
    fromCCFormula(fml.subformula, manager, registry),
    fml.pattern_id,
    1.0,
    fml.filter,
    fml.filterDep,
    manager,
    fml.repairConfig
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
    .map { bind(rctNode.assignment, variable, it) }
    .map { subFormula.createRCTNode(it, rctNode.patternMap) }
    .toList()
