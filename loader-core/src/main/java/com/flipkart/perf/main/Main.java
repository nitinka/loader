package com.flipkart.perf.main;

import ch.qos.logback.classic.Level;
import com.flipkart.perf.controller.JobController;
import com.flipkart.perf.domain.Load;
import com.flipkart.perf.common.jackson.ObjectMapperUtil;
import com.strategicgains.restexpress.Format;
import com.strategicgains.restexpress.RestExpress;
import com.strategicgains.restexpress.response.ResponseProcessor;
import org.apache.commons.cli.*;
import org.codehaus.jackson.map.ObjectMapper;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Class used by Loader agent to start Load generating Process
 */
public class Main {
    private static Logger logger;
    private static Options options;
    private static ObjectMapper objectMapper;
    static {
        objectMapper = ObjectMapperUtil.instance();
        logger = LoggerFactory.getLogger(Main.class);
        options = new Options();

        Option fileOption = new Option("f", "jobFile", true, "File containing Json representing the performance run");
        fileOption.setRequired(true);
        options.addOption(fileOption);

        options.addOption(new Option("j", "jobId", true, "Unique Job Id. By default it would be Random UUID"));
        options.addOption(new Option("s", "statsFolder", true, "Path where stats will be stored. Default is /var/log/loader/"));
        options.addOption(new Option("p", "httpPort", true, "Http Port to control the job"));
    }

    /**
     * -f jobJsonFile -j jobId -l statsFolder
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception{
        CommandLineParser parser = new GnuParser();

        try {
            CommandLine line = parser.parse(options, args);
            System.setProperty("BASE_PATH", statsFolder(line));
            buildLoader(jobJsonFile(line)).
                    start(jobId(line), httpPort(line));
        }
        catch (Exception e) {
            logger.error("Error while building loader instance",e);
        }
    }

    private static int httpPort(CommandLine line) {
        return Integer.parseInt(line.getOptionValue("p"));
    }

    private static Load buildLoader(String jobJsonFile) throws IOException {
        return objectMapper.readValue(new FileInputStream(jobJsonFile), Load.class);
    }

    private static String jobId(CommandLine line) {
        return line.getOptionValue('j', UUID.randomUUID().toString());
    }

    private static String statsFolder(CommandLine line) {
        return line.getOptionValue('s',"/tmp/");
    }

    private static String jobJsonFile(CommandLine line) {
        if(!line.hasOption('f')) {
            System.exit(1);
        }
        return line.getOptionValue('f');
    }
}
