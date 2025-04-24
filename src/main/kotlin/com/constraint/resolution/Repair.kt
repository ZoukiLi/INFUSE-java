package com.constraint.resolution

import com.CC.Contexts.ContextChange
import io.github.oshai.kotlinlogging.KotlinLogging
import com.CC.Contexts.Context as CCContext

private val logger = KotlinLogging.logger {}

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

data class RepairConfig(
    val items: List<RepairDisableConfigItem>,
    val maxAddition: Int,
    val maxUpdate: Int,
    val maxRemoval: Int,
    val prefer: String?,
    val maxCaseSize: Int,
    val maxSuiteSize: Int,
)

const val PREFER_BRANCH = "branch"
const val PREFER_REVERT = "revert"

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
    fun affectContexts(): Set<Context> = setOf()
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

    override fun affectContexts(): Set<Context> = setOf(context)
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

    override fun affectContexts(): Set<Context> = setOf(context)
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

    override fun affectContexts(): Set<Context> = setOf(context1, context2)
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

    override fun affectContexts(): Set<Context> = setOf(context1)
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

    override fun affectContexts(): Set<Context> = setOf(context1, context2)
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

    override fun affectContexts(): Set<Context> = setOf(context1)
}


enum class ValueType {
    INT,
    BOOL,
    STRING,
    LONG
}

fun ValueType.toZ3Type() = when (this) {
    ValueType.INT -> "Int"
    ValueType.BOOL -> "Bool"
    ValueType.STRING -> "String"
    ValueType.LONG -> "Int"
}

fun String.toValueType(): ValueType {
    return when (this) {
        "int" -> ValueType.INT
        "bool" -> ValueType.BOOL
        "string" -> ValueType.STRING
        "long" -> ValueType.LONG
        else -> throw IllegalArgumentException("Unknown value type: $this")
    }
}

/**
 * 修复BFunc的参数
 * 只需要保存需要修改的上下文名称和属性名称
 * 具体的修复操作等待全部的修复案例生成后再执行
 */
