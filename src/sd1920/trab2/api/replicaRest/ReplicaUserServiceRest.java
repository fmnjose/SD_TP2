package sd1920.trab2.api.replicaRest;

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
import sd1920.trab2.api.rest.UserServiceRest;

@Path(UserServiceRest.PATH)

/**
 * Interface implemented by servers that utilize the replica mechanism
 */
public interface ReplicaUserServiceRest extends UserServiceRest {

	/**
	 * Executes a Post User operation
	 * @param version primary server's version
	 * @param user user to be added
	 * @param secret shh
	 */
	@POST
	@Path("/replica")
	@Consumes(MediaType.APPLICATION_JSON)
	void execPostUser(@HeaderParam(ReplicaMessageServiceRest.HEADER_VERSION) Long version, 
				User user, @QueryParam("secret") String secret);

	/**
	 * Executes a Update User operation
	 * @param version primary server's version
	 * @param name name of the user to be updated
	 * @param user new user info
	 * @param secret shh
	 */
	@PUT
	@Path("/{name}/replica")
	@Consumes(MediaType.APPLICATION_JSON)
	void execUpdateUser(@HeaderParam(ReplicaMessageServiceRest.HEADER_VERSION) Long version, 
						@PathParam("name") String name,
						User user,
						@QueryParam("secret") String secret);

	/**
	 * Executes a Delete User operation
	 * @param version primary server's version
	 * @param name name of the user to be deleted
	 * @param secret shh
	 */
	@DELETE
	@Path("/{name}/replica")
	void execDeleteUser(@HeaderParam(ReplicaMessageServiceRest.HEADER_VERSION) Long version, 
				@PathParam("name") String name, @QueryParam("secret") String secret);


	/**
	 *  Endpoint available when this server is a primary replica
	 *  Called by one of the replicas whose version got way behind
	 *  That replica will just copy this one's state
	 *  @param secret shh
	 *  @return a Map of this server's users structure
	 */
    @GET
	@Path("/update")
	@Produces(MediaType.APPLICATION_JSON)
	Map<String, User> getUsers(@QueryParam("secret") String secret);
	
	/**
	 * Called by a replica, to itself, when this replica's version has been too outdated
	 * @param users Primary server's users structure
	 * @param secret shh
	 */
	@POST
	@Path("/update")
	@Consumes(MediaType.APPLICATION_JSON)
	void updateUsers(Map<String,User> users, @QueryParam("secret") String secret);
}