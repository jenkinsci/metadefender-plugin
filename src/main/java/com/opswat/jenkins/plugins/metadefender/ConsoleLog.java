package com.opswat.jenkins.plugins.metadefender;

import hudson.AbortException;

import java.io.PrintStream;
import java.util.Date;
import java.util.logging.Logger;

public class ConsoleLog {
    private static final Logger LOG = Logger.getLogger(ConsoleLog.class.getName());
    private static final String LOG_FORMAT = "%1$-2s %2$-2s %3$s";

    private String name;
    private PrintStream logger;

    public PrintStream getLogger() {
        return logger;
    }

    public ConsoleLog(String name, PrintStream logger) throws AbortException {
        if (null != logger) {
            this.name = name;
            this.logger = logger;
        } else {
            throw new AbortException("Can't create log instance");
        }
    }

    public void logInfo(String msg) {
        logger.println(String.format(LOG_FORMAT, name, "[INFO]" , msg));
    }

    public void logWarn(String msg) {
        logger.println(String.format(LOG_FORMAT, name, "[WARN]", msg));
    }

    public void logError(String msg) {
        logger.println(String.format(LOG_FORMAT, name, "[ERROR]" , msg));
    }

}
