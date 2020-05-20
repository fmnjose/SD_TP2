package sd1920.trab2.api.rest;

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
import sd1920.trab2.replication.Operation;
import sd1920.trab2.replication.VersionControl;

@Path(MessageServiceRest.PATH)
public interface ReplicaMessageServiceRest extends MessageServiceRest {

    @GET
	@Path("/replica")
	@Produces(MediaType.APPLICATION_JSON)
	List<Operation> getUpdatedOperations(	@HeaderParam(VersionControl.HEADER_VERSION) long version,
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
	void execPostMessage(@HeaderParam(VersionControl.HEADER_VERSION) long version, 
						Message msg,
						@QueryParam("secret") String secret);
			
	/**
	 * Used by the primary server to replicate a given postUser request
	 * @param version current version of the primary server
	 * @param msg user to be added
	 * @param secret shhh
	 */
	@DELETE
	@Path("/mbox/{user}/{mid}/replica")
	void execRemoveFromUserInbox(@HeaderParam(VersionControl.HEADER_VERSION) long version, 
								@PathParam("user") String user, 
								@PathParam("mid") long mid,
								@QueryParam("secret") String secret);

	@DELETE
	@Path("/msg/{user}/{mid}/replica")
	void execDeleteMessage(	@HeaderParam(VersionControl.HEADER_VERSION) long version, 
							@PathParam("user") String user,
							@PathParam("mid") long mid, 
							@QueryParam("secret") String secret);

	/**
	 * Saves a forwarded message
	 * @param msg message to be saved
	 * @return list of failed deliveries. Normally missing users
	 */
	@POST
	@Path("/mbox/replica")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	List<String> execPostForwardedMessage(	@HeaderParam(VersionControl.HEADER_VERSION) long version, 
											Message msg,
											@QueryParam("secret") String secret);

	/**
	 * Deletes a message from this servers. Forwarded request
	 * @param mid mid of message to be deleted
	 */
	@DELETE
	@Path("/msg/{mid}/replica")
	void execDeleteForwardedMessage(@HeaderParam(VersionControl.HEADER_VERSION) long version, 
									@PathParam("mid") long mid, 
									@QueryParam("secret") String secret);

	@GET
	@Path("/update")
	@Produces(MediaType.APPLICATION_JSON)
	Map<Long, Message> getAllMessages(@QueryParam("secret") String secret);

	@GET
	@Path("/update/mbox")
	@Produces(MediaType.APPLICATION_JSON)
	Map<String, Set<Long>> getUserInboxes(@QueryParam("secret") String secret);
	
	@POST
	@Path("/update")
	@Consumes(MediaType.APPLICATION_JSON)
	void updateAllMessages(Map<Long,Message> allMessages, @QueryParam("secret") String secret);

	@POST
	@Path("/update/mbox")
	@Consumes(MediaType.APPLICATION_JSON)
	void updateUserInboxes(Map<String,Set<Long>> usersInboxes, @QueryParam("secret") String secret);
}