# Move to Z3

## Change the Bfunc structure

define the Bfunc in json format

```json
{
    // Function signature
    "name": "two-layer",
    "params": ["v1", "v2"],    // Input contexts to compare

    // Function specification
    "body": {
        "kind": "binary",
        "operator": "<",
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
    }
}
```

structured bfunc

```json
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
```

## Clean the code and Refactor

for now the formulas in kotlin using the java interface in a very verbose way.

and the ContextManager is not easy to use.

## Add the new Bfunc to the Z3 model



## Change the repair action

