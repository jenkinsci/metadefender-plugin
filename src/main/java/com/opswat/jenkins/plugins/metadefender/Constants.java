package com.opswat.jenkins.plugins.metadefender;

public class Constants {
    public static String SHORT_PLUGIN_NAME = "[MetaDefender Plugin]";
    public static String PLUGIN_NAME = "Scan with MetaDefender";

    public static String MDCLOUD_SCAN_DOMAIN = "https://api.metadefender.com";
    public static String MDCLOUD_SCAN_RESULT_PREFIX_URL = "https://metadefender.opswat.com/results/file/";
    public static String MDCLOUD_SCAN_RESULT_SUFFIX_URL = "/regular/overview?lang=en";

    public static String MDLOCAL_SCAN_RESULT_MIDDLE_URL = "/#/public/process/dataId/";

    public static String HEADER_APIKEY = "apikey";
    public static String HEADER_FILENAME = "filename";
    public static String HEADER_RULE = "rule";
    public static String HEADER_PRIVATE_PROCESSING = "privateprocessing";

    public static String LOG_NAME = "metadefender-plugin.log";
}
