package com.opswat.jenkins.plugins.metadefender;

import jenkins.security.MasterToSlaveCallable;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * The Scanner class is for scanning files in threads
 * it is designed to run on the node side
 */
public class Scanner extends MasterToSlaveCallable<ArrayList<ScanResult>, IOException> {
    private static final long serialVersionUID = 6106269076155338045L;
    private String workspacePath;
    private String scanURL;
    private String apiKey;
    private String source;
    private String exclude;
    private String rule;
    private boolean isPrivateScan;
    private boolean isCreateLog;
    private int timeout;
    private String logFilePath;


    public Scanner(String workspacePath, String scanURL, String apiKey, String source,
                   String exclude, String rule, boolean isPrivateScan, int timeout,
                   boolean isCreateLog){
        this.workspacePath = workspacePath;
        this.scanURL = scanURL;
        this.apiKey = apiKey;
        this.source = source;
        this.exclude = exclude;
        this.isPrivateScan = isPrivateScan;
        this.timeout = timeout;
        this.rule = rule;
        this.logFilePath = workspacePath + "/" + Constants.LOG_NAME;
        this.isCreateLog = isCreateLog;
    }


    public ArrayList<ScanResult> call() {
        ArrayList<File> filesToScan = Utils.createFileList(source, exclude, workspacePath);
        Utils.writeLogFile(logFilePath, "Start scanning\n", false, isCreateLog);

        List<Future<ScanResult>> futuresList = new ArrayList<>();

        //Start ExecutorService with X threads
        ExecutorService executor = Executors.newFixedThreadPool(10);
        for (File aFile : filesToScan) {
            Utils.writeLogFile(logFilePath, "Scan File " + aFile.getAbsolutePath() +"\n", true, isCreateLog);
            Callable<ScanResult> task = new ScanAction(aFile.getAbsolutePath());
            Future<ScanResult> future = executor.submit(task);
            futuresList.add(future);
        }

        ArrayList<ScanResult> results = new ArrayList<>();
        for (Future<ScanResult> future : futuresList) {
            try {
                ScanResult sc = future.get(timeout, TimeUnit.SECONDS);
                results.add(sc);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                Utils.writeLogFile(logFilePath, "Failed to get scan result - " + e.toString() + "\n", true, isCreateLog);
            }
        }
        executor.shutdown();
        Utils.writeLogFile(logFilePath, "Total scanned files: " + filesToScan.size() + "\n", true, isCreateLog);
        return results;
    }

    private class ScanAction implements Callable<ScanResult>{
        private String filePath;

        public ScanAction(String filePath) {
            this.filePath = filePath;
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
                String errMsg = "Failure in Call function " +e.getMessage();
                sc.setBlockedReason(errMsg);
                Utils.writeLogFile(logFilePath, errMsg + "\n", true, isCreateLog);
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
                    sc.setDataID("");
                    sc.setBlockedReason("File path: " + filePath + " - HTTP response code: " + response.getStatusLine().getStatusCode());
                    Utils.writeLogFile(logFilePath, "File path: " + filePath + " - HTTP response code: " +
                            response.getStatusLine().getStatusCode() + "\n", true, isCreateLog);
                }
            }
            catch (IOException e) {
                String errMsg = "Failed to upload file " +e.getMessage();
                sc.setBlockedReason(errMsg);
                Utils.writeLogFile(logFilePath, errMsg + "\n", true, isCreateLog);
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
                        String errMsg = "Polling dataID " + dataID+ " returns http code " + response.getStatusLine().getStatusCode();
                        sc.setBlockedReason(errMsg);
                        Utils.writeLogFile(logFilePath, errMsg + "\n", true, isCreateLog);
                        sc.setDataID("");
                    }
                }
                if (count > timeout) {
                    String errMsg = "Polling dataID " + dataID+ " timeout ";
                    sc.setBlockedReason(errMsg);
                    Utils.writeLogFile(logFilePath, errMsg + "\n", true, isCreateLog);
                    sc.setDataID(""); //Empty means failed
                } else {
                    sc.setBlockedResult(responseJSON.getJSONObject("process_info").getString("result"));
                    sc.setBlockedReason(responseJSON.getJSONObject("process_info").getString("blocked_reason"));
                    sc.setScanResult(responseJSON.getJSONObject("scan_results").getString("scan_all_result_a"));
                }
            }catch (InterruptedException|IOException e) {
                String errMsg = "Polling dataID " + dataID+ " failed. "+ e.toString();
                Utils.writeLogFile(logFilePath, errMsg +" \n", true, isCreateLog);
                sc.setBlockedReason(errMsg);
                sc.setDataID("");
            }
            return sc;
        }
    }
}
