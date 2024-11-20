package com.constraint.resolution

import com.CC.Contexts.ContextChange

interface RepairAction {
    fun execute(patternMap: PatternMap): PatternMap
    fun display(): String
}

data class AdditionRepairAction(val context: Context, val patternName: String) : RepairAction {
    override fun execute(patternMap: PatternMap): PatternMap {
        return patternMap.mapValues { (name, pat) -> if (name == patternName) pat + context else pat }
    }

    override fun display(): String = "<+, $patternName, $context>"
}

data class RemovalRepairAction(val context: Context, val patternName: String) : RepairAction {
    override fun execute(patternMap: PatternMap): PatternMap {
        return patternMap.mapValues { (name, pat) -> if (name == patternName) pat - context else pat }
    }

    override fun display(): String = "<-, $patternName, $context>"
}

data class EqualizationRepairAction(
    val context1: Context,
    val attributeName1: String,
    val context2: Context,
    val attributeName2: String
) : RepairAction {
    override fun execute(patternMap: PatternMap): PatternMap = patternMap
    override fun display(): String = "<=, $context1.$attributeName1, $context2.$attributeName2>"
}

data class EqualizationConstRepairAction(
    val context1: Context,
    val attributeName1: String,
    val value: String
) : RepairAction {
    override fun execute(patternMap: PatternMap): PatternMap = patternMap
    override fun display(): String = "<=, $context1.$attributeName1, $value>"
}

data class DifferentiationRepairAction(
    val context1: Context,
    val attributeName1: String,
    val context2: Context,
    val attributeName2: String
) : RepairAction {
    override fun execute(patternMap: PatternMap): PatternMap = patternMap
    override fun display(): String = "<!=, $context1.$attributeName1, $context2.$attributeName2>"
}

data class DifferentiationConstRepairAction(
    val context1: Context,
    val attributeName1: String,
    val value: String
) : RepairAction {
    override fun execute(patternMap: PatternMap): PatternMap = patternMap
    override fun display(): String = "<!=, $context1.$attributeName1, $value>"
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

    fun display(): String = actions.joinToString("\n") { it.display() } + "\nWeight: $weight"

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

    constructor(action: RepairAction, weight: Double) : this(setOf(action), weight)
}

data class RepairSuite(val cases: Set<RepairCase>) {
    fun display(): String =
        cases.mapIndexed { index, case -> "Case $index.\n${case.display()}" }.joinToString("\n----------\n")

    infix fun or(other: RepairSuite) = RepairSuite(cases union other.cases)
    infix fun and(other: RepairSuite): RepairSuite = when (cases.isEmpty() to other.cases.isEmpty()) {
        false to false -> RepairSuite(cases.flatMap { case1 -> other.cases.map { case2 -> case1 and case2 } }.toSet())
        else -> RepairSuite(cases union other.cases)
    }

    constructor(action: RepairAction, weight: Double) : this(setOf(RepairCase(action, weight)))
    constructor() : this(emptySet())
}

fun RepairSuite.firstCase(): RepairCase? =
    cases.filter { it.actions.all { it is AdditionRepairAction || it is RemovalRepairAction } }
        .minByOrNull { it.actions.size }

fun RepairCase.toChanges(): List<ContextChange> {
    return actions.filterIsInstance<AdditionRepairAction>().map { contextChangeAddition(it.context, it.patternName) } +
            actions.filterIsInstance<RemovalRepairAction>().map { contextChangeRemoval(it.context, it.patternName) }
}

fun contextChangeAddition(context: Context, pattern: String): ContextChange {
    val change = ContextChange()
    change.change_type = ContextChange.Change_Type.ADDITION
    change.context = toCCContext(context)
    change.pattern_id = pattern
    return change
}

fun contextChangeRemoval(context: Context, pattern: String): ContextChange {
    val change = ContextChange()
    change.change_type = ContextChange.Change_Type.DELETION
    change.context = toCCContext(context)
    change.pattern_id = pattern
    return change
}

fun MutablePatternMap.applyRepairSuite(repairSuite: RepairSuite) {
    repairSuite.firstCase()?.actions?.forEach {
        when (it) {
            is AdditionRepairAction -> this[it.patternName]?.add(it.context)
            is RemovalRepairAction -> this[it.patternName]?.remove(it.context)
        }
    }
}
