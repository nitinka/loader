package com.flipkart.perf.agent;

//public class LoaderAgentService extends Application<LoaderAgentConfiguration> {
//    private static Logger logger = LoggerFactory.getLogger(LoaderAgentService.class);
//
//    @Override
//    public void initialize(Bootstrap<LoaderAgentConfiguration> bootstrap) {
////        bootstrap.setName("loader-agent");
//    }
//
//    @Override
//    public void run(final LoaderAgentConfiguration configuration, Environment environment) throws Exception {
//
//
//        JobHealthCheckThread.initialize(LoaderServerClient.buildClient(configuration.getServerInfo()),
//                configuration.getJobProcessorConfig());
//
//        LibCache.initialize(configuration.getResourceStorageFSConfig());
//
//        JobStatsSyncThread.initialize(configuration.getJobStatSyncConfig(),
//                configuration.getJobFSConfig(),
//                LoaderServerClient.buildClient(configuration.getServerInfo()));
//
//        JobProcessorThread.initialize(configuration.getJobProcessorConfig(),
//                configuration.getJobFSConfig());
//
////        JMetric.initialize(configuration.getjMetricConfig());
////        environment.addResource(new JMetricController());
//
//        environment.jersey().register(new DeployResourcesResource(configuration.getResourceStorageFSConfig()));
//        environment.jersey().register(new AdminResource(configuration));
//        environment.jersey().register(new JobResource(configuration.getJobProcessorConfig(),
//                configuration.getJobFSConfig()));
//        environment.healthChecks().register("JobProcessorThread",new JobProcessorHealthCheck());
//
//        AgentRegistrationThread.initialize(LoaderServerClient.buildClient(configuration.getServerInfo()),
//                configuration.getRegistrationParams());
//
//        addShutdownHook(configuration);
//    }
//
//    private void addShutdownHook(final LoaderAgentConfiguration configuration) {
//        Runtime.getRuntime().addShutdownHook(new Thread(){
//            public void run() {
//                logger.info("DeRegistering from server");
//                try {
//                	LoaderServerClient.buildClient(configuration.getServerInfo()).deRegister();
//                } catch (IOException e) {
//                    logger.error("",e);
//                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//                } catch (ExecutionException e) {
//                    logger.error("",e);
//                } catch (InterruptedException e) {
//                    logger.error("",e);
//                }
//            }
//        });
//    }
//
//
//    public static void main(String[] args) throws Exception {
//        args = new String[]{"server",args[0]};
//        new LoaderAgentService().run(args);
//    }
//
//}
