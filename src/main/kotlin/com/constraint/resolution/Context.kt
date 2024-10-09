package com.constraint.resolution

import java.util.concurrent.atomic.AtomicInteger

typealias ContextAttribute = Pair<String, Boolean>

data class Context(
    val id: Int,
    val attributes: Map<String, ContextAttribute>
) {
    override fun toString() = attributes["name"]?.first ?: "unknown($id)"
}

typealias Variable = String
typealias Assignment = Map<Variable, Context>

fun bind(assignment: Assignment, variable: Variable, context: Context) = assignment + (variable to context)

typealias Pattern = Set<Context>
typealias PatternMap = Map<String, Pattern>
typealias ContextPool = Map<Int, Context>

// ID counter for generating new context ids
object IdCounter {
    private var count: AtomicInteger = AtomicInteger(0)
    fun next() = count.incrementAndGet()
    fun id() = count.get()
}

fun makeContext(attributes: Map<String, String>) =
    Context(IdCounter.next(), attributes.mapValues { (_, v) -> v to true })

fun makeContextPool(contexts: List<Context>) = contexts.associateBy { it.id }

fun makeContextSetMap(pool: ContextPool, nameId: Map<String, Set<Int>>) =
    nameId.mapValues { (_, ids) -> ids.mapNotNull { pool[it] }.toSet() }
