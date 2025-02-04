package com.constraint.resolution

interface RepairAction {
    fun execute(patternMap: PatternMap): PatternMap
    fun display(): String
}

data class AdditionRepairAction(val context: Context, val patternName: String) : RepairAction {
    override fun execute(patternMap: PatternMap): PatternMap {
        return patternMap.mapValues { (name, pat) -> if (name == patternName) pat + context else pat }
    }
    override fun display(): String = "+ $context -> $patternName"
}
data class RemovalRepairAction(val context: Context, val patternName: String) : RepairAction {
    override fun execute(patternMap: PatternMap): PatternMap {
        return patternMap.mapValues { (name, pat) -> if (name == patternName) pat - context else pat }
    }
    override fun display(): String = "- $context -> $patternName"
}
data class EqualizationRepairAction(val context1: Context, val attributeName1: String, val context2: Context, val attributeName2: String) : RepairAction {
    override fun execute(patternMap: PatternMap): PatternMap = patternMap
    override fun display(): String = "= ($context1.$attributeName1, $context2.$attributeName2)"
}
data class DifferentiationRepairAction(val context1: Context, val attributeName1: String, val context2: Context, val attributeName2: String) : RepairAction {
    override fun execute(patternMap: PatternMap): PatternMap = patternMap
    override fun display(): String = "! ($context1.$attributeName1, $context2.$attributeName2)"
}

typealias Attribute = Pair<Context, String>

data class DisjointSet<T>(val parent: MutableMap<T, T> = mutableMapOf(), val rank: MutableMap<T, Int> = mutableMapOf()) {
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

    fun display(): String = actions.joinToString("\n") { it.display() } + "Weight: $weight\n"

    fun and(other: RepairCase) = RepairCase(actions + other.actions, weight + other.weight)

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
}

fun RepairCase.andAll(vararg cases: RepairCase) = cases.reduce { acc, case -> acc.and(case) }

data class RepairSuite(val cases: Set<RepairCase>) {
    fun display(): String = cases.joinToString("\n") { "Case:\n" + it.display() + "\n" }
    fun or(other: RepairSuite) = RepairSuite(cases + other.cases)
    fun and(other: RepairSuite): RepairSuite {
        if (cases.intersect(other.cases).isEmpty()) {
            return RepairSuite(cases + other.cases)
        }
        return RepairSuite(cases.flatMap { case1 -> other.cases.map { case2 -> case1.and(case2) } }.toSet())
    }
}
fun RepairSuite.andAll(vararg suites: RepairSuite) = suites.reduce { acc, suite -> acc.and(suite) }
fun RepairSuite.orAll(vararg suites: RepairSuite) = suites.reduce { acc, suite -> acc.or(suite) }