data class BfuncRepairAction(
    val context: Context,
    val attribute: String,
    val type: ValueType
) : RepairAction {
    override fun repairType() = RepairType.UPDATE
    override fun execute(patternMap: PatternMap): PatternMap = patternMap
    override fun display(): String = "<BFunc, $context, $attribute, $type>"
    override fun reverse(manager: ContextManager) = listOf<ContextChange>()
    override fun applyTo(manager: ContextManager) = listOf<ContextChange>()
    override fun affectedBy(userConfig: RepairDisableConfigItem, manager: ContextManager) =
        userConfig.repairType == repairType() && manager.patternsOf(context).contains(userConfig.patternName)

    override fun inPattern(patternName: String, manager: ContextManager) = false
    override fun equal(other: RepairAction): Boolean = false
    override fun conflict(other: RepairAction): Boolean = false
    override fun varEnv(): Map<Variable, CCContext> {
        return context.ccContext?.let { mapOf("var" to it) } ?: emptyMap()
    }

    var value: String = ""

    override fun affectContexts(): Set<Context> = setOf(context)
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

    fun conflict(): Boolean {
        return actions.any { action1 ->
            actions.any { action2 ->
                !action1.equal(action2) && action1.conflict(action2)
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

    fun earliestId() = actions.minOf { it.affectContexts().minOf { context -> context.id } }
}

/**
 * 修复集合类，包含多个修复案例
 */
data class RepairSuite(
    val cases: Set<RepairCase> = setOf()
) {
    constructor(action: RepairAction, weight: Double) : this(setOf(RepairCase(action, weight)))

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

fun Sequence<RepairCase>.filterByConfig(
    userConfig: RepairConfig?,
    manager: ContextManager?
): Sequence<RepairCase> =
    when {
        userConfig == null || manager == null -> this
        else -> filter { repairCase ->
            repairCase.actions.none { action ->
                userConfig.items.any { pattern -> action.affectedBy(pattern, manager) }
            } && repairCase.actions.size <= userConfig.maxCaseSize
                    && repairCase.actions.count { it.repairType() == RepairType.ADDITION } <= userConfig.maxAddition
                    && repairCase.actions.count { it.repairType() == RepairType.REMOVAL } <= userConfig.maxRemoval
                    && repairCase.actions.count { it.repairType() == RepairType.UPDATE } <= userConfig.maxUpdate
        }.take(userConfig.maxSuiteSize)
    }

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

fun genZ3AttrConditions(case: RepairCase, usedAttrs: Set<Pair<Attribute, ValueType>>): List<String> {
    val variableAttrs = case.actions.filterIsInstance<BfuncRepairAction>().map { it.context to it.attribute }.toSet()
    val fixedAttrs = usedAttrs.filter { (attr) -> attr !in variableAttrs }
    return fixedAttrs.map { (attr, cls) ->
        val (context, attrName) = attr
        val value = context.attributes[attrName]?.first
        val identifier = "${cls.toZ3Type()}(\"ctx_${context.id}_$attrName\")"
        "$identifier == $value"
    }
}

fun genZ3PyCode(formula: IFormula, case: RepairCase, patternMap: PatternMap): String {
    val usedAttrs = formula.getUsedAttributes(mapOf(), patternMap)
    val attrConditions = genZ3AttrConditions(case, usedAttrs)
    val newPat = case.execute(patternMap)
    val fmlCondition = formula.Z3CondTrue(mapOf(), newPat)
    /*
    from z3 import *
    solver = Solver()
    solver.add(And(
        # attr1 == value1
        # attr2 == value2
        # ...
        # attrN == valueN
        # formula
    ))
    rst = solver.check()
    if (sat == rst):
        print(solver.model())
    else:
        print(rst)
     */
    return """
from z3 import *
solver = Solver()
solver.add(And(
    ${attrConditions.joinToString(",\n    ")},
    $fmlCondition
))
rst = solver.check()
if sat == rst:
    print(solver.model())
else:
    print(rst)
"""
}

/**
 * 运行Python代码
 * @param pyCode Python代码字符串
 * @param pythonPath Python解释器路径，默认为"python"
 * @return 执行结果
 */
fun runPyCode(pyCode: String, pythonPath: String = "python"): String {
    // 创建临时文件
    val tempFile = kotlin.io.path.createTempFile(
        prefix = "z3_script_",
        suffix = ".py"
    ).toFile()

    try {
        // 写入Python代码到临时文件
        tempFile.writeText(pyCode)

        val processBuilder = ProcessBuilder(pythonPath, tempFile.absolutePath)

        // 配置环境变量
        val env = processBuilder.environment()
        logger.info { "env: $env" }
        env.remove("PYTHONHOME")
        env["PYTHONUNBUFFERED"] = "1"

        // 配置错误输出合并到标准输出
        processBuilder.redirectErrorStream(true)

        return try {
            val process = processBuilder.start()

            // 读取输出
            val output = process.inputStream.bufferedReader().use { it.readText() }

            // 等待进程完成
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                logger.error { "Python process exited with code $exitCode\nOutput: $output" }
            }

            output
        } catch (e: Exception) {
            logger.error { "Failed to run Python code: ${e.message}" }
            ""
        }

    } finally {
        // 清理临时文件
        try {
            tempFile.delete()
        } catch (e: Exception) {
            logger.warn { "Failed to delete temporary file: ${tempFile.absolutePath}" }
        }
    }
}

/**
 * 使用指定的Python环境运行代码
 * @param pyCode Python代码字符串
 * @param envName conda环境名称或virtualenv路径
 * @return 执行结果
 */
fun runPyCodeWithEnv(pyCode: String, envName: String): String {
    // 对于conda环境
    if (envName.startsWith("conda:")) {
        val condaEnv = envName.removePrefix("conda:")
        return runPyCode(
            pyCode,
            "conda run -n $condaEnv python"
        )
    }

    // 对于virtualenv环境
    val pythonPath = when {
        // Windows
        System.getProperty("os.name").lowercase().contains("windows") ->
            "$envName/Scripts/python.exe"
        // Unix-like
        else ->
            "$envName/bin/python"
    }

    return runPyCode(pyCode, pythonPath)
}
