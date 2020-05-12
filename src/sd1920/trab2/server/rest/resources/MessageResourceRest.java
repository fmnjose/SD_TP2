package sd1920.trab2.server.rest.resources;

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

import sd1920.trab2.api.rest.MessageServiceRest;
import sd1920.trab2.server.serverUtils.LocalServerUtils;
import sd1920.trab2.server.rest.RESTMailServer;
import sd1920.trab2.api.Message;
import sd1920.trab2.api.User;

@Singleton
public class MessageResourceRest extends LocalServerUtils implements MessageServiceRest {

	public MessageResourceRest() throws UnknownHostException {
		super(true);

		this.randomNumberGenerator = new Random(System.currentTimeMillis());
		
		Log = Logger.getLogger(MessageResourceRest.class.getName());

		this.domain = InetAddress.getLocalHost().getHostName();

		this.serverUri = String.format(DOMAIN_FORMAT_REST, InetAddress.getLocalHost().getHostAddress(), RESTMailServer.PORT);
	}

	

	@Override
	public long postMessage(String pwd, Message msg) {
		User user;
		String sender = msg.getSender();
		Set<String> recipientDomains = new HashSet<>();

		System.out.println("postMessage: Received request to register a new message (Sender: " + sender + "; Subject: "+msg.getSubject()+")");
		
		if(sender == null || msg.getDestination() == null || msg.getDestination().size() == 0) {
			System.out.println("postMessage: Message was rejected due to lack of recepients.");
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

		System.out.println("postMessage: Created new message with id: " + newID);

		for(String recipient: msg.getDestination()){
			String[] tokens = recipient.split("@");
			if(tokens[1].equals(this.domain))
				saveMessage(senderName,recipient, false, msg);
			else
				recipientDomains.add(tokens[1]);
		}	

		this.forwardMessage(recipientDomains, msg, true);

		return msg.getId();
	}

	@Override
	public Message getMessage(String user, long mid, String pwd) {
		
		User u = this.getUserRest(user, pwd);
		
		if(u == null){
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		System.out.println("getMessage: Received request for message with id: " + mid +".");
		synchronized(this.allMessages){
			synchronized(this.userInboxs){
				if(!this.allMessages.containsKey(mid) || !this.userInboxs.get(user).contains(mid)) {
					System.out.println("getMessage: Requested message does not exists.");
					throw new WebApplicationException( Status.NOT_FOUND ); 
				}
			}
		}
		
		return allMessages.get(mid); 
	
	}

	@Override
	public List<Long> getMessages(String user, String pwd) {
		System.out.println("getMessages: Received request for messages to '" + user + "'");

		User u = this.getUserRest(user, pwd);

		if(u == null){
			System.out.println("getMessages: User with name " + user + " does not exist in the domain.");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		Set<Long> mids = new HashSet<Long>();
		
		synchronized(this.userInboxs){
			mids = userInboxs.getOrDefault(user, Collections.emptySet());
		}

		System.out.println("getMessages: Returning message list to user with " + mids.size() + " messages.");
		return new ArrayList<>(mids);
	}

	@Override
	public void deleteMessage(String user, long mid, String pwd) {
		System.out.println("deleteMessage: Received request to delete a message with the id: " + String.valueOf(mid));
		
		User sender = null;
		
		Message msg;
		
		synchronized(this.allMessages){
			msg = this.allMessages.get(mid);
		}

		
		sender  = this.getUserRest(user, pwd);
		
		if(sender == null){
			System.out.println("delete message: User not found or wrong password");
			throw new WebApplicationException(Status.FORBIDDEN);
		}
		
		
		String userName = getSenderCanonicalName(user);
		
		if(msg == null || !getSenderCanonicalName(msg.getSender()).equals(userName))
			return;
		
		synchronized(this.userInboxs){
			this.userInboxs.get(user).remove(mid);
		}

		//this will be used to compile the domains that we'll need to forward the request to
		Set<String> recipientDomains = new HashSet<>();

		for(String u : msg.getDestination()){
			String[] tokens = u.split("@");
			if(tokens[1].equals(this.domain)){
				synchronized(this.userInboxs){
					userInboxs.get(tokens[0]).remove(mid);
				}
			}else
				recipientDomains.add(tokens[1]);
		}

		forwardDelete(recipientDomains, String.valueOf(mid), true);
	}

	@Override
	public void removeFromUserInbox(String user, long mid, String pwd) {
		System.out.println("removeFromUserInbox: Received request to delete message " + String.valueOf(mid) + " from the inbox of " + user);
		
		User u = this.getUserRest(user, pwd);

		if(u == null){
			System.out.println("removeFromUserInbox: User with name " + user + " does not exist in the domain.");
			throw new WebApplicationException(Status.FORBIDDEN);
		}
		
		synchronized(this.allMessages){
			synchronized(this.userInboxs){
				if(!this.allMessages.containsKey(mid) || !this.userInboxs.get(user).contains(mid)){
					System.out.println("removeFromUserInbox: Message not found");
					throw new WebApplicationException(Status.NOT_FOUND);
				}
			}
		}
		synchronized(this.userInboxs){
			this.userInboxs.get(user).remove(mid);
		}
	}

	@Override
	public void createInbox(String user, String secret) {
		if(!secret.equals(RESTMailServer.secret)){
			System.out.println("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		synchronized(this.userInboxs){
			this.userInboxs.put(user, new HashSet<>());
		}
	}

	@Override
	public List<String> postForwardedMessage(Message msg, String secret) {
		System.out.println("postForwardedMessage: Received request to save the message " + msg.getId());

		if(!secret.equals(RESTMailServer.secret)){
			System.out.println("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		List<String> failedDeliveries = new LinkedList<>();

		for(String recipient: msg.getDestination()){
			String[] tokens = recipient.split("@");
			if(tokens[1].equals(this.domain) && !this.saveMessage(msg.getSender(), tokens[0], true, msg))
				failedDeliveries.add(recipient);
		}

		System.out.println("postForwardedMessage: Couldn't deliver the message to " + failedDeliveries.size() + " people");
		return failedDeliveries;
	}

	@Override
	public void deleteForwardedMessage(long mid, String secret) {
		System.out.println("deleteForwardedMessage: Received request to delete message " + mid);

		if(!secret.equals(RESTMailServer.secret)){
			System.out.println("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

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
				}
			}
		}
	}
}
