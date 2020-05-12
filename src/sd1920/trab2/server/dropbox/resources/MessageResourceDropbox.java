package sd1920.trab2.server.dropbox.resources;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import com.google.gson.Gson;

import sd1920.trab2.api.Message;
import sd1920.trab2.api.User;
import sd1920.trab2.api.rest.MessageServiceRest;
import sd1920.trab2.server.dropbox.DropboxMailServer;
import sd1920.trab2.server.dropbox.requests.CreateFile;
import sd1920.trab2.server.dropbox.requests.Delete;
import sd1920.trab2.server.dropbox.requests.DownloadFile;
import sd1920.trab2.server.dropbox.requests.SearchFile;
import sd1920.trab2.server.rest.RESTMailServer;
import sd1920.trab2.server.serverUtils.DropboxServerUtils;

public class MessageResourceDropbox extends DropboxServerUtils implements MessageServiceRest {

	public static final String MESSAGES_DIR_FORMAT = "/%s/messages";
	public static final String MESSAGE_FORMAT = "/%s/messages/%s";
	
	private static Gson json = new Gson();

    public MessageResourceDropbox() throws UnknownHostException {
		super(DropboxMailServer.secret);

		this.randomNumberGenerator = new Random(System.currentTimeMillis());
		
		Log = Logger.getLogger(DropboxMailServer.class.getName());

		this.domain = InetAddress.getLocalHost().getHostName();

		this.serverUri = String.format(DOMAIN_FORMAT_REST, InetAddress.getLocalHost().getHostAddress(), RESTMailServer.PORT);
	}

	

