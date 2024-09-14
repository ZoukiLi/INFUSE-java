package com.constraint.resolution

typealias ContextAttribute = Pair<String, Boolean>

data class Context(
    val id: Int,
    val attributes: Map<String, ContextAttribute>
)

typealias Variable = String
typealias Assignment = Map<Variable, Context>

fun bind(assignment: Assignment, variable: Variable, context: Context): Assignment {
    return assignment + (variable to context)
}

typealias Pattern = Set<Context>
typealias PatternMap = Map<String, Pattern>
typealias ContextPool = Map<Int, Context>

fun makeContext(id: Int, attributes: Map<String, String>): Context {
    return Context(id, attributes.mapValues { (_, v) -> v to true })
}

fun makeContextPool(contexts: List<Context>): ContextPool {
    return contexts.associateBy { it.id }
}

fun makeContextSetMap(pool: ContextPool, nameId: Map<String, Set<Int>>): PatternMap {
    return nameId.mapValues { (_, ids) -> ids.mapNotNull { pool[it] }.toSet() }
}
