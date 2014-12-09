package com.flipkart.perf.server;

/**
 * Date : 28/12/2012
 * USer : nitinka
 */

import com.flipkart.perf.server.cache.AgentsCache;
import com.flipkart.perf.server.cache.JobsCache;
import com.flipkart.perf.server.cache.LibCache;
import com.flipkart.perf.server.config.LoaderServerConfiguration;
import com.flipkart.perf.server.daemon.*;
import com.flipkart.perf.server.dataFix.DataFixRunner;
import com.flipkart.perf.server.domain.WorkflowScheduler;
import com.flipkart.perf.server.health.CounterCompoundThreadHealthCheck;
import com.flipkart.perf.server.health.TimerComputationThreadHealthCheck;
import com.flipkart.perf.server.resource.*;
import com.flipkart.perf.server.util.DeploymentHelper;
import com.flipkart.perf.server.util.JobStatsHelper;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.servlets.CrossOriginFilter;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import java.util.EnumSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class LoaderServerService extends Application<LoaderServerConfiguration> {

    private ScheduledExecutorService scheduledExecutorService;

    public LoaderServerService() {
    }
	
    @Override
    public void initialize(Bootstrap<LoaderServerConfiguration> bootstrap) {
        bootstrap.addBundle(new AssetsBundle("/assets", "/ui", "index.html"));

    }

    @Override
    public void run(LoaderServerConfiguration configuration, Environment environment) throws Exception {
//        environment.jersey().setUrlPattern("/api/*");
        final FilterRegistration.Dynamic cors = environment.servlets().addFilter("crossOriginRequests", CrossOriginFilter.class);
        cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");

//        // Generic Stuff
//        FilterBuilder filterConfig = environment.addFilter(CrossOriginFilter.class, "/*");
//        filterConfig.setInitParam(CrossOriginFilter.PREFLIGHT_MAX_AGE_PARAM, String.valueOf(60*60*24)); // 1 day - jetty-servlet CrossOriginFilter will convert to Int.

        environment.jersey().register(com.sun.jersey.multipart.impl.MultiPartReaderServerSide.class);

        // Do Data Fixes
        new DataFixRunner(configuration.getDataFixConfig()).run();

        // Cache Initialization
        JobsCache.initiateCache(configuration.getJobFSConfig());
        LibCache.initialize(configuration.getResourceStorageFSConfig());
        AgentsCache.initialize(configuration.getAgentConfig());

        JobStatsHelper.build(configuration.getJobFSConfig(), configuration.getAgentConfig(), configuration.getMonitoringAgentConfig());

        // Start the Scheduled Executor
        this.scheduledExecutorService = Executors.newScheduledThreadPool(configuration.getScheduledExecutorConfig().getThreadPoolSize());
        // initialize Daemon Services

        HistogramComputationThread.initialize(configuration.getJobFSConfig(), 10000).start();
        CounterCompoundThread.initialize(scheduledExecutorService, configuration.getJobFSConfig(), configuration.getScheduledExecutorConfig().getCounterCompoundThreadInterval());
//        CounterThroughputThread.initialize(scheduledExecutorService, configuration.getJobFSConfig(), configuration.getScheduledExecutorConfig().getCounterThroughputThreadInterval());
        GroupConfConsolidationThread.initialize(scheduledExecutorService, configuration.getJobFSConfig(), configuration.getScheduledExecutorConfig().getGroupConfConsolidationThreadInterval());
        JobDispatcherThread.initialize(scheduledExecutorService, configuration.getScheduledExecutorConfig().getJobDispatcherThreadInterval());
        TimerComputationThread.initialize(scheduledExecutorService, configuration.getJobFSConfig(), configuration.getScheduledExecutorConfig().getTimerComputationThreadInterval());

        DeploymentHelper.initialize(configuration.getAgentConfig(),
                configuration.getResourceStorageFSConfig());


        ScheduledWorkflowDispatcherThread.initialize();
        Thread workFlowDispatcher = new Thread(ScheduledWorkflowDispatcherThread.getInstance());
        workFlowDispatcher.start();
        WorkflowScheduler.initialize();

//        JMetric.initialize(configuration.getjMetricConfig());
//        environment.addResource(new JMetricController());

        environment.jersey().register(new JobResource(configuration.getAgentConfig(),
                configuration.getJobFSConfig()));
        environment.jersey().register(new DeployResourcesResource(configuration.getResourceStorageFSConfig()));
        environment.jersey().register(new AgentResource(configuration.getAgentConfig()));
        environment.jersey().register(new RunResource(configuration.getJobFSConfig()));
        environment.jersey().register(new FunctionResource(configuration.getResourceStorageFSConfig()));
        environment.jersey().register(new BusinessUnitResource(configuration.getJobFSConfig()));
        environment.jersey().register(new AdminResource(configuration));
        environment.jersey().register(new ScheduledWorkflowResource());
        environment.jersey().register(new WorkflowJobResource());
        environment.healthChecks().register("Counter Health Check",new CounterCompoundThreadHealthCheck());
        environment.healthChecks().register("Timer Health Check", new TimerComputationThreadHealthCheck());
    }

    public static void main(String[] args) throws Exception {
        args = new String[]{"server",args[0]};
        new LoaderServerService().run(args);
    }

}
