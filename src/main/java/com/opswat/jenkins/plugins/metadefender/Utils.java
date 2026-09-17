package com.opswat.jenkins.plugins.metadefender;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Utils {

    /**
     * Null-safe check whether an array contains the given value.
     *
     * Replaces org.apache.commons.lang.ArrayUtils.contains(): Apache Commons Lang 2.6 was
     * removed from Jenkins core in 2.579, and the plugin never declared it as its own
     * dependency. Commons Lang 2.x has been EOL since 2011, so the call is inlined here
     * rather than bundling an unmaintained library.
     *
     * @param array the array to search, may be null
     * @param value the value to look for, may be null
     * @return true if the array is non-null and contains value
     */
    private static boolean arrayContains(String[] array, String value) {
        if (array == null) {
            return false;
        }
        for (String element : array) {
            if (element == null ? value == null : element.equals(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * List file recursively
     *
     * @param sourcePath the source file path, can be a file or folder
     * @param output list of files inside sourcePath
     * @param exclude exclude files/folders in this list
     */
    public static void listFilesRecursively(String sourcePath, ArrayList<File> output, String []exclude){
        if (arrayContains(exclude, sourcePath)) {
            return;
        }
        File dir = new File(sourcePath);
        if (!dir.isDirectory()) {
            output.add(dir);
            return;
        }
        File[] firstLevelFiles = dir.listFiles();
        if (firstLevelFiles != null && firstLevelFiles.length > 0) {
            for (File aFile : firstLevelFiles) {
                if (aFile.isDirectory()) {
                    listFilesRecursively(aFile.getAbsolutePath(), output, exclude);
                } else {
                    output.add(aFile);
                }
            }
        }
    }

    /**
     * Remove duplicate files in a list
     *
     * @param input input list
     */
    public static void removeDuplicateFileInList(ArrayList<File> input){
        Set<File> set = new HashSet<>(input);
        input.clear();
        input.addAll(set);
    }

    /**
     * Build a link to view scan result
     *
     * @param scanURL the source file path, can be a file or folder
     * @param dataID list of files inside sourcePath
	 * @return a full link to view scan result
     */
    public static String createScanResultLink(String scanURL, String dataID){
        if (scanURL.indexOf(Constants.MDCLOUD_SCAN_DOMAIN) == 0) {
            return Constants.MDCLOUD_SCAN_RESULT_PREFIX_URL + dataID + Constants.MDCLOUD_SCAN_RESULT_SUFFIX_URL;
        } else {
            String temp = scanURL.substring(0,scanURL.lastIndexOf("/")); //http://localhost:8008/
            return temp + Constants.MDLOCAL_SCAN_RESULT_MIDDLE_URL + dataID;
        }
    }

    /**
     * Convert InputStream to String
     *
     * @param input input stream
	 * @return output string
	 * @throws IOException in some circumstance
     **/
    public static String inputStreamtoString(InputStream input) throws IOException{
        StringBuilder string = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader
                (input, Charset.forName(StandardCharsets.UTF_8.name())))) {
            int c = 0;
            while ((c = reader.read()) != -1) {
                string.append((char) c);
            }
        }
        return string.toString();
    }

    /**
     * Change relative path to absoluted path,
     * @param rootPath root path to combine
     * @param input path list to change
	 * @return output paths
     */
    private static String[] convertRelativePathtoAbsolutedPath(String rootPath, String []input){
        if(input == null) return new String[0];

        String[] myStringArray = new String[input.length];

        for(int i=0; i < input.length; i++) {
            myStringArray[i] = rootPath + File.separator + input[i];
        }
        return myStringArray;
    }

    /**
     * Create a file list to scan
     *
     * @param source the source file path, can be a file or folder
     * @param exclude exclude files/folders from this list
	 * @param workspacePath work space path to combine
	 * @return array list file
     */
    public static ArrayList<File> createFileList(String source, String exclude, String workspacePath){
        String []excludeFiles = null;
        ArrayList<File> filesToScan = new ArrayList<>();
        if(!exclude.equals("")) {
            excludeFiles = exclude.split("\\|", 0);
        }
        excludeFiles = convertRelativePathtoAbsolutedPath(workspacePath, excludeFiles);
        if (source == null || source.equals("")) {
            Utils.listFilesRecursively(workspacePath + File.separator + source, filesToScan, excludeFiles);
        } else {
            String [] sources = source.split("\\|", 0);
            for (String s : sources) {
                Utils.listFilesRecursively(workspacePath + File.separator + s, filesToScan, excludeFiles);
            }
            Utils.removeDuplicateFileInList(filesToScan);
        }
        return filesToScan;
    }

    /**
     * Create log file
     *
     * @param filePath log file path
     * @param isAppend append or new
     * @param isLog if false, don't log
     *
     * */
    public static void writeLogFile(String filePath, String content, boolean isAppend, boolean isLog){
        if(!isLog) return;

        Writer myWriter = null;
        BufferedWriter out = null;
        try {
            myWriter = new OutputStreamWriter(new FileOutputStream(filePath, isAppend), StandardCharsets.UTF_8);
            myWriter.write(content);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (myWriter != null)
                    myWriter.close();
            }catch (IOException e) {
                //not sure what to do
            }
        }
    }
}

