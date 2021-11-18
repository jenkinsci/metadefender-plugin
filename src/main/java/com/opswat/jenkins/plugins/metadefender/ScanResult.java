package com.opswat.jenkins.plugins.metadefender;

import java.io.Serializable;

public class ScanResult implements Serializable {
    private String filepath = "";
    private String blockedResult = "";
    private String blockedReason = "";
    private String scanResult = "";
    private String dataID = "";

    public String getScanResult() { return scanResult; }

    public void setScanResult(String scanResult) { this.scanResult = scanResult; }

    public String getDataID() {
        return dataID;
    }

    public void setDataID(String dataID) {
        this.dataID = dataID;
    }

    public String getBlockedResult() {
        return blockedResult;
    }

    public void setBlockedResult(String blockedResult) {
        this.blockedResult = blockedResult;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public void setBlockedReason(String blockedReason) {
        this.blockedReason = blockedReason;
    }

    public String getFilepath() {
        return filepath;
    }

    public void setFilepath(String filepath) {
        this.filepath = filepath;
    }

}
