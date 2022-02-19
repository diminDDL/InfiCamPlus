package com.infiRay.XthermDemo;

/**
 * Created by Administrator on 2018/1/31 0031.
 */

public class ThermSettingModel {
    private String type;
    private String value;
    private  String  ok;

    public ThermSettingModel(String type,String value,String ok){

        this.type = type;
        this.value = value;
        this.ok=ok;
    }

    public String getType() {
        return type;
    }

    public void setValue(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }
    public String getOk() {
        return ok;
    }
}
