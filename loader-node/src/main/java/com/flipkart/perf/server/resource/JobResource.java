package com.flipkart.perf.server.resource;

import com.codahale.metrics.annotation.Timed;
import com.flipkart.perf.LoaderNodeConfiguration;
import com.flipkart.perf.common.util.FileHelper;
import com.flipkart.perf.domain.Group;
import com.flipkart.perf.domain.GroupFunction;
import com.flipkart.perf.server.cache.JobsCache;
import com.flipkart.perf.server.config.AgentConfig;
import com.flipkart.perf.server.config.JobFSConfig;
import com.flipkart.perf.server.daemon.JobDispatcherThread;
import com.flipkart.perf.server.domain.*;
import com.flipkart.perf.server.exception.InvalidJobStateException;
import com.flipkart.perf.server.exception.JobException;
import com.flipkart.perf.server.service.FunctionService;
import com.flipkart.perf.server.util.JobStatsHelper;
import com.flipkart.perf.server.util.ObjectMapperUtil;
import com.flipkart.perf.server.util.ResponseBuilder;
import io.dropwizard.jersey.params.BooleanParam;
import io.dropwizard.jersey.params.IntParam;
import org.codehaus.jackson.JsonParser.Feature;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Resource that receive Performance Job Request from Client Lib or Loader-Server UI
 */
@Path("/loader-server/jobs")
public class JobResource {
    private FunctionService functionService = new FunctionService(LoaderNodeConfiguration.getInstance().getServerConfig().getResourceStorageFSConfig());
    private AgentConfig agentConfig;
    private JobFSConfig jobFSConfig;
    private static JobStatsHelper jobStatsHelper;
    private static ObjectMapper objectMapper;
    private static Map<String,Map<String,ResourceCollectionInstance>> jobLastResourceMetricInstanceMap;
    private static Logger logger;

    static {
        objectMapper = new ObjectMapper();
        DateFormat dateFormat = new SimpleDateFormat("MMM dd hh:mm:ss z yyyy");
        objectMapper.setDateFormat(dateFormat);
        objectMapper.configure(Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);

        jobLastResourceMetricInstanceMap = new HashMap<String, Map<String, ResourceCollectionInstance>>();
        jobStatsHelper = JobStatsHelper.instance();
        logger = LoggerFactory.getLogger(JobResource.class);
    }

    public JobResource(AgentConfig agentConfig,
                       JobFSConfig jobFSConfig) {
        this.agentConfig = agentConfig;
        this.jobFSConfig = jobFSConfig;

        try {
            cleanRunningJobsBeforeRestart();
        } catch (IOException e) {
            logger.error("",e);
        }
    }

    private void cleanRunningJobsBeforeRestart() throws IOException {
        List<String> runningJobs = objectMapper.readValue(new File(jobFSConfig.getRunningJobsFile()), List.class);
        while(runningJobs.size() > 0) {
            try {
                logger.info("Clearing Job '"+runningJobs.get(0)+"' with RUNNING status at startup");
                killJob(runningJobs.remove(0));
            } catch (Exception e) {
                logger.error("",e);
            }
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(jobFSConfig.getRunningJobsFile()), runningJobs);
    }
    //To change body of created methods use File | Settings | File Templates.
    /**
     Following call simulates html form post call, where somebody uploads a file to server
     curl -X POST -d @file-containing-runName http://localhost:9999/loader-server/jobs --header "Content-Type:application/json"
     {"runName" : "runName"}
     It simply puts the performance job request in Job Dispatcher Thread Queue.
     * @param jobRequest
     * @throws java.io.IOException
     * @throws java.util.concurrent.ExecutionException
     * @throws InterruptedException
     */
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @POST
    @Timed
    public Job submitJob(JobRequest jobRequest) throws IOException {
        PerformanceRun.runExistsOrException(jobRequest.getRunName(), jobRequest.getRunVersion());
        return raiseJobRequest(jobRequest);
    }


