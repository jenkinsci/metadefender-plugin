package com.opswat.jenkins.plugins.metadefender;

import hudson.*;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ScanBuilder extends Builder implements SimpleBuildStep {

    private final String scanURL;
    private final String apiKey;
    private final String source;
    private final String exclude;
    private final String rule;
    private final boolean isAbortBuild;
    private final boolean isPrivateScan;
    private final boolean isShowBlockedOnly;
    private final int timeout;

    public String getScanURL() {
        return scanURL;
    }

    public String getApiKey() {
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

    public boolean getIsShowBlockedOnly() { return isShowBlockedOnly;}

    public int getTimeout() {
        return timeout;
    }

    @DataBoundConstructor
    public ScanBuilder(String scanURL, String apiKey, String source, String exclude, String rule,
                       int timeout, boolean isAbortBuild, boolean isPrivateScan, boolean isShowBlockedOnly) {
        this.scanURL = scanURL;
        this.apiKey = apiKey;
        this.source = source;
        this.exclude = exclude;
        this.isAbortBuild = isAbortBuild;
        this.isPrivateScan = isPrivateScan;
        this.isShowBlockedOnly = isShowBlockedOnly;
        this.rule = rule;
        this.timeout = timeout;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException, AbortException {
        ConsoleLog console = new ConsoleLog(Constants.SHORT_PLUGIN_NAME, listener.getLogger());
        console.logInfo("Scan URL: " + scanURL );
        console.logInfo("Rule: " + rule );
        console.logInfo("Is Private Scan?: " + isPrivateScan );
        console.logInfo("Is the build failed if threads found?: " + isAbortBuild );
        console.logInfo("Scan folder/files: " + source);
        console.logInfo("Exclude folder/files: " + exclude);

        //Build a list of files to scan
        ArrayList<File> filesToScan = Utils.createFileList(source, exclude, workspace.getRemote());

        //Start a scanner with 10 threads and scan
        Scanner scanner = new Scanner(10);
        boolean foundBlockedResult = scanner.Start(filesToScan,workspace + "",
                scanURL, apiKey, rule, timeout, isPrivateScan, isShowBlockedOnly, listener);

        //Mark the build Aborted if needed
        if (foundBlockedResult && isAbortBuild) {
            throw new AbortException("Found an issue during scan");
        }
    }

    @Symbol("greet")
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

