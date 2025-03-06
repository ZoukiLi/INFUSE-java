package com.constraint.resolution

import com.CC.Contexts.ContextChange
import com.CC.Contexts.Context as CCContext

enum class RepairType {
    ADDITION,
    UPDATE,
    REMOVAL,
}

// User Config for Disable Repair of a given type and pattern
data class RepairDisableConfigItem(
    val repairType: RepairType,
    val patternName: String
)

interface RepairAction {
    fun repairType(): RepairType
    fun execute(patternMap: PatternMap): PatternMap
    fun display(): String
    fun reverse(manager: ContextManager): List<ContextChange>
    fun applyTo(manager: ContextManager): List<ContextChange>
    fun affectedBy(userConfig: RepairDisableConfigItem, manager: ContextManager): Boolean
    fun inPattern(patternName: String, manager: ContextManager): Boolean
    fun equal(other: RepairAction): Boolean
    fun conflict(other: RepairAction): Boolean
    fun varEnv(): Map<Variable, CCContext>
}

data class AdditionRepairAction(val context: Context, val patternName: String) : RepairAction {
    override fun repairType() = RepairType.ADDITION
    override fun execute(patternMap: PatternMap): PatternMap =
        patternMap.mapValues { (name, pat) -> if (name == patternName) pat + context else pat }

    override fun display(): String = "<+, $patternName, $context>"

    override fun reverse(manager: ContextManager): List<ContextChange> =
        manager.deleteContextFromPattern(context, patternName)

    override fun applyTo(manager: ContextManager) = manager.addContextToPattern(context, listOf(patternName))

    override fun affectedBy(userConfig: RepairDisableConfigItem, manager: ContextManager) =
        userConfig.repairType == repairType() && userConfig.patternName == patternName

    override fun inPattern(patternName: String, manager: ContextManager) = this.patternName == patternName

    override fun equal(other: RepairAction): Boolean = when (other) {
        is AdditionRepairAction -> context == other.context && patternName == other.patternName
        else -> false
    }

    override fun conflict(other: RepairAction): Boolean = when (other) {
        is RemovalRepairAction -> context == other.context && patternName == other.patternName
        else -> false
    }

    override fun varEnv(): Map<Variable, CCContext> {
        return context.ccContext?.let { mapOf("var" to it) } ?: emptyMap()
    }
}

data class RemovalRepairAction(val context: Context, val patternName: String) : RepairAction {
    override fun repairType() = RepairType.REMOVAL
    override fun execute(patternMap: PatternMap): PatternMap =
        patternMap.mapValues { (name, pat) -> if (name == patternName) pat - context else pat }

    override fun display(): String = "<-, $patternName, $context>"

    override fun reverse(manager: ContextManager) = manager.addContextToPattern(context, listOf(patternName))

    override fun applyTo(manager: ContextManager) = manager.deleteContextFromPattern(context, patternName)

    override fun affectedBy(userConfig: RepairDisableConfigItem, manager: ContextManager) =
        userConfig.repairType == repairType() && userConfig.patternName == patternName

    override fun inPattern(patternName: String, manager: ContextManager) = this.patternName == patternName

    override fun equal(other: RepairAction): Boolean = when (other) {
        is RemovalRepairAction -> context == other.context && patternName == other.patternName
        else -> false
    }

    override fun conflict(other: RepairAction): Boolean = when (other) {
        is AdditionRepairAction -> context == other.context && patternName == other.patternName
        else -> false
    }

    override fun varEnv(): Map<Variable, CCContext> {
        return context.ccContext?.let { mapOf("var" to it) } ?: emptyMap()
    }
}