    @Path("/chrome/jobs")
    @POST
    @Timed
    public Job queueJobFromExtension(ExtensionJob extensionJob) throws IOException {
        /**
         * Create run at runtime
         * Create a job and associate with the run
         * return the job id
         */

        String runName = UUID.randomUUID().toString();
        String functionClass = "perf.operation.http.function.HttpGet";
        if(extensionJob.getHttpMethod().toUpperCase().equals("POST")) {
            functionClass = "perf.operation.http.function.HttpPost";
        }

        else if(extensionJob.getHttpMethod().toUpperCase().equals("PUT")) {
            functionClass = "perf.operation.http.function.HttpPut";
        }

        else if(extensionJob.getHttpMethod().toUpperCase().equals("DELETE")) {
            functionClass = "perf.operation.http.function.HttpDelete";
        }

        PerformanceRun performanceRun = functionService.buildPerformanceRun(functionClass,runName);
        performanceRun.setBusinessUnit("chrome-extension").setTeam("chrome-extension");

        // Update Load Part
        LoadPart loadPart = performanceRun.getLoadParts().get(0);
        loadPart.setAgents(extensionJob.getLoadAgents());

        // Update Group
        Group group = loadPart.getLoad().getGroups().get(0);
        group.setDuration(extensionJob.getDurationMS());
        group.setGroupStartDelay(extensionJob.getLoadStartDelay());
        group.setRepeats(extensionJob.getRepeats());
        group.setThreads(extensionJob.getThreads());
        group.setThreadStartDelay(extensionJob.getThreadStartDelay());
        group.setThroughput(extensionJob.getThroughput());
        group.setWarmUpRepeats(extensionJob.getWarmUpRepeats());

        // Update Function
        GroupFunction function = group.getFunctions().get(0);
        function.setDumpData(true);
        function.addParam("url", extensionJob.getUrl());
        function.addParam("expectedStatusCode", extensionJob.getExpectedStatusCode());
        function.addParam("headers", extensionJob.getHeaders());

        if(extensionJob.getHttpMethod().toUpperCase().equals("POST") || extensionJob.getHttpMethod().toUpperCase().equals("PUT"))  {
            function.addParam("bodyString", extensionJob.getBody());
        }

        performanceRun.persist();
        return raiseJobRequest(new JobRequest().setRunName(runName).setRunVersion("1"));
    }

    /**
     * Submit the same job again if existing job has finished.
     * @param oldJobId
     * @return
     * @throws java.io.IOException
     * @throws java.util.concurrent.ExecutionException
     * @throws InterruptedException
     * @throws com.flipkart.perf.server.exception.JobException
     */
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{jobId}/rerun")
    @POST
    @Timed
    public Job rerunJob(@PathParam("jobId") String oldJobId)
            throws IOException, ExecutionException, InterruptedException, JobException {

        Job oldJob = jobExistsOrException(oldJobId);
        if(!oldJob.isCompleted())
            throw new WebApplicationException(ResponseBuilder.jobNotOver(oldJobId));

        return raiseJobRequest(new JobRequest().setRunName(oldJob.getRunName()));
    }

    /**
     * Get Job Details
     * @param jobId
     * @return
     * @throws java.io.IOException
     */
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{jobId}")
    @GET
    @Timed
    public Job getJob(@PathParam("jobId") String jobId) throws IOException, ExecutionException {
        return jobExistsOrException(jobId);
    }

    /**
     * Get Performance Run Schema used for the job
     * @param jobId
     * @return
     * @throws java.io.IOException
     * @throws java.util.concurrent.ExecutionException
     */
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{jobId}/run")
    @GET
    @Timed
    public PerformanceRun getPerformanceRun(@PathParam("jobId") String jobId) throws IOException, ExecutionException {
        return jobExistsOrException(jobId).performanceRun();
    }

    /**
     * Delte Job Details
     * @param jobId
     * @return
     * @throws java.io.IOException
     */
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{jobId}")
    @DELETE
    @Timed
    public void deleteJob(@PathParam("jobId") String jobId) throws IOException, ExecutionException {
        try {
            jobExistsOrException(jobId).delete();
        } catch (InvalidJobStateException e) {
            throw new WebApplicationException(ResponseBuilder.badRequest(e.getLocalizedMessage()));
        }
    }

    /**
     * Search Job based on runName, jobId and job status
     * By default it would search all running jobs only (And its little slow)
     * @param searchRunName
     * @param searchJobId
     * @param searchJobStatus
     * @return
     */
    @Produces(MediaType.APPLICATION_JSON)
    @GET
    @Timed
    public List<Job> getJobs(@QueryParam("runName") @DefaultValue("") String searchRunName,
                             @QueryParam("jobId") @DefaultValue("") String searchJobId,
                             @QueryParam("jobStatus") @DefaultValue("RUNNING,QUEUED")String searchJobStatus) throws IOException, ExecutionException {
        return Job.searchJobs(searchJobId, searchRunName, Arrays.asList(searchJobStatus.split(",")));
    }

