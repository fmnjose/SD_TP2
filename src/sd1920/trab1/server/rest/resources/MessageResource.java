package sd1920.trab1.server.rest.resources;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import javax.inject.Singleton;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import sd1920.trab1.api.rest.MessageService;
import sd1920.trab1.api.Message;
import sd1920.trab1.api.User;


@Singleton
public class MessageResource implements MessageService {

	private Random randomNumberGenerator;
	
	private final Map<Long,Message> allMessages = new HashMap<Long, Message>();
	private final Map<User,Set<Long>> userInboxs = new HashMap<User, Set<Long>>();
	
	private static Logger Log = Logger.getLogger(MessageResource.class.getName());
	
	public MessageResource() {
		this.randomNumberGenerator = new Random(System.currentTimeMillis());
	}
	
	
	@Override
	public long postMessage(@QueryParam("pwd") String pwd, Message msg) {
		Log.info("Received request to register a new message (Sender: " + msg.getSender() + "; Subject: "+msg.getSubject()+")");
		
		//Check if message is valid, if not return HTTP CONFLICT (409)
		if(msg.getSender() == null || msg.getDestination() == null || msg.getDestination().size() == 0) {
			Log.info("Message was rejected due to lack of recepients.");
			throw new WebApplicationException( Status.CONFLICT );
		}
			
		
		//Generate a new id for the message, that is not in use yet
		long newID = Math.abs(randomNumberGenerator.nextLong());
		while(allMessages.containsKey(newID)) {
			newID = Math.abs(randomNumberGenerator.nextLong());
		}
		
		//Add the message to the global list of messages
		allMessages.put(newID, msg);
		
        Log.info("Created new message with id: " + newID);
        		
		//Add the message (identifier) to the inbox of each recipient
		for(String recipient: msg.getDestination()) {
			if(!userInboxs.containsKey(recipient)) {
				userInboxs.put(recipient, new HashSet<Long>());
			}
			userInboxs.get(recipient).add(newID);
		}
		
		//Return the id of the registered message to the client (in the body of a HTTP Response with 200)
		Log.info("Recorded message with identifier: " + newID);
		return newID;
	}

	@Override
	public Message getMessage(@PathParam("user") String user, @PathParam("mid") long mid,
    @QueryParam("pwd") String pwd) {
		Log.info("Received request for message with id: " + mid +".");
		if(!allMessages.containsKey(mid)) { //check if message exists
			Log.info("Requested message does not exists.");
			throw new WebApplicationException( Status.NOT_FOUND ); //if not send HTTP 404 back to client
		}
		
		Log.info("Returning requested message to user.");
		return allMessages.get(mid); //Return message to the client with code HTTP 200
	}

	@Override
	public List<Long> getMessages(@PathParam("user") String user, @QueryParam("pwd") String pwd) {
		Log.info("Received request for messages with optional user parameter set to: '" + user + "'");
		List<Message> messages = new ArrayList<Message>();
		if(user == null) {
			Log.info("Collecting all messages in server");
			messages.addAll(allMessages.values());
		} else {
			Log.info("Collecting all messages in server for user " + user);
			Set<Long> mids = userInboxs.getOrDefault(user, Collections.emptySet());
			for(Long l: mids) { 
				Log.info("Adding messaeg with id: " + l + ".");
				messages.add(allMessages.get(l));
			}
		}
		Log.info("Returning message list to user with " + messages.size() + " messages.");
		return messages;
	}

	@Override
	public void deleteMessage(@PathParam("user") String user, @PathParam("mid") long mid, 
    @QueryParam("pwd") String pwd) {
		Log.info("Received request to delete a message with the id: " + String.valueOf(mid));
		Message msg;
		if((msg = this.allMessages.get(mid)) == null){
			Log.info("Message not found");
			throw new WebApplicationException(Status.NOT_FOUND);
		}

		Set<String> users = msg.getDestination();

		for(String user : users){
			Log.info("Removing message for user " + user);
			this.userInboxs.get(user).remove(mid);
		}

	}

	@Override
	public void removeFromUserInbox(@PathParam("user") String user, @PathParam("mid") long mid, 
    @QueryParam("pwd") String pwd) {
		Log.info("Received request to delete message " + String.valueOf(mid) + " from the inbox of " + user);

		Message msg;
		if((msg = this.allMessages.get(mid)) == null){
			Log.info("Message not found");
			throw new WebApplicationException(Status.NOT_FOUND);
		}

		if(!msg.getDestination().contains(user)){
			Log.info("Message not sent to user");
			throw new WebApplicationException(Status.NOT_ACCEPTABLE);
		}

		Log.info("Deleting message from user inbox");
		this.userInboxs.get(user).remove(mid);
	}
}
