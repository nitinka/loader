package com.flipkart.perf.server.resource;

import com.flipkart.perf.LoaderNodeConfiguration;
import com.flipkart.perf.server.domain.PerformanceRun;
import com.flipkart.perf.server.service.FunctionService;
import io.dropwizard.jersey.params.BooleanParam;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;


/**
 * Resource can be used to search available functions that can be used for load generation
 */
@Path("/loader-server/functions")
public class FunctionResource {
    private FunctionService functionService = new FunctionService(LoaderNodeConfiguration.getInstance().getServerConfig().getResourceStorageFSConfig());

    /**
     * Get All Deployed Functions
     * @return
     * @throws java.io.IOException
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Object> getFunctions(@QueryParam("classInfo") @DefaultValue("false") BooleanParam includeClassInfo) throws IOException {
        return functionService.getFunctions("", includeClassInfo.get());
    }

    /**
     * Get All Deployed Functions
     * @return
     * @throws java.io.IOException
     */
    @Path("/{functionsRegEx}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Object> getFunctions(@PathParam("functionsRegEx") @DefaultValue(".+") String functionsRegEx,
                                     @QueryParam("classInfo") @DefaultValue("false") BooleanParam includeClassInfo) throws IOException {

        return functionService.getFunctions(functionsRegEx, includeClassInfo.get());
    }

    /**
     * Get All Deployed Functions
     * @return
     * @throws java.io.IOException
     */
    @Path("/{function}/performanceRun")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public PerformanceRun buildPerformanceRun(@PathParam("function") String function,
                                     @QueryParam("runName") String runName) throws IOException {
        return functionService.buildPerformanceRun(function, runName);
    }


    /**
     * Get All Deployed Functions
     * @return
     * @throws java.io.IOException
     */
    @Path("/{functionsRegEx}")
    @DELETE
    public void deleteFunctions(@PathParam("functionsRegEx") @DefaultValue("$%^&*") String functionsRegEx) throws IOException {
        functionService.deleteFunctions(functionsRegEx);
    }

    public static void main(String[] args) {
        System.out.println("H.L".split("\\.").length);
    }
}
