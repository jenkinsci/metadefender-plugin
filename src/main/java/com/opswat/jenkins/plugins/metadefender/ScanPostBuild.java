package com.opswat.jenkins.plugins.metadefender;

import hudson.*;
import hudson.model.*;
import hudson.tasks.*;
import hudson.util.FormValidation;
import hudson.util.Secret;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class ScanPostBuild extends Recorder {

    private final String scanURL;
    private final Secret apiKey;
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

    public boolean getIsShowBlockedOnly() { return isShowBlockedOnly;}

    public int getTimeout() {
        return timeout;
    }

    @DataBoundConstructor
    public ScanPostBuild(String scanURL, Secret apiKey, String source, String exclude,
                         String rule, int timeout, boolean isPrivateScan, boolean isAbortBuild, boolean isShowBlockedOnly) {
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
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws AbortException {
        ConsoleLog console = new ConsoleLog(Constants.SHORT_PLUGIN_NAME, listener.getLogger());
        console.logInfo("Scan URL: " + scanURL );
        console.logInfo("Rule: " + rule );
        console.logInfo("Is Private Scan?: " + isPrivateScan );
        console.logInfo("Is the build failed if threads found?: " + isAbortBuild );
        console.logInfo("Scan folder/files: " + source);
        console.logInfo("Exclude folder/files: " + exclude);

        //Build a list of files to scan
        ArrayList<File> filesToScan = Utils.createFileList(source, exclude, build.getWorkspace() + "");

        //Start a scanner with 10 threads and scan
        Scanner scanner = new Scanner(10);
        boolean foundBlockedResult = scanner.Start(filesToScan,build.getWorkspace() + "", scanURL,
                apiKey.getPlainText(), rule, timeout, isPrivateScan, isShowBlockedOnly, listener);

        //Mark the build Aborted if needed
        if (foundBlockedResult && isAbortBuild) {
            throw new AbortException("Found an issue during scan");
        }
        return true;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Symbol("greet")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

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