    /**
     * Agents Publish Job Job Health Status when Load Generation Process goes in stress mode
     * @param request
     * @param jobId
     * @param jobHealthStatus
     * @throws java.io.IOException
     * @throws InterruptedException
     */
    @Path("/{jobId}/healthStatus")
    @PUT
    @Timed
    synchronized public void jobHealthStats(@Context HttpServletRequest request,
                                            @PathParam("jobId") String jobId,
                                            String jobHealthStatus)
            throws IOException, InterruptedException, ExecutionException {
        Job job = jobExistsOrException(jobId);
        job.getAgentsJobStatus().
                get(request.getRemoteAddr()).
                setHealthStatus(ObjectMapperUtil.instance().readValue(new ByteArrayInputStream(jobHealthStatus.getBytes()), Map.class));

        jobStatsHelper.persistJobHealthStatusComingFromAgent(jobId,
                request.getRemoteAddr(),
                new ByteArrayInputStream(jobHealthStatus.getBytes()));
    }

    /**
     * Agents Publish Job Load stats on this resource
     * @param request
     * @param jobId
     * @param relatedFilePath
     * @param jobStatsStream
     * @throws java.io.IOException
     * @throws InterruptedException
     */
    @Path("/{jobId}/jobStats")
    @POST
    @Timed
    synchronized public void jobStats(@Context HttpServletRequest request,
                                      @PathParam("jobId") String jobId,
                                      @QueryParam("file") String relatedFilePath,
                                      InputStream jobStatsStream)
            throws IOException, InterruptedException {
        jobStatsHelper.persistJobStatsComingFromAgent(jobId, request.getRemoteAddr(), relatedFilePath, jobStatsStream);
    }

    /**
     * Returns Job Stats Meta Data. Useful to see what all stats are being collected as part of performance testing
     * @param jobId
     * @return
     */
    @Path("/{jobId}/jobStats")
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    public List<JobStatsHelper.GroupStatsMeta> getJobMetricsStatsMeta(@PathParam("jobId") String jobId) throws IOException, ExecutionException {
        jobExistsOrException(jobId);
        return jobStatsHelper.getJobMetricsStatsMeta(jobId);
    }

    /**
     * Get Real Group Conf
     * @param jobId
     * @return
     */
    @Path("/{jobId}/groupConf/groups/{groupName}/agents/{agentIp}")
    @GET
    @Timed
    @Produces(MediaType.TEXT_PLAIN)
    public InputStream getJobGroupConf(@PathParam("jobId") String jobId,
                                       @PathParam("groupName") String groupName,
                                       @PathParam("agentIp") String agentIp,
                                       @QueryParam("last") @DefaultValue("false")BooleanParam last) throws IOException, ExecutionException {
        jobExistsOrException(jobId);
        return jobStatsHelper.getJobGroupConf(jobId, groupName, agentIp,last.get());
    }

    /**
     * Returns particular function stats
     * Example /jobId/jobStats/groups/sampleGroup/timers/timer1/agents/127.0.0.1
     * Example /jobId/jobStats/groups/sampleGroup/counters/counter1/agents/127.0.0.1
     * @param jobId
     * @return
     */
    @Path("/{jobId}/jobStats/groups/{groupName}/functions/{functionName}/{metricType}/{metricName}/agents/{agent}")
    @GET
    @Timed
    @Produces(MediaType.TEXT_PLAIN)
    public InputStream getJobMetricStats(@PathParam("jobId") String jobId,
                                         @PathParam("groupName") String groupName,
                                         @PathParam("functionName") String functionName,
                                         @PathParam("metricType") String metricType,
                                         @PathParam("metricName") String metricName,
                                         @PathParam("agent") String agent,
                                         @QueryParam("last") @DefaultValue("false") BooleanParam last) throws IOException, ExecutionException {
        jobExistsOrException(jobId);
        return jobStatsHelper.getJobMetricStats(jobId, groupName, functionName, metricType, metricName, agent, last.get());
    }

