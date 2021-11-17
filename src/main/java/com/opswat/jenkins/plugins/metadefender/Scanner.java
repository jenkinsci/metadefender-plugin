package com.opswat.jenkins.plugins.metadefender;

import hudson.AbortException;
import hudson.model.TaskListener;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * The Scanner class is for scanning files in threads
 */
public class Scanner{
    private int numberOfThread = 10;

    public Scanner(int numberOfThread) {
        this.numberOfThread = numberOfThread;
    }

    //Scan thread
    public boolean Start(ArrayList<File> filesToScan, String workspacePath, String scanURL, String apiKey, String rule, int timeout,
                         boolean isPrivateScan, boolean isShowBlockedOnnly, TaskListener listener) throws AbortException{

        ConsoleLog console = new ConsoleLog(Constants.SHORT_PLUGIN_NAME,listener.getLogger());
        List<Future<ScanResult>> futuresList = new ArrayList<>();

        //Start ExecutorService with X threads
        ExecutorService executor = Executors.newFixedThreadPool(this.numberOfThread);
        for (File aFile : filesToScan) {
            Callable<ScanResult> task = new ScannerThread(scanURL, apiKey, rule, aFile.getAbsolutePath(), timeout, isPrivateScan, console);
            Future<ScanResult> future = executor.submit(task);
            futuresList.add(future);
        }

        boolean foundBlockResult = false;

        for (Future<ScanResult> future : futuresList) {
            try {
                ScanResult sc = future.get(timeout, TimeUnit.SECONDS);
                if (sc.getDataID().equals("")) {
                    console.logError(sc.getFilepath().substring(workspacePath.length()) + 1);
                    foundBlockResult = true;
                } else {
                    if(sc.getBlockedResult().equals("Blocked")) {
                        console.logInfo(sc.getFilepath().substring(workspacePath.length() + 1) + " | " + Utils.createScanResultLink(scanURL, sc.getDataID()) + " | " + sc.getBlockedReason());
                        foundBlockResult = true;
                    } else if(!isShowBlockedOnnly) {
                        console.logInfo(sc.getFilepath().substring(workspacePath.length() + 1) + " | " + Utils.createScanResultLink(scanURL, sc.getDataID()) + " | " + sc.getScanResult());
                    }
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                console.logError(e.getMessage());
            }
        }
        executor.shutdown();
        console.logInfo("Total scanned files: " + filesToScan.size());
        return foundBlockResult;
    }

    /**
     * ScannerThread is class actually does a scan
     */
    private class ScannerThread implements Callable<ScanResult>{
        private String scanURL;
        private String apiKey;
        private String filePath;
        private int timeout;
        private boolean isPrivateScan;
        private ConsoleLog console;
        private String rule;

        public ScannerThread(String scanURL, String apiKey, String rule, String filePath,
                             int timeout, boolean isPrivateScan, ConsoleLog console) {
            this.scanURL = scanURL;
            this.apiKey = apiKey;
            this.filePath = filePath;
            this.timeout = timeout;
            this.isPrivateScan = isPrivateScan;
            this.rule = rule;
            this.console = console;
        }

        @Override
        public ScanResult call() throws Exception
        {
            ScanResult sc = new ScanResult();
            sc.setFilepath(filePath);
            try {
                RequestConfig config = RequestConfig.custom()
                        .setConnectTimeout(timeout * 1000)
                        .setConnectionRequestTimeout(timeout * 1000)
                        .setSocketTimeout(timeout * 1000).build();
                CloseableHttpClient httpClient =
                        HttpClientBuilder.create().setDefaultRequestConfig(config).build();
                sc = uploadFile(filePath, httpClient);
                if (!sc.getDataID().equals("")){
                    sc = pollingResult(sc.getDataID(), httpClient);
                    sc.setFilepath(filePath);
                }
            }
            catch (Exception e) {
                console.logInfo("File path: " + filePath + "\n" + e.getMessage());
                sc.setDataID("");
            }
            return sc;
        }

        /**
         * Upload a file to MetaDefender to scan.
         *
         * @param  filePath   file path to scan
         * @return         Scan result object with Data ID, an empty data id means file failed to scan
         */
        private ScanResult uploadFile(String filePath, CloseableHttpClient httpClient) {
            ScanResult sc = new ScanResult();
            sc.setFilepath(filePath);

            File f = new File(filePath);

            HttpPost post = new HttpPost(scanURL);
            post.setHeader("Content-Type", "application/octet-stream");

            //set file name
            post.setHeader(Constants.HEADER_FILENAME, f.getName());
            //set APIKEY
            if (!apiKey.equals("")) {
                post.setHeader(Constants.HEADER_APIKEY, apiKey);
            }
            //set Rule
            if (!rule.equals("")) {
                post.setHeader(Constants.HEADER_RULE, rule);
            }
            //set PrivateScan
            if (isPrivateScan) {
                post.setHeader(Constants.HEADER_PRIVATE_PROCESSING, "1");
            }

            post.setEntity(new FileEntity(f));

            try {
                HttpResponse response = httpClient.execute(post);
                if (response.getStatusLine().getStatusCode() == 200) {
                    InputStream responseStream = response.getEntity().getContent();
                    String responseString = Utils.inputStreamtoString(responseStream);
                    JSONObject responseJSON = new JSONObject(responseString);
                    sc.setDataID(responseJSON.getString("data_id"));
                } else {
                    console.logError("File path: " + filePath + " - HTTP response code: " + response.getStatusLine().getStatusCode());
                    sc.setDataID("");
                }
            }
            catch (Exception e) {
                console.logInfo("File path: " + filePath + " - " + e.getMessage());
                sc.setDataID("");
            }
            return sc;
        }

        /**
         * Retrieve scan result from a data id
         *
         * @param  dataID   data id to retrieve scan result
         * @return         Scan result object
         */
        private ScanResult pollingResult(String dataID, CloseableHttpClient httpClient){
            ScanResult sc = new ScanResult();
            sc.setDataID(dataID);
            HttpGet get = new HttpGet(scanURL + "/" + dataID);

            if (!apiKey.equals("")) {
                get.setHeader(Constants.HEADER_APIKEY, apiKey);
            }

            try {
                int progressPercent = 0;
                int count = 0;
                JSONObject responseJSON = null;

                //polling the scan result until either progress percentage is 100% or timeout
                while (progressPercent < 100 && count <= timeout) {
                    HttpResponse response = httpClient.execute(get);
                    if (response.getStatusLine().getStatusCode() == 200) {
                        InputStream responseStream = response.getEntity().getContent();
                        String responseString = Utils.inputStreamtoString(responseStream);
                        responseJSON = new JSONObject(responseString);
                        if (responseJSON.has("process_info")) { //MD Cloud doesn't have it from beginning
                            progressPercent = responseJSON.getJSONObject("process_info").getInt("progress_percentage");
                        }
                        Thread.sleep(1000);
                        count++;
                    } else{
                        console.logError("Polling dataID " + dataID+ " returns http code " + response.getStatusLine().getStatusCode());
                        sc.setDataID("");
                    }
                }
                if (count > timeout) {
                    console.logError("DataID: " + dataID +" - Scan timeout after " + timeout + "s");
                    sc.setDataID(""); //Empty means failed
                } else {
                    sc.setBlockedResult(responseJSON.getJSONObject("process_info").getString("result"));
                    sc.setBlockedReason(responseJSON.getJSONObject("process_info").getString("blocked_reason"));
                    sc.setScanResult(responseJSON.getJSONObject("scan_results").getString("scan_all_result_a"));
                }
            }catch (Exception e) {
                console.logError("DataID: " + dataID + " - " + e.getMessage());
                sc.setDataID("");
            }
            return sc;
        }
    }
}
