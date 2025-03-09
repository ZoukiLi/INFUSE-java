package com.constraint.resolution.bfunc

import kotlinx.serialization.json.*

// Data classes to represent the structure
sealed interface Expression {
    val kind: String
    val valueType: String?
}

data class BinaryExpression(
    override val kind: String = "binary",
    val operator: String,
    val args: List<Expression>,
    override val valueType: String? = null
) : Expression

data class AccessorExpression(
    override val kind: String = "accessor",
    val context: String,
    val attribute: String,
    override val valueType: String
) : Expression

data class LiteralExpression(
    override val kind: String = "literal",
    val value: Any,
    override val valueType: String
) : Expression

data class BFunc(
    val name: String,
    val params: List<String>,
    val body: Expression
)

// Parser class
class BFuncParser {
    private fun parseExpression(json: JsonObject): Expression {
        return when (val kind = json["kind"]?.jsonPrimitive?.content) {
            "binary" -> BinaryExpression(
                operator = json["operator"]?.jsonPrimitive?.content ?: error("Missing operator"),
                args = json["args"]?.jsonArray?.map { parseExpression(it.jsonObject) } ?: emptyList(),
                valueType = json["valueType"]?.jsonPrimitive?.content
            )
            "accessor" -> AccessorExpression(
                context = json["context"]?.jsonPrimitive?.content ?: error("Missing context"),
                attribute = json["attribute"]?.jsonPrimitive?.content ?: error("Missing attribute"),
                valueType = json["valueType"]?.jsonPrimitive?.content ?: error("Missing valueType")
            )
            "literal" -> LiteralExpression(
                value = parseLiteralValue(json),
                valueType = json["valueType"]?.jsonPrimitive?.content ?: error("Missing valueType")
            )
            else -> throw IllegalArgumentException("Unknown expression kind: $kind")
        }
    }

    private fun parseLiteralValue(json: JsonObject): Any {
        return when (json["valueType"]?.jsonPrimitive?.content) {
            "int" -> json["value"]?.jsonPrimitive?.int ?: error("Missing value")
            "bool" -> json["value"]?.jsonPrimitive?.boolean ?: error("Missing value")
            "string" -> json["value"]?.jsonPrimitive?.content ?: error("Missing value")
            else -> throw IllegalArgumentException("Unsupported literal type: ${json["valueType"]?.jsonPrimitive?.content}")
        }
    }

    fun parse(jsonString: String): BFunc {
        val json = Json.parseToJsonElement(jsonString).jsonObject
        return BFunc(
            name = json["name"]?.jsonPrimitive?.content ?: error("Missing name"),
            params = json["params"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            body = parseExpression(json["body"]?.jsonObject ?: error("Missing body"))
        )
    }

    fun toPythonZ3(bfunc: BFunc): String {
        val sb = StringBuilder()
        sb.appendLine("from z3 import *")
        sb.appendLine()
        sb.appendLine("solver = Solver()")
        sb.appendLine("# Declare variables")
        sb.appendLine(generateVariableDeclarations(bfunc))
        sb.appendLine("# Add constraint")
        sb.appendLine("solver.add(${expressionToPythonZ3(bfunc.body)})")
        return sb.toString()
    }

    private fun generateVariableDeclarations(bfunc: BFunc): String {
        val variables = collectAccessors(bfunc.body)
        return variables.joinToString("\n") { (context, attr) ->
            "${context}_${attr} = Int('${context}_${attr}')"
        }
    }

    private fun collectAccessors(expr: Expression): Set<Pair<String, String>> {
        return when (expr) {
            is AccessorExpression -> setOf(expr.context to expr.attribute)
            is BinaryExpression -> expr.args.flatMap { collectAccessors(it) }.toSet()
            else -> emptySet()
        }
    }

    private fun expressionToPythonZ3(expr: Expression): String {
        return when (expr) {
            is BinaryExpression -> {
                val op = when (expr.operator) {
                    "+" -> "+"
                    "-" -> "-"
                    "*" -> "*"
                    "<" -> "<"
                    ">" -> ">"
                    "==" -> "=="
                    else -> throw IllegalArgumentException("Unsupported operator: ${expr.operator}")
                }
                "(${expr.args.joinToString(" $op ") { expressionToPythonZ3(it) }})"
            }
            is AccessorExpression -> "${expr.context}_${expr.attribute}"
            is LiteralExpression -> expr.value.toString()
        }
    }
}
