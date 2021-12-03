package com.opswat.jenkins.plugins.metadefender;

public class Constants {
    public static final String SHORT_PLUGIN_NAME = "[MetaDefender Plugin]";
    public static final String PLUGIN_NAME = "Scan with MetaDefender";

    public static final String MDCLOUD_SCAN_DOMAIN = "https://api.metadefender.com";
    public static final String MDCLOUD_SCAN_RESULT_PREFIX_URL = "https://metadefender.opswat.com/results/file/";
    public static final String MDCLOUD_SCAN_RESULT_SUFFIX_URL = "/regular/overview?lang=en";

    public static final String MDLOCAL_SCAN_RESULT_MIDDLE_URL = "/#/public/process/dataId/";

    public static final String HEADER_APIKEY = "apikey";
    public static final String HEADER_FILENAME = "filename";
    public static final String HEADER_RULE = "rule";
    public static final String HEADER_PRIVATE_PROCESSING = "privateprocessing";

    public static final String LOG_NAME = "metadefender-plugin.log";
}
