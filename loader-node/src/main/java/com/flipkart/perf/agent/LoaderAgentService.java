package com.flipkart.perf.agent;

import com.flipkart.perf.agent.cache.LibCache;
import com.flipkart.perf.agent.client.LoaderServerClient;
import com.flipkart.perf.agent.config.LoaderAgentConfiguration;
import com.flipkart.perf.agent.daemon.AgentRegistrationThread;
import com.flipkart.perf.agent.daemon.JobHealthCheckThread;
import com.flipkart.perf.agent.daemon.JobProcessorThread;
import com.flipkart.perf.agent.daemon.JobStatsSyncThread;
import com.flipkart.perf.agent.health.JobProcessorHealthCheck;
import com.flipkart.perf.agent.resource.AdminResource;
import com.flipkart.perf.agent.resource.DeployResourcesResource;
import com.flipkart.perf.agent.resource.JobResource;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import java.io.IOException;
import java.util.EnumSet;
import java.util.concurrent.ExecutionException;


public class LoaderAgentService extends Application<LoaderAgentConfiguration> {
    private static Logger logger = LoggerFactory.getLogger(LoaderAgentService.class);

    @Override
    public void initialize(Bootstrap<LoaderAgentConfiguration> bootstrap) {
//        bootstrap.setName("loader-agent");
    }

    @Override
    public void run(final LoaderAgentConfiguration configuration, Environment environment) throws Exception {

//    	  FilterBuilder filterConfig = environment.addFilter(CrossOriginFilter.class, "/*");
//        filterConfig.setInitParam(CrossOriginFilter.PREFLIGHT_MAX_AGE_PARAM, String.valueOf(60*60*24));
        final FilterRegistration.Dynamic cors = environment.servlets().addFilter("crossOriginRequests", CrossOriginFilter.class);
        cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");

//        environment.addProvider(com.sun.jersey.multipart.impl.MultiPartReaderServerSide.class);
        environment.jersey().register(com.sun.jersey.multipart.impl.MultiPartReaderServerSide.class);



        JobHealthCheckThread.initialize(LoaderServerClient.buildClient(configuration.getServerInfo()),
                configuration.getJobProcessorConfig());

        LibCache.initialize(configuration.getResourceStorageFSConfig());

        JobStatsSyncThread.initialize(configuration.getJobStatSyncConfig(),
                configuration.getJobFSConfig(),
                LoaderServerClient.buildClient(configuration.getServerInfo()));

        JobProcessorThread.initialize(configuration.getJobProcessorConfig(),
                configuration.getJobFSConfig());

//        JMetric.initialize(configuration.getjMetricConfig());
//        environment.addResource(new JMetricController());

        environment.jersey().register(new DeployResourcesResource(configuration.getResourceStorageFSConfig()));
        environment.jersey().register(new AdminResource(configuration));
        environment.jersey().register(new JobResource(configuration.getJobProcessorConfig(),
                configuration.getJobFSConfig()));
        environment.healthChecks().register("JobProcessorThread",new JobProcessorHealthCheck());

        AgentRegistrationThread.initialize(LoaderServerClient.buildClient(configuration.getServerInfo()),
                configuration.getRegistrationParams());

        addShutdownHook(configuration);
    }

    private void addShutdownHook(final LoaderAgentConfiguration configuration) {
        Runtime.getRuntime().addShutdownHook(new Thread(){
            public void run() {
                logger.info("DeRegistering from server");
                try {
                	LoaderServerClient.buildClient(configuration.getServerInfo()).deRegister();
                } catch (IOException e) {
                    logger.error("",e);
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                } catch (ExecutionException e) {
                    logger.error("",e);
                } catch (InterruptedException e) {
                    logger.error("",e);
                }
            }
        });
    }


    public static void main(String[] args) throws Exception {
        args = new String[]{"server",args[0]};
        new LoaderAgentService().run(args);
    }

}
