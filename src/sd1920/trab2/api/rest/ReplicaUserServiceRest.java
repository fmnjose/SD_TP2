package sd1920.trab2.api.rest;

import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import sd1920.trab2.api.User;
import sd1920.trab2.replication.VersionControl;

@Path(UserServiceRest.PATH)
public interface ReplicaUserServiceRest extends UserServiceRest {

	/**
	 * Used by the primary server to replicate a given postUser request
	 * @param version current version of the primary server
	 * @param msg user to be added
	 * @param secret shhh
	 */
	@POST
	@Path("/replica")
	@Consumes(MediaType.APPLICATION_JSON)
	void execPostUser(@HeaderParam(VersionControl.HEADER_VERSION) Long version, 
				User user, @QueryParam("secret") String secret);

	/**
	 * Used by the primary server to replicate a given postUser request
	 * @param version current version of the primary server
	 * @param msg user to be added
	 * @param secret shhh
	 */
	@PUT
	@Path("/{name}/replica")
	@Consumes(MediaType.APPLICATION_JSON)
	void execUpdateUser(@HeaderParam(VersionControl.HEADER_VERSION) Long version, 
						@PathParam("name") String name,
						User user,
						@QueryParam("secret") String secret);

	/**
	 * Used by the primary server to replicate a given postUser request
	 * @param version current version of the primary server
	 * @param msg user to be added
	 * @param secret shhh
	 */
	@DELETE
	@Path("/{name}/replica")
	void execDeleteUser(@HeaderParam(VersionControl.HEADER_VERSION) Long version, 
				@PathParam("name") String name, @QueryParam("secret") String secret);


    @GET
	@Path("/update")
	@Produces(MediaType.APPLICATION_JSON)
	Map<String, User> getUsers(@QueryParam("secret") String secret);
	
	@POST
	@Path("/update")
	@Consumes(MediaType.APPLICATION_JSON)
	void updateUsers(Map<String,User> users, @QueryParam("secret") String secret);
}