data class EqualizationRepairAction(
    val context1: Context,
    val attributeName1: String,
    val context2: Context,
    val attributeName2: String
) : RepairAction {
    override fun repairType() = RepairType.UPDATE
    override fun execute(patternMap: PatternMap): PatternMap = patternMap
    override fun display(): String = "<=, $context1.$attributeName1, $context2.$attributeName2>"
    override fun reverse(manager: ContextManager) =
        manager.updateContextAttribute(context1, attributeName1, context1.attributes[attributeName1]?.first)

    override fun applyTo(manager: ContextManager) =
        manager.updateContextAttribute(context1, attributeName1, context2.attributes[attributeName2]?.first)

    override fun affectedBy(userConfig: RepairDisableConfigItem, manager: ContextManager) =
        userConfig.repairType == repairType() && manager.patternsOf(context1).contains(userConfig.patternName)

    override fun inPattern(patternName: String, manager: ContextManager) =
        manager.patternsOf(context1).contains(patternName)

    override fun equal(other: RepairAction): Boolean = when (other) {
        is EqualizationRepairAction -> {
            (context1 == other.context1 && attributeName1 == other.attributeName1 &&
                    context2 == other.context2 && attributeName2 == other.attributeName2) ||
                    (context1 == other.context2 && attributeName1 == other.attributeName2 &&
                            context2 == other.context1 && attributeName2 == other.attributeName1)
        }

        else -> false
    }

    override fun conflict(other: RepairAction): Boolean = when (other) {
        is DifferentiationRepairAction -> {
            (context1 == other.context1 && attributeName1 == other.attributeName1 &&
                    context2 == other.context2 && attributeName2 == other.attributeName2) ||
                    (context1 == other.context2 && attributeName1 == other.attributeName2 &&
                            context2 == other.context1 && attributeName2 == other.attributeName1)
        }

        else -> false
    }

    override fun varEnv(): Map<Variable, CCContext> {
        val env = mutableMapOf<Variable, CCContext>()
        context1.ccContext?.let { env["var1"] = it }
        context2.ccContext?.let { env["var2"] = it }
        return env
    }
}

data class EqualizationConstRepairAction(
    val context1: Context,
    val attributeName1: String,
    val value: String
) : RepairAction {
    override fun repairType() = RepairType.UPDATE
    override fun execute(patternMap: PatternMap): PatternMap = patternMap
    override fun display(): String = "<=, $context1.$attributeName1, $value>"
    override fun reverse(manager: ContextManager) =
        manager.updateContextAttribute(context1, attributeName1, context1.attributes[attributeName1]?.first)

    override fun applyTo(manager: ContextManager) =
        manager.updateContextAttribute(context1, attributeName1, value)

    override fun affectedBy(userConfig: RepairDisableConfigItem, manager: ContextManager) =
        userConfig.repairType == repairType() && manager.patternsOf(context1).contains(userConfig.patternName)

    override fun inPattern(patternName: String, manager: ContextManager) =
        manager.patternsOf(context1).contains(patternName)

    override fun equal(other: RepairAction): Boolean = when (other) {
        is EqualizationConstRepairAction ->
            context1 == other.context1 && attributeName1 == other.attributeName1 && value == other.value

        else -> false
    }

    override fun conflict(other: RepairAction): Boolean = when (other) {
        is DifferentiationConstRepairAction ->
            context1 == other.context1 && attributeName1 == other.attributeName1 && value == other.value

        else -> false
    }

    override fun varEnv(): Map<Variable, CCContext> {
        return context1.ccContext?.let { mapOf("var1" to it) } ?: emptyMap()
    }
}

data class DifferentiationRepairAction(
    val context1: Context,
    val attributeName1: String,
    val context2: Context,
    val attributeName2: String
) : RepairAction {
    override fun repairType() = RepairType.UPDATE
    override fun execute(patternMap: PatternMap): PatternMap = patternMap
    override fun display(): String = "<!=, $context1.$attributeName1, $context2.$attributeName2>"
    override fun reverse(manager: ContextManager) =
        manager.updateContextAttribute(context1, attributeName1, context1.attributes[attributeName1]?.first)

    override fun applyTo(manager: ContextManager) =
        manager.updateContextAttribute(context1, attributeName1, null)

    override fun affectedBy(userConfig: RepairDisableConfigItem, manager: ContextManager) =
        userConfig.repairType == repairType() && manager.patternsOf(context1).contains(userConfig.patternName)

    override fun inPattern(patternName: String, manager: ContextManager) =
        manager.patternsOf(context1).contains(patternName)

    override fun equal(other: RepairAction): Boolean = when (other) {
        is DifferentiationRepairAction -> {
            (context1 == other.context1 && attributeName1 == other.attributeName1 &&
                    context2 == other.context2 && attributeName2 == other.attributeName2) ||
                    (context1 == other.context2 && attributeName1 == other.attributeName2 &&
                            context2 == other.context1 && attributeName2 == other.attributeName1)
        }

        else -> false
    }

    override fun conflict(other: RepairAction): Boolean = when (other) {
        is EqualizationRepairAction -> {
            (context1 == other.context1 && attributeName1 == other.attributeName1 &&
                    context2 == other.context2 && attributeName2 == other.attributeName2) ||
                    (context1 == other.context2 && attributeName1 == other.attributeName2 &&
                            context2 == other.context1 && attributeName2 == other.attributeName1)
        }

        else -> false
    }

    override fun varEnv(): Map<Variable, CCContext> {
        val env = mutableMapOf<Variable, CCContext>()
        context1.ccContext?.let { env["var1"] = it }
        context2.ccContext?.let { env["var2"] = it }
        return env
    }
}

