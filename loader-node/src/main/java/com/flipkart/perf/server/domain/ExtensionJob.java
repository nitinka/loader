package com.flipkart.perf.server.domain;

import java.util.Map;

/**
 * Created by nitin on 21/12/14.
 */
public class ExtensionJob {
    private String jobId;
    private String url;
    private Map<String, Object> headers;
    private String httpMethod;
    private String body;
    private int expectedStatusCode;
    private int repeats;
    private int threads;
    private int durationMS;
    private int loadAgents;
    private int warmUpRepeats;
    private float throughput;
    private int threadStartDelay;
    private int loadStartDelay;

    public String getJobId() {
        return jobId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, Object> headers) {
        this.headers = headers;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public int getExpectedStatusCode() {
        return expectedStatusCode;
    }

    public void setExpectedStatusCode(int expectedStatusCode) {
        this.expectedStatusCode = expectedStatusCode;
    }

    public int getRepeats() {
        return repeats;
    }

    public void setRepeats(int repeats) {
        this.repeats = repeats;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public int getDurationMS() {
        return durationMS;
    }

    public void setDurationMS(int durationMS) {
        this.durationMS = durationMS;
    }

    public int getLoadAgents() {
        return loadAgents;
    }

    public void setLoadAgents(int loadAgents) {
        this.loadAgents = loadAgents;
    }

    public int getWarmUpRepeats() {
        return warmUpRepeats;
    }

    public void setWarmUpRepeats(int warmUpRepeats) {
        this.warmUpRepeats = warmUpRepeats;
    }

    public float getThroughput() {
        return throughput;
    }

    public void setThroughput(float throughput) {
        this.throughput = throughput;
    }

    public int getThreadStartDelay() {
        return threadStartDelay;
    }

    public void setThreadStartDelay(int threadStartDelay) {
        this.threadStartDelay = threadStartDelay;
    }

    public int getLoadStartDelay() {
        return loadStartDelay;
    }

    public void setLoadStartDelay(int loadStartDelay) {
        this.loadStartDelay = loadStartDelay;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
