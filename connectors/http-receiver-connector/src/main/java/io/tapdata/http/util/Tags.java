package io.tapdata.http.util;

import java.util.Optional;

/**
 * @author GavinXiao
 * @description Tags create by Gavin
 * @create 2023/5/17 18:56
 **/
public class Tags {
    public static final String OP_TYPE_KEY = "opType";
    public static final String EVENT_AFTER_KAY = "after";
    public static final String EVENT_BEFORE_KAY = "before";
    public static final String EVENT_REFERENCE_TIME = "time";

    public static final String OP_INSERT = "i";
    public static final String OP_UPDATE = "u";
    public static final String OP_DELETE = "d";

    public static final String SCRIPT_BEFORE = "function handleEvent(event) {";
    public static final String SCRIPT_AFTER = "}";

    public static String script(String body){
        return SCRIPT_BEFORE + Optional.ofNullable(body).orElse("") + SCRIPT_AFTER;
    }

    public static boolean isOp(Object opType){
        if (opType instanceof String){
            String type = (String) opType;
            return OP_INSERT.equals(type) || OP_UPDATE.equals(type) || OP_DELETE.equals(type);
        } else if (opType instanceof Character){
            char type = (char) opType;
            return OP_INSERT.charAt(0) == type || OP_UPDATE.charAt(0) == type || OP_DELETE.charAt(0) == type;
        }else {
            return false;
        }
    }

    public static String getOp(Object opType){
        if (opType instanceof String){
            String type = (String) opType;
            return OP_INSERT.equals(type) || OP_UPDATE.equals(type) || OP_DELETE.equals(type) ? type : Tags.OP_INSERT;
        } else if (opType instanceof Character){
            char type = (char) opType;
            return OP_INSERT.charAt(0) == type || OP_UPDATE.charAt(0) == type || OP_DELETE.charAt(0) == type ? String.valueOf(type) : Tags.OP_INSERT;
        }else {
            return Tags.OP_INSERT;
        }
    }
}
