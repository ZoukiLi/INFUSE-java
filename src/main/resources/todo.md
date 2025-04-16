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

我希望将原来的约束修复改为使用Z3的模型来实现。

1. 在原来的约束表示中，加入更复杂的bfunc，这需要不止步于EqualFormula，而是统一的BfuncFormula。
2. 在原来求解RepairCase的逻辑中，使用Z3的模型来求解。目前暂时使用的方法是，输出Z3的python代码，然后手动运行。
3. 写一个测试，来验证生成的python代码的合理性。

测试的例子参考resources/FunctionTest下的例子，但是需要修改，因为需要测试更复杂的bfunc。另外需要额外的bfunc.json文件，来描述bfunc的结构。

```xml
<rules>
    <rule>
        <id>rule1</id>
        <formula>
            <forall var="a" in="A">
                <forall var="b" in="B">
                        <bfunc name="less_than_x_y">
                            <!--a.x < b.y-->
                            <param pos="var1" var="a"/>
                            <param pos="var2" var="b"/>
                        </bfunc>
                </forall>
            </forall>
        </formula>
    </rule>
</rules>
```

```json
{
    "name": "less_than_x_y",
    "params": ["var1", "var2"],
    "body": {
        "kind": "binary",
        "operator": "<",
        "args": [
            {
                "kind": "accessor",
                "context": "var1",
                "attribute": "x",
                "valueType": "int"
            },
            {
                "kind": "accessor",
                "context": "var2",
                "attribute": "y",
                "valueType": "int"
            }
        ]   
    }
}
```

## Change the repair action

现在要修改repair action，使得它能够使用Z3的模型来求解。

1. 在原来的RepairAction中，除了添加和删除的action，还需要能够修改的action，目前只有Equal和DifferentiationRepairAction。
2. 现在新增一个BfuncRepairAction，来处理bfunc的约束。它有一个字符串字段，来描述bfunc的约束。目前用python代码来描述z3能够求解的约束。
3. 在repair case中加入整合所有生成的python代码，并能够输出。
4. 在test中加入测试保存所有生成的python代码，并能够输出。之后手动运行python代码，验证结果。


