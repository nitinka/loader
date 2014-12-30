package com.flipkart.perf;

/**
 * NitinK.Agarwal@yahoo.com
 */

import com.flipkart.perf.agent.config.LoaderAgentConfiguration;
import com.flipkart.perf.server.config.LoaderServerConfiguration;
import com.flipkart.perf.server.util.ObjectMapperUtil;
import io.dropwizard.Configuration;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class LoaderNodeConfiguration extends Configuration {
    private String appName;
    /**
     * Takes 3 values
     */
    private String serverConfigFile;
    private String agentConfigFile;

    private LoaderServerConfiguration serverConfig;
    private LoaderAgentConfiguration agentConfig;

    private static LoaderNodeConfiguration instance;

    public LoaderNodeConfiguration() {
        instance = this;
    }

    public static LoaderNodeConfiguration instance() {
        return instance;
    }

    public static LoaderNodeConfiguration getInstance() {
        return instance;
    }

    public static void setInstance(LoaderNodeConfiguration instance) {
        LoaderNodeConfiguration.instance = instance;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getServerConfigFile() {
        return serverConfigFile;
    }

    public void setServerConfigFile(String serverConfigFile) throws FileNotFoundException {
        this.serverConfigFile = serverConfigFile;
    }

    public String getAgentConfigFile() {
        return agentConfigFile;
    }

    public void setAgentConfigFile(String agentConfigFile) throws FileNotFoundException {
        this.agentConfigFile = agentConfigFile;
    }

    public LoaderServerConfiguration getServerConfig() {
        if(serverConfig == null) {
            try {
                serverConfig = ObjectMapperUtil.instance().readValue(new FileInputStream(serverConfigFile), LoaderServerConfiguration.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return serverConfig;
    }

    public LoaderAgentConfiguration getAgentConfig() {
        if(agentConfig == null) {
            try {
                agentConfig = ObjectMapperUtil.instance().readValue(new FileInputStream(agentConfigFile), LoaderAgentConfiguration.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return agentConfig;
    }

    public static void main(String[] args) throws IOException {
        ObjectMapperUtil.instance().readValue(new FileInputStream("./loader-node/config/loader-agent.json"), LoaderAgentConfiguration.class);
    }
}