    /**
     * Monitoring Agents publishes job Related Monitoring stats here
     * @param request
     * @param jobId
     * @param resourcesCollectionInstances
     * @throws java.io.IOException
     * @throws InterruptedException
     */
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{jobId}/monitoringStats")
    @POST
    @Timed
    public void jobMonitoringStats(@Context HttpServletRequest request,
                                   @PathParam("jobId") String jobId,
                                   Map<String, List<ResourceCollectionInstance>> resourcesCollectionInstances)
            throws IOException, InterruptedException, ExecutionException {
        Job job = jobExistsOrException(jobId);

        if(job.isCompleted()) {
            logger.warn("Job "+jobId+" is already completed. Monitoring stats will not be persisted. Issuing request to monitoring service to stop sending");
            job.stopMonitoring();
            throw new WebApplicationException(ResponseBuilder.badRequest("Job "+jobId+" is already completed"));
        }

        Map<String,ResourceCollectionInstance> resourcesLastInstance = jobLastResourceMetricInstanceMap.get(jobId);

        if(resourcesLastInstance == null)
            resourcesLastInstance = new HashMap<String, ResourceCollectionInstance>();

        for(String resource : resourcesCollectionInstances.keySet()) {
            String jobMonitoringStatsPath = jobFSConfig.getJobResourceMonitoringFile(jobId,
                    request.getRemoteAddr(),
                    resource);

            List<ResourceCollectionInstance> resourceCollectionInstances = resourcesCollectionInstances.get(resource);
            FileHelper.createFilePath(jobMonitoringStatsPath);

            // Get Last Persisted Metric Instance. Compare it with new one, if changed then persist
            ResourceCollectionInstance resourceLastInstance = resourcesLastInstance.get(resource);

            for(ResourceCollectionInstance resourceCollectionInstance : resourceCollectionInstances) {
                boolean persistStat = true;

                if(resourceLastInstance != null) {
                    persistStat = !resourceLastInstance.toString().equals(resourceCollectionInstance.toString());
                }

                if(persistStat) {
                    FileHelper.persistStream(new ByteArrayInputStream((objectMapper.writeValueAsString(resourceCollectionInstances) + "\n"). // knocking off resource name from the files
                            getBytes()),
                            jobMonitoringStatsPath, true);
                    resourcesLastInstance.put(resource, resourceCollectionInstance);
                    resourceLastInstance = resourceCollectionInstance;
                }
            }
            if(resourceLastInstance != null)
                FileHelper.persistStream(new ByteArrayInputStream(objectMapper.writeValueAsString(resourceLastInstance).getBytes()), jobMonitoringStatsPath+".last", false);
            resourcesLastInstance.put(resource, resourceLastInstance);
        }
        jobLastResourceMetricInstanceMap.put(jobId, resourcesLastInstance);
    }

    /**
     * Returns Monitoring Stats Meta Data. Useful to see what all monitoring stats are being collected as part of performance testing
     * @param jobId
     * @return
     */
    @Path("/{jobId}/monitoringStats")
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    public List<JobStatsHelper.MonitoringAgentStats> getJobMonitoringStatsMeta(@PathParam("jobId") String jobId) throws IOException, ExecutionException {
        jobExistsOrException(jobId);
        return jobStatsHelper.getJobMonitoringStatsMeta(jobId);
    }

    /**
     * Returns stats for particular resource being monitored at particular box
     * @return
     */
    @Path("/{jobId}/monitoringStats/agents/{agent}/resources/{resourceName}")
    @GET
    @Timed
    @Produces(MediaType.TEXT_PLAIN)
    public InputStream getJobMonitoringResourceStats(@PathParam("jobId") String jobId,
                                                     @PathParam("agent") String agent,
                                                     @PathParam("resourceName") String resourceName,
                                                     @QueryParam("last") @DefaultValue("false") BooleanParam last) throws IOException, ExecutionException {
        jobExistsOrException(jobId);
        return jobStatsHelper.getJobMonitoringResourceStats(jobId, agent, resourceName, last.get());
    }

    /**
     * Return resource metric Keys that are being collected
     * @param jobId
     * @return
     */
    @Path("/{jobId}/monitoringStats/agents/{agent}/resources/{resourceName}/meta")
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    public Set<String> getJobMonitoringResourceMeta(@PathParam("jobId") String jobId,
                                                    @PathParam("agent") String agent,
                                                    @PathParam("resourceName") String resourceName) throws IOException, ExecutionException {
        jobExistsOrException(jobId);
        return jobStatsHelper.getJobMonitoringResourceMeta(jobId, agent, resourceName);
    }

