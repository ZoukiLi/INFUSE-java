import java.util.Map;

public class bfuncs {
    public boolean bfunc(String funcName, Map<String, Map<String, String>> vcMap) throws Exception {
        if("notStill".equals(funcName)){
            return notStill(vcMap);
        }
        else if("earlier_than".equals(funcName)){
            return earlier_than(vcMap);
        }
        else if("prev_location".equals(funcName)){
            return prev_location(vcMap);
        }
        else if("sameLoc".equals(funcName)){
            return sameLoc(vcMap);
        }
        else if("sameTime".equals(funcName)){
            return sameTime(vcMap);
        }
        else{
            System.out.println(funcName + "wrong");
            throw new Exception("Illegal bfuncName");
        }
    }

    private boolean notStill(Map<String, Map<String, String>> vcMap){
        String state = vcMap.get("v1").get("state");
        return !state.equals("0");
    }
    private boolean earlier_than(Map<String, Map<String, String>> vcMap){
//        if(vcMap.get("v1").get("timestamp")==null || vcMap.get("v2").get("timestamp")==null)    return false;
        long time1 = Long.parseLong(vcMap.get("v1").get("timestamp"));
        long time2 = Long.parseLong(vcMap.get("v2").get("timestamp"));
        return time1>time2;
    }
    private boolean prev_location(Map<String, Map<String, String>> vcMap){
        String prev_loc1 = vcMap.get("v1").get("prev_loc");
        String cur_loc2 = vcMap.get("v3").get("cur_loc");
        String cur_loc1 = vcMap.get("v1").get("cur_loc");
        String next_loc2 = vcMap.get("v3").get("next_loc");
        boolean res1 = prev_loc1.equals(cur_loc2);
        boolean res2 = cur_loc1.equals(next_loc2);
        return res1&res2;
    }
    private boolean sameLoc(Map<String, Map<String, String>> vcMap){
        String cur_loc1 = vcMap.get("v1").get("cur_loc");
        String cur_loc2 = vcMap.get("v2").get("cur_loc");
//        if(cur_loc1 == null || cur_loc2 == null)    return true;
        return cur_loc1.equals(cur_loc2);
    }
    private boolean sameTime(Map<String, Map<String, String>> vcMap){
//        if(vcMap.get("v1").get("timestamp")==null || vcMap.get("v2").get("timestamp")==null)    return false;
        long time1 = Long.parseLong(vcMap.get("v1").get("timestamp"));
        long time2 = Long.parseLong(vcMap.get("v2").get("timestamp"));
        return time1 > time2 && time1 - time2 < 100;
    }
}