package com.flipkart.perf.server.resource;


import com.codahale.metrics.annotation.Timed;
import com.flipkart.perf.common.util.FileHelper;
import com.flipkart.perf.server.config.JobFSConfig;
import com.flipkart.perf.server.domain.BusinessUnit;
import com.flipkart.perf.server.domain.PerformanceRun;
import com.flipkart.perf.server.exception.JobException;
import com.flipkart.perf.server.util.ObjectMapperUtil;
import com.flipkart.perf.server.util.ResponseBuilder;
import org.codehaus.jackson.map.ObjectMapper;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Create Various runs. Think of them as Performance Flows
 */
@Path("/loader-server/runs")
public class RunResource {

    private final JobFSConfig jobFSConfig;
    private static ObjectMapper objectMapper = ObjectMapperUtil.instance();

    public RunResource(JobFSConfig jobFSConfig) {
        this.jobFSConfig = jobFSConfig;
    }

    /**
     Following call simulates html form post call, where somebody uploads a file to server
     curl -X POST -d @file-containing-run-details http://localhost:9999/loader-server/runs --header "Content-Type:application/json"
     *
     * @param performanceRun
     * @throws java.io.IOException
     * @throws java.util.concurrent.ExecutionException
     * @throws InterruptedException
     */
    @Consumes(MediaType.APPLICATION_JSON)
    @POST
    @Timed
    public Response createRun(PerformanceRun performanceRun)
            throws IOException, ExecutionException, InterruptedException, JobException {
        if(performanceRun.exists())
            throw new WebApplicationException(ResponseBuilder.runNameAlreadyExists(performanceRun.getRunName()));

        BusinessUnit businessUnit = BusinessUnit.build(performanceRun.getBusinessUnit());
        if(businessUnit == null)
            throw new WebApplicationException(ResponseBuilder.badRequest("Business Unit "+ performanceRun.getBusinessUnit() + " Doesn't exist"));

        if(!businessUnit.teamExist(performanceRun.getTeam()))
            throw new WebApplicationException(ResponseBuilder.badRequest("Team " + performanceRun.getTeam()
                    + " Doesn't exist under Business Unit " + performanceRun.getBusinessUnit()));

        performanceRun.create();
        return ResponseBuilder.resourceCreated("Run", performanceRun.getRunName());
    }

    @Produces(MediaType.APPLICATION_JSON)
    @GET
    @Timed
    public Set<String> getRuns() throws IOException {
        Set<String> runs = new HashSet<String>();
        File runsPath = new File(jobFSConfig.getRunsPath());
        File[] runFolders = runsPath.listFiles();
        for(File runFolder: runFolders)
            runs.add(runFolder.getName());
        return runs;
    }

    @Produces(MediaType.APPLICATION_JSON)
    @GET
    @Path(value = "/{runName}")
    @Timed
    public PerformanceRun getRun(@PathParam("runName") String runName)
            throws IOException, InterruptedException, ExecutionException, JobException {
        return getRun(runName, "LATEST");
    }

    /**
     Following call simulates html form post call, where somebody uploads a file to server
     curl -X PUT -d @file-containing-run-details http://localhost:9999/loader-server/runs/runName --header "Content-Type:application/json"
     * @param newRun
     * @throws java.io.IOException
     * @throws java.util.concurrent.ExecutionException
     * @throws InterruptedException
     */
    @Path("/{runName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @PUT
    @Timed
    public void updateRun(@PathParam("runName") String runName,
                          PerformanceRun newRun)
            throws IOException, ExecutionException, InterruptedException, JobException {
        PerformanceRun existingRun = PerformanceRun.runExistsOrException(runName);
        existingRun.update(newRun);
    }

    /**
     Following call simulates html form post call, where somebody uploads a file to server
     curl
     -X DELETE
     http://localhost:9999/loader-server/runs/runName
     * @throws java.io.IOException
     * @throws java.util.concurrent.ExecutionException
     * @throws InterruptedException
     */
    @Path("/{runName}")
    @DELETE
    @Timed
    public void deleteRun(@PathParam("runName") String runName)
            throws IOException, ExecutionException, InterruptedException, JobException {
        PerformanceRun run = PerformanceRun.runExistsOrException(runName);
        run.delete();
    }

    @Produces(MediaType.APPLICATION_JSON)
    @GET
    @Path("/{runName}/jobs")
    @Timed
    public Set<String> getAllRunJobs(@PathParam("runName") String runName) throws IOException {
        PerformanceRun.runExistsOrException(runName);
        Set<String> jobs = new HashSet<String>();
        String runJobsFile = jobFSConfig.getRunAllJobsFile(runName);
        BufferedReader br = FileHelper.bufferedReader(runJobsFile);
        String jobId = null;
        while((jobId = br.readLine()) != null)
            jobs.add(jobId);
        FileHelper.close(br);
        return jobs;
    }

    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{runName}/versions")
    @GET
    @Timed
    public List<String> getVersions(@PathParam("runName") String runName)
            throws IOException, ExecutionException, InterruptedException, JobException {
        PerformanceRun.runExistsOrException(runName);
        return PerformanceRun.versions(runName);
    }

    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{runName}/versions/{version}")
    @GET
    @Timed
    public PerformanceRun getRun(@PathParam("runName") String runName,
                                 @PathParam("version") @DefaultValue("LATEST") String version)
            throws IOException, ExecutionException, InterruptedException, JobException {
        return PerformanceRun.runExistsOrException(runName, version);
    }

    @Produces(MediaType.APPLICATION_JSON)
    @GET
    @Path("/{runName}/versions/{version}/jobs")
    @Timed
    public Set<String> getRunJobs(@PathParam("runName") String runName,
                                  @PathParam("version") @DefaultValue("LATEST") String version) throws IOException {
        PerformanceRun.runExistsOrException(runName, version);
        Set<String> jobs = new HashSet<String>();
        String runJobsFile = jobFSConfig.getRunJobsFile(runName, PerformanceRun.runVersion(runName, version));
        BufferedReader br = FileHelper.bufferedReader(runJobsFile);
        String jobId = null;
        while((jobId = br.readLine()) != null)
            jobs.add(jobId);
        FileHelper.close(br);
        return jobs;
    }
}