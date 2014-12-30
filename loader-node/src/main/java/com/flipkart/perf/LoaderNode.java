package com.flipkart.perf;

/**
 * NitinK.Agarwal@yahoo.com
 */

import com.flipkart.perf.agent.cache.LibCache;
import com.flipkart.perf.agent.client.LoaderServerClient;
import com.flipkart.perf.agent.config.LoaderAgentConfiguration;
import com.flipkart.perf.agent.daemon.AgentRegistrationThread;
import com.flipkart.perf.agent.daemon.JobHealthCheckThread;
import com.flipkart.perf.agent.daemon.JobProcessorThread;
import com.flipkart.perf.agent.daemon.JobStatsSyncThread;
import com.flipkart.perf.agent.health.JobProcessorHealthCheck;
import com.flipkart.perf.server.cache.AgentsCache;
import com.flipkart.perf.server.cache.JobsCache;
import com.flipkart.perf.server.config.LoaderServerConfiguration;
import com.flipkart.perf.server.daemon.*;
import com.flipkart.perf.server.dataFix.DataFixRunner;
import com.flipkart.perf.server.domain.WorkflowScheduler;
import com.flipkart.perf.server.health.CounterCompoundThreadHealthCheck;
import com.flipkart.perf.server.health.TimerComputationThreadHealthCheck;
import com.flipkart.perf.server.resource.*;
import com.flipkart.perf.server.util.AsyncHttpClientUtil;
import com.flipkart.perf.server.util.DeploymentHelper;
import com.flipkart.perf.server.util.JobStatsHelper;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.commons.cli.*;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import java.io.IOException;
import java.util.EnumSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class LoaderNode extends Application<LoaderNodeConfiguration> {
    private static Logger logger;

    public static enum Mode {
        AGENT, SERVER, SINGLE_NODE;
    }
    public static Mode mode;
    private static String serverHost = null;
    private static int serverPort = -1;

    private ScheduledExecutorService scheduledExecutorService;

    private static Options options = new Options();
    static {
        logger = LoggerFactory.getLogger(LoaderNode.class);
        mode = Mode.SINGLE_NODE;

        options.addOption("m", "mode", true, "Loader Node Mode. Possible Values : AGENT, SERVER, SINGLE_NODE. By Default it is SINGLE_NODE. ");
        options.addOption("s", "server", true, "Server Ip. Used in case mode is AGENT. Default value is localhost");
        options.addOption("p", "port", true, "Server Port. Used in case mode is AGENT. Default value is 9999");
        Option config = new Option("c", "config", true, "configuration file.");
        config.setRequired(true);
        options.addOption(config);
    }

    public LoaderNode() {}

    @Override
    public void initialize(Bootstrap<LoaderNodeConfiguration> bootstrap) {
        bootstrap.addBundle(new AssetsBundle("/assets", "/loader-ui", "index.html"));
    }

    @Override
    public void run(LoaderNodeConfiguration configuration, Environment environment) throws Exception {
        if(mode.equals(Mode.SERVER)
                || mode.equals(Mode.SINGLE_NODE)) {
            LoaderServerConfiguration serverConfiguration = configuration.getServerConfig();

            /**
             * Enable Cross Origin Scripting
             */
            final FilterRegistration.Dynamic cors = environment.servlets().addFilter("crossOriginRequests", CrossOriginFilter.class);
            cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");

            /**
             * Register Multi part Reader
             */
            environment.jersey().register(com.sun.jersey.multipart.impl.MultiPartReaderServerSide.class);

            /**
             * Inititalise Server Stuff
             */
            // Do Data Fixes
            new DataFixRunner(serverConfiguration.getDataFixConfig()).run();

            // Cache Initialization
            JobsCache.initiateCache(serverConfiguration.getJobFSConfig());
            com.flipkart.perf.server.cache.LibCache.initialize(serverConfiguration.getResourceStorageFSConfig());
            AgentsCache.initialize(serverConfiguration.getAgentConfig());

            JobStatsHelper.build(serverConfiguration.getJobFSConfig(), serverConfiguration.getAgentConfig(), serverConfiguration.getMonitoringAgentConfig());

            // Start the Scheduled Executor
            this.scheduledExecutorService = Executors.newScheduledThreadPool(serverConfiguration.getScheduledExecutorConfig().getThreadPoolSize());
            // initialize Daemon Services

            HistogramComputationThread.initialize(serverConfiguration.getJobFSConfig(), 10000).start();
            CounterCompoundThread.initialize(scheduledExecutorService, serverConfiguration.getJobFSConfig(), serverConfiguration.getScheduledExecutorConfig().getCounterCompoundThreadInterval());
            GroupConfConsolidationThread.initialize(scheduledExecutorService, serverConfiguration.getJobFSConfig(), serverConfiguration.getScheduledExecutorConfig().getGroupConfConsolidationThreadInterval());
            JobDispatcherThread.initialize(scheduledExecutorService, serverConfiguration.getScheduledExecutorConfig().getJobDispatcherThreadInterval());
            TimerComputationThread.initialize(scheduledExecutorService, serverConfiguration.getJobFSConfig(), serverConfiguration.getScheduledExecutorConfig().getTimerComputationThreadInterval());

            DeploymentHelper.initialize(serverConfiguration.getAgentConfig(),
                    serverConfiguration.getResourceStorageFSConfig());


            ScheduledWorkflowDispatcherThread.initialize();
            Thread workFlowDispatcher = new Thread(ScheduledWorkflowDispatcherThread.getInstance());
            workFlowDispatcher.start();
            WorkflowScheduler.initialize();

            environment.jersey().register(new JobResource(serverConfiguration.getAgentConfig(),
                    serverConfiguration.getJobFSConfig()));
            environment.jersey().register(new DeployResourcesResource(serverConfiguration.getResourceStorageFSConfig()));
            environment.jersey().register(new AgentResource(serverConfiguration.getAgentConfig()));
            environment.jersey().register(new RunResource(serverConfiguration.getJobFSConfig()));
            environment.jersey().register(new FunctionResource());
            environment.jersey().register(new BusinessUnitResource(serverConfiguration.getJobFSConfig()));
            environment.jersey().register(new AdminResource(serverConfiguration));
            environment.jersey().register(new ScheduledWorkflowResource());
            environment.jersey().register(new WorkflowJobResource());
            environment.healthChecks().register("Counter Health Check",new CounterCompoundThreadHealthCheck());
            environment.healthChecks().register("Timer Health Check", new TimerComputationThreadHealthCheck());
        }

        if(mode.equals(Mode.AGENT)
                || mode.equals(Mode.SINGLE_NODE)) {
            /**
             * Initialise Agent Stuff
             */
            LoaderAgentConfiguration agentConfiguration = configuration.getAgentConfig();
            if(serverHost != null) {
                agentConfiguration.getServerInfo().setHost(serverHost);
            }

            if(serverPort != -1) {
                agentConfiguration.getServerInfo().setPort(serverPort);
            }

            JobHealthCheckThread.initialize(LoaderServerClient.buildClient(agentConfiguration.getServerInfo()),
                    agentConfiguration.getJobProcessorConfig());

            LibCache.initialize(agentConfiguration.getResourceStorageFSConfig());

            JobStatsSyncThread.initialize(agentConfiguration.getJobStatSyncConfig(),
                    agentConfiguration.getJobFSConfig(),
                    LoaderServerClient.buildClient(agentConfiguration.getServerInfo()));

            JobProcessorThread.initialize(agentConfiguration.getJobProcessorConfig(),
                    agentConfiguration.getJobFSConfig());

            environment.jersey().register(new com.flipkart.perf.agent.resource.DeployResourcesResource(agentConfiguration.getResourceStorageFSConfig()));
            environment.jersey().register(new com.flipkart.perf.agent.resource.AdminResource(agentConfiguration));
            environment.jersey().register(new com.flipkart.perf.agent.resource.JobResource(agentConfiguration.getJobProcessorConfig(),
                    agentConfiguration.getJobFSConfig()));
            environment.healthChecks().register("JobProcessorThread",new JobProcessorHealthCheck());

            AgentRegistrationThread.initialize(LoaderServerClient.buildClient(agentConfiguration.getServerInfo()),
                    agentConfiguration.getRegistrationParams());

            addShutdownHook(agentConfiguration);        }

    }

    private void addShutdownHook(final LoaderAgentConfiguration configuration) {
        Runtime.getRuntime().addShutdownHook(new Thread(){
            public void run() {
                logger.info("DeRegistering from server");
                try {
                    LoaderServerClient.buildClient(configuration.getServerInfo()).deRegister();
                    AsyncHttpClientUtil.close();
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
        CommandLineParser parser = new BasicParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        }
        catch(Exception e) {
            logger.error("",e);
            printHelp();
            return;
        }


        if(cmd.hasOption('m')) {
            mode = Mode.valueOf(cmd.getOptionValue('m'));
        }

        if(mode.equals(Mode.AGENT)) {
            if(cmd.hasOption('s')) {
                serverHost = cmd.getOptionValue('m');
            }

            if(cmd.hasOption('p')) {
                serverPort = Integer.parseInt(cmd.getOptionValue('p'));
            }
        }

        if(cmd.hasOption('h')) {
            printHelp();
        }

        new LoaderNode().run(new String[] {"server", cmd.getOptionValue('c')});
    }

    private static void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( "ant", options );
    }

}