    @Path("/{jobId}/logs")
    @GET
    @Timed
    @Produces(MediaType.TEXT_HTML)
    public String getJobLogs(@PathParam("jobId") String jobId) throws IOException, ExecutionException {
        Job job = jobExistsOrException(jobId);

        StringBuilder stringBuilder = new StringBuilder();
        for(String agentIp : job.getAgentsJobStatus().keySet()) {
            stringBuilder.append("<a href=\"" + agentConfig.getJobLogUrl(jobId, agentIp) +"\">" + agentIp + ".log</a><br>");
        }
        return stringBuilder.toString();
    }

    /**
     * This resource is called by Loader agent once job is completed
     * @param request
     * @param jobId
     * @throws InterruptedException
     * @throws java.util.concurrent.ExecutionException
     * @throws java.io.IOException
     */
    @Path("/{jobId}/over")
    @PUT
    @Timed
    public void jobOver(@Context HttpServletRequest request,
                        @PathParam("jobId") String jobId, @QueryParam("jobStatus") @DefaultValue("COMPLETED") String jobStatus) throws InterruptedException, ExecutionException, IOException {
        Job job = jobExistsOrException(jobId);
        if(jobStatus.equals("COMPLETED"))
            job.jobCompletedInAgent(request.getRemoteAddr());
        else if(jobStatus.equals("KILLED"))
            job.jobKilledInAgent(request.getRemoteAddr());
        else if(jobStatus.equals("ERROR"))
            job.jobErrorInAgent(request.getRemoteAddr());

    }


    /**
     * Will be called from Loader Server Management UI To kill the job in all agents
     * @param jobId
     * @throws InterruptedException
     * @throws java.util.concurrent.ExecutionException
     * @throws java.io.IOException
     */
    @Path("/{jobId}/kill")
    @PUT
    @Timed
    public void killJob(@PathParam("jobId") String jobId) throws InterruptedException, ExecutionException, IOException, JobException {
        Job job = jobExistsOrException(jobId);
        job.kill();
    }

    /**
     * Will be called from Loader Server Management UI To kill the job in specific agents
     * @param jobId
     * @throws InterruptedException
     * @throws java.util.concurrent.ExecutionException
     * @throws java.io.IOException
     * @throws com.flipkart.perf.server.exception.JobException
     */
    @Path("/{jobId}/agents/{agentIps}/kill")
    @PUT
    @Timed
    public void killJob(@PathParam("jobId") String jobId, @PathParam("agentIps") String agentIps) throws InterruptedException, ExecutionException, IOException, JobException {
        Job job = jobExistsOrException(jobId);
        job.killJobInAgents(Arrays.asList(agentIps.split(",")));
    }

    @GET
    @Path("/{jobId}/agents/{agentId}/logs")
    public InputStream getJobLogs(@PathParam("jobId") String jobId,
                                  @PathParam("agentId") String agentId,
                                  @QueryParam("lines") @DefaultValue("100") IntParam lines,
                                  @QueryParam("grep") @DefaultValue("") String grepExp) throws IOException, ExecutionException {
        Job job = jobExistsOrException(jobId);
        return job.getLogsFromAgent(agentId, lines, grepExp);
    }
    /**
     * Returns Monitoring Stats Meta Data. Useful to see what all monitoring stats are being collected as part of performance testing
     * @param jobId
     * @return
     */
    @Path("/{jobId}/remarks")
    @PUT
    @Timed
    @Consumes(MediaType.APPLICATION_JSON)
    public void updateJobRemarks(@PathParam("jobId") String jobId, String remarks) throws IOException, ExecutionException {
        jobExistsOrException(jobId).
                setRemarks(remarks).
                persist();
    }

    private Job raiseJobRequest(JobRequest jobRequest) throws IOException {
        Job job = new Job().
                setJobId(UUID.randomUUID().toString()).
                setRunName(jobRequest.getRunName()).
                setRunVersion(PerformanceRun.runVersion(jobRequest.getRunName(),jobRequest.getRunVersion()));

        job.persistRunInfo();
        JobDispatcherThread.instance().addJobRequest(job);
        return job;
    }

    private Job jobExistsOrException(String jobId) throws IOException, ExecutionException {
        Job job = JobsCache.getJob(jobId);
        if (job == null)
            throw new WebApplicationException(ResponseBuilder.resourceNotFound("Job", jobId));
        return job;
    }
}
