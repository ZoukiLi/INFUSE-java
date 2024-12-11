package com.constraint.resolution

import com.CC.Contexts.ContextChange
import java.util.concurrent.atomic.AtomicInteger
typealias ContextAttribute = Pair<String, Boolean>
typealias AttributeMap = Map<String, ContextAttribute>

data class Context(
    val id: Int,
    val attributes: AttributeMap,
    var ccContext: com.CC.Contexts.Context? = null
) {
    override fun toString() = attributes["name"]?.first ?: "unknown($id)"
    fun updateAttribute(attribute: String, value: String?): Context {
        val newValue = value?.let { it to true } ?: (String() to false)
        val newAttrs = attributes + (attribute to newValue)
        val newCC = com.CC.Contexts.Context()
        newCC.ctx_id = id.toString()
        attributes.forEach { (k, v) -> newCC.ctx_fields[k] = v.first }
        newCC.ctx_fields[attribute] = value
        return copy(attributes = newAttrs, ccContext = newCC)
    }
}

typealias Variable = String
typealias Assignment = Map<Variable, Context>

fun bind(assignment: Assignment, variable: Variable, context: Context) = assignment + (variable to context)

typealias Pattern = Set<Context>
typealias MutablePattern = MutableSet<Context>
typealias PatternMap = Map<String, Pattern>
typealias MutablePatternMap = Map<String, MutablePattern>
typealias ContextPool = Map<Int, Context>

// ID counter for generating new context ids
object IdCounter {
    private var count: AtomicInteger = AtomicInteger(0)
    fun next() = count.incrementAndGet()
    fun id() = count.get()
}

// Context Management
class ContextManager {
    val pool = ArrayDeque<Context?>()
    val patternTable = ArrayDeque<List<String>>()
    val patternMap = mutableMapOf<String, MutablePattern>()
    var count = 0
    var prefixCount = 0

    fun patternsOf(context: Context) = patternTable[context.id - prefixCount]

    // Construct a new Context and link its CC Context
    fun constructContext(attributes: Map<String, String>): Context {
        val id = count++
        val context = Context(id, attributes.mapValues { (_, v) -> v to true })
        val ccContext = com.CC.Contexts.Context()
        ccContext.ctx_id = context.id.toString()
        attributes.forEach { (k, v) -> ccContext.ctx_fields[k] = v }
        context.ccContext = ccContext
        return context
    }

    // Add a Context into Patterns.
    // Return the context changes for this addition.
    fun addContextToPattern(context: Context, patternName: List<String>): List<ContextChange> {
        if (context.id < prefixCount) {
            // if context is in prefix, push front (prefixCount - context.id) to pool
            repeat(prefixCount - context.id) {
                pool.addFirst(null)
                patternTable.addFirst(emptyList())
            }
            prefixCount = context.id
        }
        if (context.id - prefixCount >= pool.size) {
            // if context is not in pool, push null to pool
            repeat(context.id - prefixCount - pool.size + 1) {
                pool.add(null)
                patternTable.add(emptyList())
            }
        }
        pool[context.id - prefixCount] = context
        patternTable[context.id - prefixCount] = patternName
        // if pattern name is new
        patternName.forEach {
            patternMap.putIfAbsent(it, mutableSetOf())
            patternMap[it]?.add(context)
        }
        return patternName.map { pattern ->
            ContextChange(ContextChange.Change_Type.ADDITION, pattern, context.ccContext!!)
        }
    }

    // Delete a Context from a Pattern, and clean the pool.
    // Return the context changes for this deletion.
    fun deleteContextFromPattern(context: Context, patternName: String): List<ContextChange> {
        // if context is not in pool, return empty list
        if (context.id - prefixCount >= pool.size || context.id - prefixCount < 0) {
            return emptyList()
        }
        patternTable[context.id - prefixCount] = patternsOf(context).filter { it != patternName }
        patternMap[patternName]?.removeIf { it.id == context.id }
        val ccContext = pool[context.id - prefixCount]?.ccContext!!
        val change = listOf(ContextChange(ContextChange.Change_Type.DELETION, patternName, ccContext))
        // clean pool from first by pattern
        while (pool.isNotEmpty() && patternTable.isNotEmpty() && patternTable.first().isEmpty()) {
            pool.removeFirst()
            patternTable.removeFirst()
            prefixCount++
        }
        return change
    }

    // Update an attribute of a Context.
    // Return the context changes for this update.
    // For now, using deletion and addition for Context Change.
    fun updateContextAttribute(context: Context, attribute: String, value: String?): List<ContextChange> {
        // if context is not in pool, return empty list
        if (context.id - prefixCount >= pool.size || context.id - prefixCount < 0) {
            return emptyList()
        }
        // pool changes
        val curContext = pool[context.id - prefixCount]!!
        val newContext = curContext.updateAttribute(attribute, value)
        pool[context.id - prefixCount] = newContext
        patternsOf(context).forEach { patternName ->
            patternMap[patternName]?.removeIf { it.id == context.id }
            patternMap[patternName]?.add(newContext)
        }
        // generate context changes
        return patternsOf(context).flatMap { patternName ->
            listOf(
                ContextChange(ContextChange.Change_Type.DELETION, patternName, curContext.ccContext!!),
                ContextChange(ContextChange.Change_Type.ADDITION, patternName, newContext.ccContext!!)
            )
        }
    }

    // Apply a RepairCase.
    // Return the context changes for this application.
    fun applyRepairCase(repairCase: RepairCase?) = repairCase?.applyTo(this)


    // Find a Context based on its CC Context
    fun findContext(ccContext: com.CC.Contexts.Context) = pool[ccContext.ctx_id.toInt()]
}

fun makeContext(attributes: Map<String, String>) =
    Context(IdCounter.next(), attributes.mapValues { (_, v) -> v to true })

fun fromCCContext(ccContext: com.CC.Contexts.Context) =
    Context(ccContext.ctx_id.toInt(), ccContext.ctx_fields.mapValues { (_, v) -> v to true }, ccContext)

fun toCCContext(context: Context): com.CC.Contexts.Context {
    if (context.ccContext != null) {
        return context.ccContext!!
    }
    val ccContext = com.CC.Contexts.Context()
    ccContext.ctx_id = context.id.toString()
    context.attributes.forEach { (k, v) -> ccContext.ctx_fields[k] = v.first }
    context.ccContext = ccContext
    return ccContext
}

fun toContextChanges(patternMap: PatternMap): List<ContextChange> {
    val changes = mutableListOf<ContextChange>()
    patternMap.forEach { (name, pattern) ->
        pattern.forEach { context ->
            val change = ContextChange()
            change.change_type = ContextChange.Change_Type.ADDITION
            change.context = toCCContext(context)
            change.pattern_id = name
            changes.add(change)
        }
    }
    return changes
}

fun applyContextChange(mutablePatternMap: MutablePatternMap, change: ContextChange) {
    when (change.change_type) {
        ContextChange.Change_Type.ADDITION -> mutablePatternMap[change.pattern_id]?.add(fromCCContext(change.context))
        ContextChange.Change_Type.DELETION -> mutablePatternMap[change.pattern_id]?.removeIf(change.context.ctx_id::equals)
        ContextChange.Change_Type.UPDATE -> TODO()
    }
}

fun makeContextPool(contexts: List<Context>) = contexts.associateBy { it.id }

fun makeContextSetMap(pool: ContextPool, nameId: Map<String, Set<Int>>) =
    nameId.mapValues { (_, ids) -> ids.mapNotNull { pool[it] }.toSet() }
