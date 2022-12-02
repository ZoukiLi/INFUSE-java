<h1 align="center">
  INFUSE
</h1>

<p align="center">A <b>general constraint checking enginel</b> for data consistency.</p>

## :book: Tutorial

First, download **INFUSE_v2.1.jar** into your work directory.

Next, write your own rules and patterns in **rules.xml** and **patterns.xml** respectively according to their [templates](#templates).



## :page_facing_up: <span id="templates">Templates</span>

### Rule template

Rules are writtern in **first-order logic** style language which contains **seven** formula types. 

For example, a physical law  `no one can be in two rooms at the same time` can be writtern as:

```XML
<?xml version="1.0"?>

<rules>

    <rule>
        <id>rule_02</id> <!-- unique id -->
        <formula>
            <forall var="v1" in="pat_room1">
                  <not>
                    <exists var="v2" in="pat_room2">
                      <bfunction name="samePerson">
                          <param pos="1" var="v1"/>
                          <param pos="2" var="v2"/>
                      </bfunction>
                    </exists>
                  </not>
            </forall>
        </formula>
    </rule>

</rules>
```
