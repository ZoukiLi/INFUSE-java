package com.constraint.resolution.bfunc

import kotlin.test.Test

class BFuncParserTest {
    @Test
    fun testGeneratePythonZ3() {
        val jsonString = """
    {
        "name": "two-layer",
        "params": ["v1", "v2"],
        "body": {
            "kind": "binary",
            "operator": "<",
            "args": [
                {
                    "kind": "binary",
                    "operator": "+",
                    "args": [
                        {
                            "kind": "accessor",
                            "context": "v1",
                            "attribute": "attr1",
                            "valueType": "int"
                        },
                        {
                            "kind": "accessor",
                            "context": "v2",
                            "attribute": "attr2",
                            "valueType": "int"
                        }
                    ]
                },
                {
                    "kind": "literal",
                    "value": 10,
                    "valueType": "int"
                }
            ]
        }
    }
    """.trimIndent()
        val parser = BFuncParser()
        val bfunc = parser.parse(jsonString)
        val pythonCode = parser.toPythonZ3(bfunc)
        println(pythonCode)
    }
}
