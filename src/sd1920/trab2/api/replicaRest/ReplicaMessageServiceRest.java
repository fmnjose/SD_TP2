package sd1920.trab2.api.replicaRest;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import sd1920.trab2.api.Message;
import sd1920.trab2.api.rest.MessageServiceRest;
import sd1920.trab2.server.replica.utils.Operation;

@Path(MessageServiceRest.PATH)

/**
 * Interface implemented by servers that utilize the replica mechanism
 */
public interface ReplicaMessageServiceRest extends MessageServiceRest {
	public static final String HEADER_VERSION = "Msgserver-version";

	/**
	 * Endpoint used by replica servers to ask a primary to return the missing operations in their version
	 * @param version version of the requesting server
	 * @param secret shh
	 * @return List of Operations whose version is greater than the version param
	 */
    @GET
	@Path("/replica")
	@Produces(MediaType.APPLICATION_JSON)
	List<Operation> getUpdatedOperations(	@HeaderParam(HEADER_VERSION) long version,
											@QueryParam("secret") String secret);

	/**
	 * Used by the primary server to replicate a given postMessage request
	 * @param version current version of the primary server
	 * @param msg message object to be written, with id and sender set already
	 * @param secret shhh
	 */
	@POST
	@Path("/replica")
	@Consumes(MediaType.APPLICATION_JSON)
	void execPostMessage(@HeaderParam(HEADER_VERSION) long version, 
						Message msg,
						@QueryParam("secret") String secret);
			
	/**
	 * Executes a removeFromInbox operation
	 * Bypasses the verifications because they've already been done in the primary server
	 * @param version version of the primary server
	 * @param user accessing user
	 * @param mid mid of the message to be deleted
	 * @param secret shh
	 */
	@DELETE
	@Path("/mbox/{user}/{mid}/replica")
	void execRemoveFromUserInbox(@HeaderParam(HEADER_VERSION) long version, 
								@PathParam("user") String user, 
								@PathParam("mid") long mid,
								@QueryParam("secret") String secret);

	/**
	 * Eecutes a Delete operation
	 * Bypasses the verifications because they've already been done in the primary server
	 * @param version version of the primary server
	 * @param user accessing user
	 * @param mid mid of the message to be deleted
	 * @param secret shh
	 */
	@DELETE
	@Path("/msg/{user}/{mid}/replica")
	void execDeleteMessage(	@HeaderParam(HEADER_VERSION) long version, 
							@PathParam("user") String user,
							@PathParam("mid") long mid, 
							@QueryParam("secret") String secret);

	/**
	 * Executes a PostForwarded operation
	 * @param version version of the primary server
	 * @param msg forwarded message to be saved
	 * @param secret shh
	 * @return List of deliveries to users who don't exist
	 */
	@POST
	@Path("/mbox/replica")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	List<String> execPostForwardedMessage(	@HeaderParam(HEADER_VERSION) long version, 
											Message msg,
											@QueryParam("secret") String secret);

	/**
	 * Executes a DeleteForwarded Operation
	 * @param version version of the primary server
	 * @param mid mid of the message to be deleted
	 * @param secret shh
	 */
	@DELETE
	@Path("/msg/{mid}/replica")
	void execDeleteForwardedMessage(@HeaderParam(HEADER_VERSION) long version, 
									@PathParam("mid") long mid, 
									@QueryParam("secret") String secret);

	/**
	 *  Endpoint available when this server is a primary replica
	 *  Called by one of the replicas whose version got way behind
	 *  That replica will just copy this one's state
	 * @param secret shh
	 * @return the Map of all our messages
	 */
	@GET
	@Path("/update")
	@Produces(MediaType.APPLICATION_JSON)
	Map<Long, Message> getAllMessages(@QueryParam("secret") String secret);

	/**
	 *  Endpoint available when this server is a primary replica
	 *  Called by one of the replicas whose version got way behind
	 *  That replica will just copy this one's state
	 * @param secret shh
	 * @return the Map of our users' inboxes
	 */
	@GET
	@Path("/update/mbox")
	@Produces(MediaType.APPLICATION_JSON)
	Map<String, Set<Long>> getUserInboxes(@QueryParam("secret") String secret);
	
	/**
	 * Called by a replica, to itself, when this replica's version has been too outdated
	 * @param allMessages Map of the primary server's messages
	 * @param secret shh
	 */
	@POST
	@Path("/update")
	@Consumes(MediaType.APPLICATION_JSON)
	void updateAllMessages(Map<Long,Message> allMessages, @QueryParam("secret") String secret);

	/**
	 * Called by a replica, to itself, when this replica's version has been too outdated
	 * @param usersInboxes Map of the primary server's user inboxes'
	 * @param secret shh
	 */
	@POST
	@Path("/update/mbox")
	@Consumes(MediaType.APPLICATION_JSON)
	void updateUserInboxes(Map<String,Set<Long>> usersInboxes, @QueryParam("secret") String secret);
}