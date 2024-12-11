package com.CC.Constraints.Rules;

import com.CC.Constraints.Formulas.*;
import com.CC.Constraints.Runtime.RuntimeNode;
import com.CC.Util.Loggable;
import com.constraint.resolution.RepairDisableConfigItem;
import com.constraint.resolution.RepairType;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class RuleHandler implements Loggable {

    private final Map<String, Rule> ruleMap;

    public RuleHandler() {
        this.ruleMap = new HashMap<>();
    }

    public void buildRules(String filename) throws Exception {
        try(InputStream inputStream = Files.newInputStream(Paths.get(filename))) {
            SAXReader saxReader = new SAXReader();
            Document document = saxReader.read(inputStream);
            List<Element> eRuleList = document.getRootElement().elements();
            for(Element eRule: eRuleList){
                List<Element> eLabelList = eRule.elements();
                assert eLabelList.size() == 2 || eLabelList.size() == 3;
                //id
                assert eLabelList.get(0).getName().equals("id");
                Rule newRule = new Rule(eLabelList.get(0).getText());
                // formula
                assert eLabelList.get(1).getName().equals("formula");
                Element eFormula =  eLabelList.get(1).elements().get(0);
                newRule.setFormula(resolveFormula(eFormula, newRule.getVarPatternMap(), newRule.getPatToFormula(), newRule.getPatToRuntimeNode(), 0));
                setPatWithDepth(newRule.getFormula(), newRule.getPatToDepth(), newRule.getDepthToPat());
                ruleMap.put(newRule.getRule_id(), newRule);
            }
        }
    }

    @Nullable
    private List<RepairDisableConfigItem> getDisableConfigItems(Element element){
        List<RepairDisableConfigItem> retList = new ArrayList<>();
        if (element.attributeValue("non-updatable") != null) {
            var nonUpdatable = element.attributeValue("non-updatable").split(",");
            for(var item : nonUpdatable){
                retList.add(new RepairDisableConfigItem(RepairType.UPDATE, item));
            }
        }
        if (element.attributeValue("non-addable") != null) {
            var nonAddable = element.attributeValue("non-addable").split(",");
            for(var item : nonAddable){
                retList.add(new RepairDisableConfigItem(RepairType.ADDITION, item));
            }
        }
        if (element.attributeValue("non-removable") != null) {
            var nonRemovable = element.attributeValue("non-removable").split(",");
            for(var item : nonRemovable){
                retList.add(new RepairDisableConfigItem(RepairType.REMOVAL, item));
            }
        }
        if (element.attributeValue("immutable") != null) {
            var immutable = element.attributeValue("immutable").split(",");
            for(var item : immutable){
                retList.add(new RepairDisableConfigItem(RepairType.UPDATE, item));
                retList.add(new RepairDisableConfigItem(RepairType.ADDITION, item));
                retList.add(new RepairDisableConfigItem(RepairType.REMOVAL, item));
            }
        }

        if (retList.isEmpty()){
            return null;
        }
        return retList;
    }

    private Formula resolveFormula(Element eFormula, Map<String, String> varPatternMap, Map<String, Formula> patToFormula,
                                   Map<String, Set<RuntimeNode>> patToRunTimeNode, int depth){
        Formula retFormula = null;
        switch (eFormula.getName()){
            case "forall":{
                FForall tmpForall = new FForall(eFormula.attributeValue("var"), eFormula.attributeValue("in"));
                // forall has only one kid
                tmpForall.setSubformula(resolveFormula(eFormula.elements().get(0), varPatternMap, patToFormula, patToRunTimeNode, depth + 1));
                varPatternMap.put(eFormula.attributeValue("var"), eFormula.attributeValue("in"));
                patToFormula.put(eFormula.attributeValue("in"), tmpForall);
                patToRunTimeNode.put(eFormula.attributeValue("in"), new HashSet<>());
                // Set filter if exists
                tmpForall.setFilter(eFormula.attributeValue("filter"));
                tmpForall.setFilterDep(eFormula.attributeValue("filterDep"));
                // Add immutable pattern if exists
                tmpForall.setDisableConfigItems(getDisableConfigItems(eFormula));
                retFormula = tmpForall;
                break;
            }
            case "exists":{
                FExists tmpExists = new FExists(eFormula.attributeValue("var"), eFormula.attributeValue("in"));
                // exists has only one kid
                tmpExists.setSubformula(resolveFormula(eFormula.elements().get(0), varPatternMap, patToFormula, patToRunTimeNode, depth + 1));
                varPatternMap.put(eFormula.attributeValue("var"), eFormula.attributeValue("in"));
                patToFormula.put(eFormula.attributeValue("in"), tmpExists);
                patToRunTimeNode.put(eFormula.attributeValue("in"), new HashSet<>());
                tmpExists.setFilter(eFormula.attributeValue("filter"));
                tmpExists.setFilterDep(eFormula.attributeValue("filterDep"));
                // Add immutable pattern if exists
                tmpExists.setDisableConfigItems(getDisableConfigItems(eFormula));
                retFormula = tmpExists;
                break;
            }
            case "and":{
                FAnd tmpAnd = new FAnd();
                // and has two kids
                tmpAnd.replaceSubformula(0, resolveFormula(eFormula.elements().get(0), varPatternMap, patToFormula, patToRunTimeNode, depth + 1));
                tmpAnd.replaceSubformula(1, resolveFormula(eFormula.elements().get(1), varPatternMap, patToFormula, patToRunTimeNode, depth + 1));
                // Add immutable pattern if exists
                tmpAnd.setDisableConfigItems(getDisableConfigItems(eFormula));
                retFormula = tmpAnd;
                break;
            }
            case "or" :{
                FOr tmpOr = new FOr();
                // or has two kids
                tmpOr.replaceSubformula(0, resolveFormula(eFormula.elements().get(0), varPatternMap, patToFormula, patToRunTimeNode, depth + 1));
                tmpOr.replaceSubformula(1, resolveFormula(eFormula.elements().get(1), varPatternMap, patToFormula, patToRunTimeNode, depth + 1));
                // Add immutable pattern if exists
                tmpOr.setDisableConfigItems(getDisableConfigItems(eFormula));
                retFormula = tmpOr;
                break;
            }
            case "implies" :{
                FImplies tmpImplies = new FImplies();
                // implies has two kids
                tmpImplies.replaceSubformula(0, resolveFormula(eFormula.elements().get(0), varPatternMap, patToFormula, patToRunTimeNode, depth + 1));
                tmpImplies.replaceSubformula(1, resolveFormula(eFormula.elements().get(1), varPatternMap, patToFormula, patToRunTimeNode, depth + 1));
                // Add immutable pattern if exists
                tmpImplies.setDisableConfigItems(getDisableConfigItems(eFormula));
                retFormula = tmpImplies;
                break;
            }
            case "not" :{
                FNot tmpNot = new FNot();
                // not has only one kid
                tmpNot.setSubformula(resolveFormula(eFormula.elements().get(0), varPatternMap, patToFormula, patToRunTimeNode, depth + 1));
                // Add immutable pattern if exists
                tmpNot.setDisableConfigItems(getDisableConfigItems(eFormula));
                retFormula = tmpNot;
                break;
            }
            case "bfunc" :{
                FBfunc tmpBfunc = new FBfunc(eFormula.attributeValue("name"));
                // bfunc has several params
                List<Element> paramElementList = eFormula.elements();
                for(Element paramElement : paramElementList){
                    // pos and var
                    var pos = paramElement.attributeValue("pos");
                    // if pos is a number, add a prefix "v" to it
                    if(pos.matches("\\d+")){
                        pos = "v" + pos;
                    }
                    tmpBfunc.addParam(pos, paramElement.attributeValue("var"));
                }
                // Add immutable pattern if exists
                tmpBfunc.setDisableConfigItems(getDisableConfigItems(eFormula));
                retFormula = tmpBfunc;
                break;
            }
            default:
                assert false;
        }

        return retFormula;
    }

    private int setPatWithDepth(Formula formula, Map<String,Integer> patToDepth, Map<Integer, String> depthToPat){
        int maxDepth;
        switch (formula.getFormula_type()){
            case FORALL:
                maxDepth = setPatWithDepth(((FForall)formula).getSubformula(), patToDepth, depthToPat);
                patToDepth.put(((FForall)formula).getPattern_id(), maxDepth);
                depthToPat.put(maxDepth, ((FForall)formula).getPattern_id());
                return maxDepth + 1;
            case EXISTS:
                maxDepth = setPatWithDepth(((FExists)formula).getSubformula(), patToDepth, depthToPat);
                patToDepth.put(((FExists)formula).getPattern_id(), maxDepth);
                depthToPat.put(maxDepth, ((FExists)formula).getPattern_id());
                return maxDepth + 1;
            case AND:
                maxDepth = setPatWithDepth(((FAnd)formula).getSubformulas()[0], patToDepth, depthToPat);
                maxDepth = Math.max(maxDepth, setPatWithDepth(((FAnd)formula).getSubformulas()[1], patToDepth, depthToPat));
                return maxDepth + 1;
            case OR:
                maxDepth = setPatWithDepth(((FOr)formula).getSubformulas()[0], patToDepth, depthToPat);
                maxDepth = Math.max(maxDepth, setPatWithDepth(((FOr)formula).getSubformulas()[1], patToDepth, depthToPat));
                return maxDepth + 1;
            case IMPLIES:
                maxDepth = setPatWithDepth(((FImplies)formula).getSubformulas()[0], patToDepth, depthToPat);
                maxDepth = Math.max(maxDepth, setPatWithDepth(((FImplies)formula).getSubformulas()[1], patToDepth, depthToPat));
                return maxDepth + 1;
            case NOT:
                maxDepth = setPatWithDepth(((FNot)formula).getSubformula(), patToDepth, depthToPat);
                return maxDepth + 1;
            case BFUNC:
                return 1;
            default:
                return -1;
        }
    }


    public Map<String, Rule> getRuleMap() {
        return ruleMap;
    }

}
