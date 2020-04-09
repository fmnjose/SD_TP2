package sd1920.trab1.server.rest.resources;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import sd1920.trab1.api.rest.MessageServiceRest;
import sd1920.trab1.server.serverUtils.ServerMessageUtils;
import sd1920.trab1.server.rest.RESTMailServer;
import sd1920.trab1.api.Message;
import sd1920.trab1.api.User;

@Singleton
public class MessageResourceRest extends ServerMessageUtils implements MessageServiceRest {

	public MessageResourceRest() throws UnknownHostException {
		super();

		this.randomNumberGenerator = new Random(System.currentTimeMillis());
		
		Log = Logger.getLogger(MessageResourceRest.class.getName());

		this.domain = InetAddress.getLocalHost().getHostName();

		this.serverUri = String.format("http://%s:%d/rest",InetAddress.getLocalHost().getHostAddress(),RESTMailServer.PORT);
	}

	

	@Override
	public long postMessage(String pwd, Message msg) {
		User user;
		String sender = msg.getSender();
		Set<String> recipientDomains = new HashSet<>();

		Log.info("Received request to register a new message (Sender: " + sender + "; Subject: "+msg.getSubject()+")");
		
		//Check if message is valid, if not return HTTP CONFLICT (409)
		if(sender == null || msg.getDestination() == null || msg.getDestination().size() == 0) {
			Log.info("Message was rejected due to lack of recepients.");
			throw new WebApplicationException(Status.CONFLICT );
		}
		String senderName = getSenderCanonicalName(sender);

		user = this.getUserRest(senderName, pwd);

		if(user == null)
			throw new WebApplicationException(Status.FORBIDDEN);

		long newID = Math.abs(randomNumberGenerator.nextLong());

		synchronized(this.allMessages){
			while(allMessages.containsKey(newID))
				newID = Math.abs(randomNumberGenerator.nextLong());
			
			msg.setSender(String.format(SENDER_FORMAT, user.getDisplayName(), user.getName(), user.getDomain()));
			msg.setId(newID);
			allMessages.put(newID, msg);
							
		}
		Log.info("Created new message with id: " + newID);

		for(String recipient: msg.getDestination()){
			String[] tokens = recipient.split("@");
			if(tokens[1].equals(this.domain))
				saveMessage(senderName,tokens[0], false, msg);
			else
				recipientDomains.add(tokens[1]);
		}	

		this.forwardMessage(recipientDomains, msg, true);

		Log.info("Recorded message with identifier: " + msg.getId());
		return msg.getId();
	}

	@Override
	public Message getMessage(String user, long mid, String pwd) {
		
		User u = this.getUserRest(user, pwd);
		
		if(u == null){
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		Log.info("Received request for message with id: " + mid +".");
		synchronized(this.allMessages){
			synchronized(this.userInboxs){
				if(!this.allMessages.containsKey(mid) || !this.userInboxs.get(user).contains(mid)) { //check if message exists
					Log.info("Requested message does not exists.");
					throw new WebApplicationException( Status.NOT_FOUND ); //if not send HTTP 404 back to client
				}
			}
		}
		
		Log.info("Returning requested message to user.");
		return allMessages.get(mid); //Return message to the client with code HTTP 200
	
	}

	@Override
	public List<Long> getMessages(String user, String pwd) {
		Log.info("Received request for messages with optional user parameter set to: '" + user + "'");
		User u = this.getUserRest(user, pwd);

		if(u == null){
			Log.info("User with name " + user + " does not exist in the domain.");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		Set<Long> mids = new HashSet<Long>();
		
		Log.info("Collecting all messages in server for user " + user);
		synchronized(this.userInboxs){
			mids = userInboxs.getOrDefault(user, Collections.emptySet());
		}
		Log.info("Returning message list to user with " + mids.size() + " messages.");
		return new ArrayList<>(mids);
	}

	@Override
	public void deleteMessage(String user, long mid, String pwd) 
	{
		Log.info("Received request to delete a message with the id: " + String.valueOf(mid));
		
		User sender = null;
		
		Message msg;
		
		synchronized(this.allMessages){
			msg = this.allMessages.get(mid);
		}

		
		sender  = this.getUserRest(user, pwd);
		
		if(sender == null){
			Log.info("Delete message: User not found or wrong password");
			throw new WebApplicationException(Status.FORBIDDEN);
		}
		
		
		String userName = getSenderCanonicalName(user);
		
		if(msg == null || !getSenderCanonicalName(msg.getSender()).equals(userName))
		return;
		
		/*synchronized(this.allMessages){
			this.allMessages.remove(mid);
		}*/
		synchronized(this.userInboxs){
			this.userInboxs.get(user).remove(mid);
		}

		Set<String> recipientDomains = new HashSet<>();

		for(String u : msg.getDestination()){
			String[] tokens = u.split("@");
			if(tokens[1].equals(this.domain)){
				synchronized(this.userInboxs){
					userInboxs.get(tokens[0]).remove(mid);
					
					Log.info("Removing message for user " + u);
				}
			}else
				recipientDomains.add(tokens[1]);
		}

		deleteFromDomains(recipientDomains, sender.getName(),String.valueOf(mid), true);
	
	}

	@Override
	public void removeFromUserInbox(String user, long mid, String pwd) {
		Log.info("Received request to delete message " + String.valueOf(mid) + " from the inbox of " + user);
		
		User u = this.getUserRest(user, pwd);

		if(u == null){
			Log.info("User with name " + user + " does not exist in the domain.");
			throw new WebApplicationException(Status.FORBIDDEN);
		}
		
		//DUVIDA: e possivel apagar uma mensagem que nao esteja na inbox do user fornecido?
		synchronized(this.allMessages){
			synchronized(this.userInboxs){
				if(!this.allMessages.containsKey(mid) || !this.userInboxs.get(user).contains(mid)){
					Log.info("Message not found");
					throw new WebApplicationException(Status.NOT_FOUND);
				}
			}
		}
		Log.info("Deleting message from user inbox");
		synchronized(this.userInboxs){
			this.userInboxs.get(user).remove(mid);
		}
	}

	@Override
	public void createInbox(String user) {
		synchronized(this.userInboxs){
			this.userInboxs.put(user, new HashSet<>());
		}
	}

	@Override
	public List<String> postForwardedMessage(Message msg) {
		List<String> failedDeliveries = new LinkedList<>();

		for(String recipient: msg.getDestination()){
			String[] tokens = recipient.split("@");
			if(tokens[1].equals(this.domain) && !this.saveMessage(msg.getSender(), tokens[0], true, msg))
				failedDeliveries.add(recipient);
		}
		return failedDeliveries;
	}

	@Override
	public void deleteForwardedMessage(long mid) {
		Set<String> recipients = null;
		synchronized(this.allMessages){
			if(!this.allMessages.containsKey(mid))
				return;

			recipients = this.allMessages.get(mid).getDestination();
			this.allMessages.remove(mid);
		}

		for(String s : recipients){
			String[] tokens = s.split("@");
			if(tokens[1].equals(this.domain)){
				synchronized(this.userInboxs){
					userInboxs.get(tokens[0]).remove(mid);

					Log.info("Removing message for user " + s);
				}
			}
		}
	}
}