data class DifferentiationConstRepairAction(
    val context1: Context,
    val attributeName1: String,
    val value: String
) : RepairAction {
    override fun repairType() = RepairType.UPDATE
    override fun execute(patternMap: PatternMap): PatternMap = patternMap
    override fun display(): String = "<!=, $context1.$attributeName1, $value>"
    override fun reverse(manager: ContextManager) =
        manager.updateContextAttribute(context1, attributeName1, context1.attributes[attributeName1]?.first)

    override fun applyTo(manager: ContextManager) =
        manager.updateContextAttribute(context1, attributeName1, null)

    override fun affectedBy(userConfig: RepairDisableConfigItem, manager: ContextManager) =
        userConfig.repairType == repairType() && manager.patternsOf(context1).contains(userConfig.patternName)

    override fun inPattern(patternName: String, manager: ContextManager) =
        manager.patternsOf(context1).contains(patternName)

    override fun equal(other: RepairAction): Boolean = when (other) {
        is DifferentiationConstRepairAction ->
            context1 == other.context1 && attributeName1 == other.attributeName1 && value == other.value

        else -> false
    }

    override fun conflict(other: RepairAction): Boolean = when (other) {
        is EqualizationConstRepairAction ->
            context1 == other.context1 && attributeName1 == other.attributeName1 && value == other.value

        else -> false
    }

    override fun varEnv(): Map<Variable, CCContext> {
        return context1.ccContext?.let { mapOf("var1" to it) } ?: emptyMap()
    }
}

typealias Attribute = Pair<Context, String>

data class DisjointSet<T>(
    val parent: MutableMap<T, T> = mutableMapOf(),
    val rank: MutableMap<T, Int> = mutableMapOf()
) {
    fun find(x: T): T {
        if (x !in parent.keys) {
            parent[x] = x
            rank[x] = 0
        }
        if (parent[x] != x) {
            parent[x] = find(parent[x]!!)
        }
        return parent[x]!!
    }

    fun union(x: T, y: T) {
        val xRoot = find(x)
        val yRoot = find(y)
        if (xRoot == yRoot) {
            return
        }
        if (rank[xRoot]!! < rank[yRoot]!!) {
            parent[xRoot] = yRoot
        } else if (rank[xRoot]!! > rank[yRoot]!!) {
            parent[yRoot] = xRoot
        } else {
            parent[yRoot] = xRoot
            rank[xRoot] = rank[xRoot]!! + 1
        }
    }
}

data class RepairCase(val actions: Set<RepairAction>, val weight: Double) {
    fun execute(patternMap: PatternMap): PatternMap {
        return actions.fold(patternMap) { acc, action -> action.execute(acc) }
    }

    fun display(): String =
        actions.sortedBy { it.repairType() }.joinToString("\n") { it.display() }// + "\nWeight: $weight"

    infix fun and(other: RepairCase) = RepairCase(actions union other.actions, weight + other.weight)

    fun getEqualizationSets(): DisjointSet<Attribute> {
        val equalizationSets = DisjointSet<Attribute>()
        actions.filterIsInstance<EqualizationRepairAction>()
            .map { (it.context1 to it.attributeName1) to (it.context2 to it.attributeName2) }
            .forEach { (x, y) -> equalizationSets.union(x, y) }
        return equalizationSets
    }

    fun isConflicting(disjointSet: DisjointSet<Attribute>): Boolean {
        return actions.filterIsInstance<DifferentiationRepairAction>()
            .map { (it.context1 to it.attributeName1) to (it.context2 to it.attributeName2) }
            .any { (x, y) -> disjointSet.find(x) == disjointSet.find(y) }
    }

    fun applyTo(manager: ContextManager) = actions.sortedBy { it.repairType() }.flatMap { it.applyTo(manager) }
    fun reverse(manager: ContextManager) =
        actions.sortedByDescending { it.repairType() }.flatMap { it.reverse(manager) }

    override fun hashCode(): Int {
        var hasher = 0
        actions.forEach { hasher = hasher xor it.hashCode() xor it.repairType().hashCode() }
        return hasher
    }

    override fun equals(other: Any?): Boolean {
        if (other !is RepairCase) {
            return false
        }
        return actions == other.actions && weight == other.weight
    }

