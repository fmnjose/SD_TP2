package sd1920.trab2.api.rest;

import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import sd1920.trab2.api.User;

public interface ReplicaUserServiceRest extends UserServiceRest {
    @GET
	@Path("/update")
	@Produces(MediaType.APPLICATION_JSON)
	Map<String, User> getUsers(@QueryParam("secret") String secret);
	
	@POST
	@Path("/update")
	@Consumes(MediaType.APPLICATION_JSON)
	void updateUsers(Map<String,User> users, @QueryParam("secret") String secret);
}