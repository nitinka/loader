package com.flipkart.perf.server.client;

import com.flipkart.perf.domain.Load;
import com.flipkart.perf.server.cache.LibCache;
import com.flipkart.perf.server.exception.JobException;
import com.flipkart.perf.server.exception.LibNotDeployedException;
import com.flipkart.perf.server.util.AsyncHttpClientUtil;
import com.flipkart.perf.server.util.ObjectMapperUtil;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.ning.http.multipart.FilePart;
import com.ning.http.multipart.StringPart;
import io.dropwizard.jersey.params.IntParam;
import org.codehaus.jackson.map.ObjectMapper;

import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class LoaderAgentClient {
    private String host;
    private int port;
    private static LibCache libCache;

    private static final String RESOURCE_PLATFORM_LIB = "/loader-agent/resourceTypes/platformLibs";
    private static final String RESOURCE_UDF_LIB = "/loader-agent/resourceTypes/udfLibs";
    private static final String RESOURCE_INPUT_FILE = "/loader-agent/resourceTypes/inputFiles";
    private static final String RESOURCE_JOB = "/loader-agent/jobs";
    private static final String RESOURCE_JOB_KILL = "/loader-agent/jobs/{jobId}/kill";
    private static final String RESOURCE_JOB_LOGS = "/loader-agent/jobs/{jobId}/log?lines={lines}&grep={grepExp}";
    private static final String RESOURCE_ADMIN_REGISTRATION_INFO = "/loader-agent/admin/registrationInfo";
    private static ObjectMapper objectMapper = ObjectMapperUtil.instance();
    private static AsyncHttpClient asyncHttpClient = AsyncHttpClientUtil.instance();

    static {
        libCache = LibCache.instance();
    }

    public LoaderAgentClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public LoaderAgentClient setHost(String host) {
        this.host = host;
        return this;
    }

    public int getPort() {
        return port;
    }

    public LoaderAgentClient setPort(int port) {
        this.port = port;
        return this;
    }

    public Map registrationInfo() throws IOException, ExecutionException, InterruptedException {
        AsyncHttpClient.BoundRequestBuilder b = asyncHttpClient.
                prepareGet("http://" + this.getHost() + ":" + this.getPort() + RESOURCE_ADMIN_REGISTRATION_INFO);

        Future<Response> r = b.execute();
        Response response = r.get();
        return objectMapper.readValue(response.getResponseBodyAsStream(), Map.class);
    }

    public boolean deployPlatformLibs() throws IOException, ExecutionException, InterruptedException, LibNotDeployedException {
        if(libCache.getPlatformZipPath() == null) {
            throw new LibNotDeployedException("Platform Lib Not Deployed Yet on Loader Server. Deploy them before submitting another job");
        }
        AsyncHttpClient.BoundRequestBuilder b = asyncHttpClient.
                preparePost("http://" + this.getHost() + ":" + this.getPort() + RESOURCE_PLATFORM_LIB).
                setHeader("Content-Type", MediaType.MULTIPART_FORM_DATA).
                addBodyPart(new FilePart("lib", new File(libCache.getPlatformZipPath())));

        Future<Response> r = b.execute();
        if(!r.isDone())
            r.get();

        boolean successfulDeployment = r.get().getStatusCode() == 200;
        return successfulDeployment;
    }

    public boolean deployUDFLib(String libPath, String classList) throws IOException, ExecutionException, InterruptedException {
        AsyncHttpClient.BoundRequestBuilder b = asyncHttpClient.
                preparePost("http://" + this.getHost() + ":" + this.getPort() + RESOURCE_UDF_LIB).
                setHeader("Content-Type", MediaType.MULTIPART_FORM_DATA).
                addBodyPart(new FilePart("lib", new File(libPath))).
                addBodyPart(new StringPart("classList", classList));

        Future<Response> r = b.execute();
        r.get();
        boolean successfulDeployment = r.get().getStatusCode() == 204;
        return successfulDeployment;
    }

    public boolean deployInputFile(String resourceName, String inputFilePath) throws IOException, ExecutionException, InterruptedException {
        AsyncHttpClient.BoundRequestBuilder b = asyncHttpClient.
                preparePost("http://" + this.getHost() + ":" + this.getPort() + RESOURCE_INPUT_FILE).
                setHeader("Content-Type", MediaType.MULTIPART_FORM_DATA).
                addBodyPart(new FilePart("file", new File(inputFilePath))).
                addBodyPart(new StringPart("resourceName", resourceName));

        Future<Response> r = b.execute();
        r.get();
        boolean successfulDeployment = r.get().getStatusCode() == 204;
        return successfulDeployment;
    }

    public void submitJob(String jobId, Load load, String classListStr)
            throws ExecutionException, InterruptedException, JobException, IOException {
        AsyncHttpClient.BoundRequestBuilder b = asyncHttpClient.
                preparePost("http://"+this.getHost()+":" +
                        this.getPort() +
                        RESOURCE_JOB).
                setHeader("Content-Type", MediaType.MULTIPART_FORM_DATA).
                addBodyPart(new StringPart("jobId", jobId)).
                addBodyPart(new StringPart("jobJson", objectMapper.writeValueAsString(load))).
                addBodyPart(new StringPart("classList", classListStr));

        Future<Response> r = b.execute();
        r.get();
        if(r.get().getStatusCode() != 200) {
            throw new JobException("JobId "+jobId+" submission failed with error response :"+r.get().getResponseBody());
        }
    }

    public void killJob(String jobId) throws ExecutionException, InterruptedException, JobException, IOException {
        AsyncHttpClient.BoundRequestBuilder b = asyncHttpClient.
                preparePut("http://"+this.getHost()+":" +
                        this.getPort() +
                        RESOURCE_JOB_KILL.
                                replace("{jobId}", jobId));
        Future<Response> r = b.execute();
        r.get();
        if(r.get().getStatusCode() != 200) {
            throw new JobException("JobId "+jobId+" kill failed with error response :"+r.get().getResponseBody());
        }
    }

    public InputStream getLogs(String jobId, IntParam lines, String grepExp)
            throws IOException, ExecutionException, InterruptedException, JobException {
        AsyncHttpClient.BoundRequestBuilder b = asyncHttpClient.
                prepareGet("http://" + this.getHost() + ":" +
                        this.getPort() +
                        RESOURCE_JOB_LOGS
                                .replace("{jobId}", jobId)
                                .replace("{lines}", lines.toString())
                                .replace("{grepExp}", URLEncoder.encode(grepExp,"UTF-8")));

        Future<Response> r = b.execute();
        Response res = r.get();
        if(r.get().getStatusCode() != 200) {
            throw new JobException("Getting logs for job id '"+jobId+"' failed with error response :"+r.get().getResponseBody());
        }
        return res.getResponseBodyAsStream();
    }
}