    fun conflict(other: RepairCase): Boolean {
        return actions.any { action1 ->
            other.actions.any { action2 ->
                action1.conflict(action2)
            }
        }
    }

    fun countEquals(other: RepairCase): Int {
        return actions.count { action1 ->
            other.actions.any { action2 ->
                action1.equal(action2)
            }
        }
    }

    constructor(action: RepairAction, weight: Double) : this(setOf(action), weight)
}

data class RepairSuite(val cases: Set<RepairCase>) {
    fun display(): String =
        cases.mapIndexed { index, case -> "Case $index.\n${case.display()}" }.joinToString("\n----------\n")

    fun forEachIndexed(action: (Int, RepairCase) -> Unit) = cases.forEachIndexed(action)

    fun filterImmutable(userConfig: List<RepairDisableConfigItem>?, manager: ContextManager?): RepairSuite {
        if (userConfig == null || manager == null) {
            return this
        }
        return RepairSuite(
            cases
                .filterNot {
                    it.actions.any {
                        userConfig.any { pattern -> it.affectedBy(pattern, manager) }
                    }
                }
                .toSet())
    }

    infix fun or(other: RepairSuite) = RepairSuite(cases union other.cases)
    infix fun and(other: RepairSuite) = when (cases.isEmpty() to other.cases.isEmpty()) {
        false to false -> RepairSuite(cases.flatMap { case1 -> other.cases.map { case2 -> case1 and case2 } }.toSet())
        else -> RepairSuite(cases union other.cases)
    }

    fun firstCase(): RepairCase? = cases.minByOrNull { it.actions.size }

    constructor(action: RepairAction, weight: Double) : this(setOf(RepairCase(action, weight)))
    constructor() : this(emptySet())
}

fun chain(vararg cases: Sequence<RepairCase>): Sequence<RepairCase> = chain(cases.asSequence())

fun chain(caseSeqs: Sequence<Sequence<RepairCase>>) = caseSeqs.flatten()

fun cartesianProductCases(vararg cases: Sequence<RepairCase>): Sequence<Sequence<RepairCase>> = when {
    cases.isEmpty() -> emptySequence()
    else -> sequence {
        val tailProduct = cartesianProductCases(*cases.drop(1).toTypedArray())
        if (tailProduct.none()) {
            yieldAll(cases.first().map { sequenceOf(it) })
            return@sequence
        }
        if (cases.first().none()) {
            yieldAll(tailProduct)
            return@sequence
        }
        cases.first().forEach { head ->
            tailProduct.forEach { tail ->
                yield(sequenceOf(head) + tail)
            }
        }
    }
}

fun cartesianProductCases(cases: Sequence<Sequence<RepairCase>>): Sequence<Sequence<RepairCase>> =
    cartesianProductCases(*cases.toList().toTypedArray())

fun cartesianProduct(vararg cases: Sequence<RepairCase>): Sequence<RepairCase> = when {
    cases.isEmpty() -> emptySequence()
    else -> sequence {
        val tailProduct = cartesianProduct(*cases.drop(1).toTypedArray())
        if (tailProduct.none()) {
            yieldAll(cases.first())
            return@sequence
        }
        if (cases.first().none()) {
            yieldAll(tailProduct)
            return@sequence
        }
        cases.first().forEach { head ->
            tailProduct.forEach { tail ->
                yield(head and tail)
            }
        }
    }
}

fun cartesianProduct(caseSeqs: Sequence<Sequence<RepairCase>>) =
    cartesianProduct(*caseSeqs.toList().toTypedArray())

fun filterImmutable(
    userConfig: List<RepairDisableConfigItem>?,
    manager: ContextManager?,
    sequence: Sequence<RepairCase>
): Sequence<RepairCase> =
    when {
        userConfig == null || manager == null -> {
            sequence
        }

        else -> sequence.filter { repairCase ->
            repairCase.actions.none { action ->
                userConfig.any { pattern -> action.affectedBy(pattern, manager) }
            }
        }
    }

fun hasConflict(cases: Sequence<RepairCase>): Boolean {
    val caseList = cases.toList()
    return caseList.indices.any { i ->
        ((i + 1) until caseList.size).any { j ->
            caseList[i].conflict(caseList[j])
        }
    }
}

fun countTotalEquals(cases: Sequence<RepairCase>): Int {
    val caseList = cases.toList()
    return caseList.indices.sumOf { i ->
        ((i + 1) until caseList.size).sumOf { j ->
            caseList[i].countEquals(caseList[j])
        }
    }
}