	@Override
	public long postMessage(String pwd, Message msg) {
		User user;
		String sender = msg.getSender();
		Set<String> recipientDomains = new HashSet<>();

		Log.info("postMessage: Received request to register a new message (Sender: " + sender + "; Subject: "+msg.getSubject()+")");
		
		if(sender == null || msg.getDestination() == null || msg.getDestination().size() == 0) {
			Log.info("postMessage: Message was rejected due to lack of recepients.");
			throw new WebApplicationException(Status.CONFLICT );
		}

		String senderName = getSenderCanonicalName(sender);

		user = this.getUserRest(senderName, pwd);

		if(user == null)
			throw new WebApplicationException(Status.FORBIDDEN);

		long newID = Math.abs(randomNumberGenerator.nextLong());

		String path = String.format(MESSAGES_DIR_FORMAT, DropboxMailServer.hostname);

		while(SearchFile.run(path, Long.toString(newID)))
			newID = Math.abs(randomNumberGenerator.nextLong());
		
		msg.setSender(String.format(SENDER_FORMAT, user.getDisplayName(), user.getName(), user.getDomain()));
		msg.setId(newID);							
		
		Log.info("postMessage: Created new message with id: " + newID);

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

	@SuppressWarnings("unchecked")
	@Override
	public Message getMessage(String user, long mid, String pwd) {
		
		User u = this.getUserRest(user, pwd);
		
		if(u == null){
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		String path = String.format(UserResourceDropbox.USER_MSGS_FILE_FORMAT, 
						DropboxMailServer.hostname, user);

		Set<Long> ids = json.fromJson(DownloadFile.run(path) , HashSet.class);
		
		Log.info("getMessage: Received request for message with id: " + mid +".");
		
		if(!ids.contains(mid)){
			Log.info("getMessage: Requested message does not exist.");
			throw new WebApplicationException( Status.NOT_FOUND );
		}

		path = String.format(MESSAGE_FORMAT, DropboxMailServer.hostname, Long.toString(mid));

		Message message = json.fromJson(DownloadFile.run(path) , Message.class);
		
		return message; 
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<Long> getMessages(String user, String pwd) {
		Log.info("getMessages: Received request for messages to '" + user + "'");

		User u = this.getUserRest(user, pwd);

		if(u == null){
			Log.info("getMessages: User with name " + user + " does not exist in the domain.");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		String path = String.format(UserResourceDropbox.USER_MSGS_FILE_FORMAT,
					DropboxMailServer.hostname, user);

		Set<Long> mids = json.fromJson(DownloadFile.run(path) , HashSet.class);

		Log.info("getMessages: Returning message list to user with " + mids.size() + " messages.");
		return new ArrayList<>(mids);
	}

	@Override
	public void deleteMessage(String user, long mid, String pwd) {
		Log.info("deleteMessage: Received request to delete a message with the id: " + String.valueOf(mid));
		
		User sender = null;
		
		Message msg;
		
		sender  = this.getUserRest(user, pwd);
		
		if(sender == null){
			Log.info("delete message: User not found or wrong password");
			throw new WebApplicationException(Status.FORBIDDEN);
		}
		
		String path = String.format(MESSAGE_FORMAT, 
					DropboxMailServer.hostname, Long.toString(mid));

		msg = json.fromJson(DownloadFile.run(path) , Message.class);
		
		String userName = getSenderCanonicalName(user);
		
		if(msg == null || !getSenderCanonicalName(msg.getSender()).equals(userName))
			return;
		

		removeMessage(user, mid);

		//this will be used to compile the domains that we'll need to forward the request to
		Set<String> recipientDomains = new HashSet<>();

		for(String u : msg.getDestination()){
			String[] tokens = u.split("@");
			if(tokens[1].equals(this.domain)){				
				removeMessage(tokens[0], mid);
			}else
				recipientDomains.add(tokens[1]);
		}

		forwardDelete(recipientDomains, String.valueOf(mid), true);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void removeFromUserInbox(String user, long mid, String pwd) {
		Log.info("removeFromUserInbox: Received request to delete message " + String.valueOf(mid) + " from the inbox of " + user);
		
		User u = this.getUserRest(user, pwd);

		if(u == null){
			Log.info("removeFromUserInbox: User with name " + user + " does not exist in the domain.");
			throw new WebApplicationException(Status.FORBIDDEN);
		}
		
		String path = String.format(UserResourceDropbox.USER_MSGS_FILE_FORMAT, 
						DropboxMailServer.hostname, user);

		Set<Long> ids = json.fromJson(DownloadFile.run(path) , HashSet.class);
		
		
		if(!ids.contains(mid)){
			Log.info("removeFromUserInbox: Message not found");
			throw new WebApplicationException(Status.NOT_FOUND);
		}
			
		removeMessage(user, mid);	
	}

	@Override
	public void createInbox(String user, String secret) {

		if(!secret.equals(DropboxMailServer.secret)){
			Log.info("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		String directoryPath = String.format(UserResourceDropbox.USER_MSGS_FILE_FORMAT, DropboxMailServer.hostname, user);
		Set<Long> messageIds = new HashSet<>();

		CreateFile.run(directoryPath, messageIds);
	}

	@Override
	public List<String> postForwardedMessage(Message msg, String secret) {
		Log.info("postForwardedMessage: Received request to save the message " + msg.getId());

		if(!secret.equals(DropboxMailServer.secret)){
			Log.info("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		List<String> failedDeliveries = new LinkedList<>();

		for(String recipient: msg.getDestination()){
			String[] tokens = recipient.split("@");
			if(tokens[1].equals(this.domain) && !this.saveMessage(msg.getSender(), tokens[0], true, msg))
				failedDeliveries.add(recipient);
		}

		Log.info("postForwardedMessage: Couldn't deliver the message to " + failedDeliveries.size() + " people");
		return failedDeliveries;
	}

	@Override
	public void deleteForwardedMessage(long mid, String secret) {
		Log.info("deleteForwardedMessage: Received request to delete message " + mid);

		if(!secret.equals(DropboxMailServer.secret)){
			Log.info("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		Set<String> recipients = null;


		String path = String.format(MESSAGE_FORMAT, 
				DropboxMailServer.hostname, Long.toString(mid));

		Message msg = json.fromJson(DownloadFile.run(path) , Message.class);;
		
		
		if(msg == null)
			return;

		recipients = msg.getDestination();

		Delete.run(path);

		for(String s : recipients){
			String[] tokens = s.split("@");
			if(tokens[1].equals(this.domain)){
				removeMessage(tokens[0],mid);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private void removeMessage(String user, long mid){
		String path = String.format(UserResourceDropbox.USER_MSGS_FILE_FORMAT, 
				DropboxMailServer.hostname, user);

		Set<Long> ids = json.fromJson(DownloadFile.run(path) , HashSet.class);;

		ids.remove(mid);

		CreateFile.run(path,ids);
	}

}