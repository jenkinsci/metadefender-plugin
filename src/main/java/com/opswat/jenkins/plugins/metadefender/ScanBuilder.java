package com.opswat.jenkins.plugins.metadefender;

import hudson.*;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;

public class ScanBuilder extends Builder implements SimpleBuildStep {

    private final String scanURL;
    private final Secret apiKey;
    private final String source;
    private final String exclude;
    private final String rule;
    private final boolean isAbortBuild;
    private final boolean isPrivateScan;
    private final boolean isShowBlockedOnly;
    private final boolean isCreateLog;
    private final int timeout;

    public String getScanURL() {
        return scanURL;
    }

    public Secret getApiKey() {
        return apiKey;
    }

    public String getSource() {
        return source;
    }

    public String getExclude() {
        return exclude;
    }

    public String getRule() {
        return rule;
    }

    public boolean getIsAbortBuild() {
        return isAbortBuild;
    }

    public boolean getIsPrivateScan() { return isPrivateScan;}

    public boolean getIsCreateLog() { return isCreateLog;}

    public boolean getIsShowBlockedOnly() { return isShowBlockedOnly;}

    public int getTimeout() {
        return timeout;
    }

    @DataBoundConstructor
    public ScanBuilder(String scanURL, Secret apiKey, String source, String exclude, String rule,
                       int timeout, boolean isAbortBuild, boolean isPrivateScan, boolean isShowBlockedOnly, boolean isCreateLog) {
        this.scanURL = scanURL;
        this.apiKey = apiKey;
        this.source = source;
        this.exclude = exclude;
        this.isAbortBuild = isAbortBuild;
        this.isPrivateScan = isPrivateScan;
        this.isShowBlockedOnly = isShowBlockedOnly;
        this.rule = rule;
        this.timeout = timeout;
        this.isCreateLog = isCreateLog;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        ConsoleLog console = new ConsoleLog(Constants.SHORT_PLUGIN_NAME, listener.getLogger());
        console.logInfo("Scan URL: " + scanURL );
        console.logInfo("Rule: " + rule );
        console.logInfo("Is Private Scan?: " + isPrivateScan );
        console.logInfo("Is the build failed if threads found?: " + isAbortBuild );
        console.logInfo("Scan folder/files: " + source);
        console.logInfo("Exclude folder/files: " + exclude);
        String workspacePath = workspace + "";

        boolean foundBlockedResult = false;
        Scanner sc = new Scanner(workspacePath, scanURL, apiKey.getPlainText(), source,
                exclude, rule, isPrivateScan, timeout, isCreateLog);
        ArrayList<ScanResult> results = null;
        VirtualChannel channel = launcher.getChannel();
        if (channel != null) {
            results = channel.call(sc);
        }
        if (results == null) {
            throw new AbortException("Can't get scan result, please check log file");
        } else {
            for (ScanResult rs : results) {
                if (rs.getDataID().equals("")) {
                    console.logError(rs.getFilepath().substring(workspacePath.length() + 1) + "|" + rs.getBlockedReason());
                    foundBlockedResult = true;
                } else {
                    if(rs.getBlockedResult().equals("Blocked")) {
                        console.logInfo(rs.getFilepath().substring(workspacePath.length() + 1) + " | " + Utils.createScanResultLink(scanURL, rs.getDataID()) + " | " + rs.getBlockedReason());
                        foundBlockedResult = true;
                    } else if(!isShowBlockedOnly) {
                        console.logInfo(rs.getFilepath().substring(workspacePath.length() + 1) + " | " + Utils.createScanResultLink(scanURL, rs.getDataID()) + " | " + rs.getScanResult());
                    }
                }
            }
        }
        if (foundBlockedResult && isAbortBuild) {
            throw new AbortException("Found an issue during scan");
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        public FormValidation doCheckScanURL(@QueryParameter String value)
                throws IOException, ServletException {

            if (value.length() == 0)
                return FormValidation.error("Please input Scan URL");

            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Constants.PLUGIN_NAME;
        }

    }

}

