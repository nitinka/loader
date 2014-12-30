package com.flipkart.perf.server.service;

import com.flipkart.perf.domain.Group;
import com.flipkart.perf.domain.GroupFunction;
import com.flipkart.perf.domain.Load;
import com.flipkart.perf.function.FunctionParameter;
import com.flipkart.perf.server.config.ResourceStorageFSConfig;
import com.flipkart.perf.server.domain.*;
import com.flipkart.perf.server.util.ObjectMapperUtil;
import com.flipkart.perf.server.util.ResponseBuilder;
import org.codehaus.jackson.map.ObjectMapper;

import javax.ws.rs.WebApplicationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Created by nitin on 21/12/14.
 */
public class FunctionService {

    private final ResourceStorageFSConfig storageConfig;
    private static ObjectMapper objectMapper = ObjectMapperUtil.instance();

    public FunctionService(ResourceStorageFSConfig storageConfig) {
        this.storageConfig = storageConfig;
    }

    public List<Object> getFunctions(String functionsRegEx, boolean includeClassInfo) throws IOException {
        functionsRegEx = ".*" + functionsRegEx + ".*";
        System.out.println("functionsRegEx: "+functionsRegEx);
        List<Object> userFunctions = new ArrayList<Object>();
        File userFunctionsBaseFolder = new File(storageConfig.getUserClassInfoPath());
        if(userFunctionsBaseFolder.exists()) {
            for(File userFunctionFile : userFunctionsBaseFolder.listFiles()){
                String userFunctionFileName = userFunctionFile.getName();
                if(userFunctionFileName.endsWith("info.json")) {
                    String function = userFunctionFileName.replace(".info.json","");
                    if(Pattern.matches(functionsRegEx, function)) {
                        if(includeClassInfo) {
                            FunctionInfo functionInfo = objectMapper.readValue(userFunctionFile, FunctionInfo.class);
                            userFunctions.add(functionInfo);
                        }
                        else {
                            userFunctions.add(function);
                        }
                    }
                }
            }
        }
        return userFunctions;
    }

    public void deleteFunctions(String functionsRegEx) throws IOException {
        functionsRegEx = ".*" + functionsRegEx + ".*";
        System.out.println("functionsRegEx: "+functionsRegEx);
        File userFunctionsBaseFolder = new File(storageConfig.getUserClassInfoPath());
        if(userFunctionsBaseFolder.exists()) {
            Properties mappingProp = new Properties();
            mappingProp.load(new FileInputStream(storageConfig.getUserClassLibMappingFile()));
            for(File userFunctionFile : userFunctionsBaseFolder.listFiles()){
                String userFunctionFileName = userFunctionFile.getName();
                if(userFunctionFileName.endsWith("info")) {
                    userFunctionFileName = userFunctionFileName.replace(".info.json","");
                    if(Pattern.matches(functionsRegEx, userFunctionFileName)) {
                        userFunctionFile.delete();
                        mappingProp.remove(userFunctionFileName);
                    }
                }
            }
            mappingProp.store(new FileOutputStream(storageConfig.getUserClassLibMappingFile()), "Class and Library Mapping");
        }
    }

    public PerformanceRun buildPerformanceRun(String function, String runName) throws IOException {

        File userFunctionsInfoFile = new File(storageConfig.getUserClassInfoFile(function));
        if(!userFunctionsInfoFile.exists())
            throw new WebApplicationException(ResponseBuilder.resourceNotFound("Function", function));

        String functionClassName = function.split("\\.")[function.split("\\.").length-1];
        if(runName == null)
            runName = "run" + function;

        FunctionInfo functionInfo = objectMapper.readValue(userFunctionsInfoFile, FunctionInfo.class);

        GroupFunction groupFunction = new GroupFunction().
                setDumpData(true).
                setFunctionalityName(functionClassName + "_with_" + function).setFunctionClass(function);
        Map<String, FunctionParameter> functionInputParameters = functionInfo.getInputParameters();
        for(String inputParameterName : functionInputParameters.keySet()) {
            FunctionParameter inputParameterInfo = functionInputParameters.get(inputParameterName);
            groupFunction.addParam(inputParameterName, inputParameterInfo.getDefaultValue());
        }

        Group group = new Group().
                setName("group_" + functionClassName + "_" + function).
                setFunctions(Arrays.asList(new GroupFunction[]{groupFunction}));

        Load load = new Load().
                setGroups(Arrays.asList(new Group[]{group}));

        LoadPart loadPart = new LoadPart().
                setName("loadPart").
                setAgents(1).
                setClasses(Arrays.asList(new String[]{function})).
                setLoad(load);

        return new PerformanceRun().
                setRunName(runName).
                setMetricCollections(Arrays.asList(new MetricCollection[]{})).
                setOnDemandMetricCollections(Arrays.asList(new OnDemandMetricCollection[]{})).
                setLoadParts(Arrays.asList(new LoadPart[]{loadPart}));
    }
}
