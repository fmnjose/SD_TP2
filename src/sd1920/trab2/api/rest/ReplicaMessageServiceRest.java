package sd1920.trab2.api.rest;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import sd1920.trab2.api.Message;
import sd1920.trab2.replication.Operation;

public interface ReplicaMessageServiceRest extends MessageServiceRest {

    @GET
	@Path("/replica")
	@Produces(MediaType.APPLICATION_JSON)
	List<Operation> getUpdatedOperations(@HeaderParam(MessageServiceRest.HEADER_VERSION) Long version,
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
	void execPostMessage(@HeaderParam(MessageServiceRest.HEADER_VERSION) Long version, 
				Message msg, @QueryParam("secret") String secret);

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