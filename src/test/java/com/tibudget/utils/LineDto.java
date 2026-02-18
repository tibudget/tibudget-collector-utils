package com.tibudget.utils;

public class LineDto {

    private String msisdn;
    private String state;
    private String login;
    private IdentityDto identity;
    private boolean isMaster;
    private String planType;

    public String getMsisdn() {
        return msisdn;
    }

    public String getState() {
        return state;
    }

    public IdentityDto getIdentity() {
        return identity;
    }

    public String getLogin() {
        return login;
    }

    public boolean isMaster() {
        return isMaster;
    }

    public String getPlanType() {
        return planType;
    }

    @Override
    public String toString() {
        return "LineDto{" + "msisdn='" + msisdn + '\'' + ", state='" + state + '\'' + ", login='" + login + '\'' + ", isMaster=" + isMaster + ", planType='" + planType + '\'' + ", identity=" + identity + '}';
    }
}
