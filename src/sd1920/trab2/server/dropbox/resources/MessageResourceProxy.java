package sd1920.trab2.server.dropbox.resources;

import java.net.InetAddress;
import java.net.UnknownHostException;
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
import sd1920.trab2.server.dropbox.ProxyMailServer;
import sd1920.trab2.server.dropbox.requests.Create;
import sd1920.trab2.server.dropbox.requests.Delete;
import sd1920.trab2.server.dropbox.requests.DownloadFile;
import sd1920.trab2.server.dropbox.requests.GetMeta;
import sd1920.trab2.server.dropbox.requests.ListDirectory;
import sd1920.trab2.server.rest.RESTMailServer;
import sd1920.trab2.server.serverUtils.DropboxServerUtils;

public class MessageResourceProxy extends DropboxServerUtils implements MessageServiceRest {

	public static final String MESSAGES_DIR_FORMAT = "/%s/messages";
	public static final String USER_INBOX_FORMAT = "/%s/users/%s%s";
	public static final String MESSAGE_FORMAT = "/%s/messages/%s";
	private static Gson json = new Gson();

	public MessageResourceProxy() throws UnknownHostException {
		super(ProxyMailServer.secret);

		this.randomNumberGenerator = new Random(System.currentTimeMillis());
		
		Log = Logger.getLogger(ProxyMailServer.class.getName());

		this.domain = InetAddress.getLocalHost().getHostName();

		this.serverUri = String.format(DOMAIN_FORMAT_REST, InetAddress.getLocalHost().getHostAddress(), RESTMailServer.PORT);
	}

	

	@Override
	public long postMessage(String pwd, Message msg) {
		User user;
		String sender = msg.getSender();
		Set<String> recipientDomains = new HashSet<>();
		Set<String> recipients = new HashSet<>();

		System.out.println("postMessage: Received request to register a new message (Sender: " + sender + "; Subject: "+msg.getSubject()+")");
		
		if(sender == null || msg.getDestination() == null || msg.getDestination().size() == 0) {
			System.out.println("postMessage: Message was rejected due to lack of recepients.");
			throw new WebApplicationException(Status.CONFLICT );
		}

		String senderName = getSenderCanonicalName(sender);


		long curr = System.currentTimeMillis();

		//User treatment
		user = this.getUserRest(senderName, pwd);

		if(user == null)
			throw new WebApplicationException(Status.FORBIDDEN);

		//Message Treatment
		long newID = Math.abs(randomNumberGenerator.nextLong());

		newID = Math.abs(randomNumberGenerator.nextLong());
		
		msg.setSender(String.format(SENDER_FORMAT, user.getDisplayName(), user.getName(), user.getDomain()));
		msg.setId(newID);	

		//Upload the msg
		String path = String.format(MESSAGE_FORMAT, ProxyMailServer.hostname, Long.toString(newID));

		Create.run(path, msg);

		System.out.println("postMessage: Created new message with id: " + newID);

		for(String recipient: msg.getDestination()){
			String[] tokens = recipient.split("@");
			if(tokens[1].equals(this.domain))
				recipients.add(tokens[0]);
			else
				recipientDomains.add(tokens[1]);
		}	

		this.saveMessage(recipients, msg, false);

		this.forwardMessage(recipientDomains, msg, ServerTypes.PROXY);

		System.out.println("Time elapsed PostMessage: " + (System.currentTimeMillis() - curr));

		return msg.getId();
	}

	@Override
	public Message getMessage(String user, long mid, String pwd) {
		System.out.println("getMessage: Received request for message with id: " + mid +".");

		String userName = getSenderCanonicalName(user); 
		
		User u = this.getUserRest(user, pwd);
		
		if(u == null){
			System.out.println("getMessage: User does not exist");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		if(!u.getPwd().equals(pwd)){
			System.out.println("getMessage: Invalid Password");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		String path = String.format(UserResourceProxy.USER_INBOX_FOLDER, ProxyMailServer.hostname, userName);

		List<String> mids = ListDirectory.run(path);
		
		if(mids.contains(Long.toString(mid))){
			System.out.println("getMessage: Requested message does not exist.");
			throw new WebApplicationException( Status.NOT_FOUND );
		}

		path = String.format(MESSAGE_FORMAT, ProxyMailServer.hostname, Long.toString(mid));

		String messageString = DownloadFile.run(path);

		Message message = json.fromJson(messageString , Message.class);
		
		return message; 
	}

	@Override
	public List<Long> getMessages(String user, String pwd) {
		System.out.println("getMessages: Received request for messages to '" + user + "'");

		
		String userName = getSenderCanonicalName(user);

		User u = this.getUserRest(userName, pwd);
		
		if(u == null){
			System.out.println("getMessages: User does not exist");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		String path = String.format(UserResourceProxy.USER_INBOX_FOLDER, ProxyMailServer.hostname, userName);

		List<String> mids = ListDirectory.run(path);

		List<Long> messages = new LinkedList<>();

		for(String mid : mids)
			messages.add(Long.valueOf(mid));

		System.out.println("getMessages: Returning message list to user with " + mids.size() + " messages.");
		return messages;
	}

	@Override
	public void deleteMessage(String user, long mid, String pwd) {
		System.out.println("deleteMessage: Received request to delete a message with the id: " + String.valueOf(mid));
		
		String senderName = getSenderCanonicalName(user);
		
		User sender = this.getUserRest(senderName, pwd);
				
		if(sender == null){
			System.out.println("deleteMessage: User does not exist");
			throw new WebApplicationException(Status.FORBIDDEN);
		}
		
		String path = String.format(MESSAGE_FORMAT, ProxyMailServer.hostname, Long.toString(mid));

		String messageString = DownloadFile.run(path);

		if(messageString == null){
			System.out.println("deleteMessage: Message does not exist");
			return;
		}

		Message msg = json.fromJson(messageString, Message.class);

		if(getSenderCanonicalName(msg.getSender()).equals(senderName)){
			System.out.println("deleteMessage: Sender mismatch");
			return;
		}

		//List of deletions to be made
		List<String> deletePaths = new LinkedList<>();

		//delete the message in the general messages
		deletePaths.add(path);

		Set<String> forwardDomains = new HashSet<>();

		for(String recipient : msg.getDestination()){
			String[] tokens = recipient.split("@");
			if(tokens[1].equals(this.domain))
				deletePaths.add(String.format(UserResourceProxy.USER_MESSAGE_FORMAT, ProxyMailServer.hostname, tokens[0], mid));
			else
				forwardDomains.add(tokens[1]);
		}

		//Dropbox handles the async request
		Delete.run(deletePaths);

		this.forwardDelete(forwardDomains, mid, ServerTypes.PROXY);
	}

	@Override
	public void removeFromUserInbox(String user, long mid, String pwd) {
		System.out.println("removeFromUserInbox: Received request to delete message " + String.valueOf(mid) + " from the inbox of " + user);
		
		String name = getSenderCanonicalName(user);

		User u = this.getUserRest(name, pwd);

		if(u == null || !u.getPwd().equals(pwd)){
			System.out.println("removeFromUserInbox: User does not exist or invalid Password");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		String path = String.format(UserResourceProxy.USER_MESSAGE_FORMAT, ProxyMailServer.hostname, name, mid);

		if(!GetMeta.run(path)){
			System.out.println("removeFromUserInbox: Message not found");
			throw new WebApplicationException(Status.NOT_FOUND);
		}

		Delete.run(path);
		System.out.println("removeFromUserInbox: Successful");
	}

	@Override
	public void createInbox(String user, String secret) {
		//Deprecated
	}

	@Override
	public List<String> postForwardedMessage(Message msg, String secret) {
		System.out.println("postForwardedMessage: Received request to save the message " + msg.getId());

		if(!secret.equals(ProxyMailServer.secret)){
			System.out.println("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		//Create the message in this domain
		String path = String.format(MESSAGE_FORMAT, ProxyMailServer.hostname, Long.toString(msg.getId()));

		Create.run(path, msg);

		System.out.println("posForwardedMessage: Saved message with ID: " + Long.toString(msg.getId()));

		Set<String> recipients = new HashSet<>();

		List<String> failedDeliveries = new LinkedList<>();

		//Iterates the recipients in this domain, if they exist, add message to inbox, if not, add to failed deliveries
		for(String recipient: msg.getDestination()){
			String[] tokens = recipient.split("@");
			if(tokens[1].equals(this.domain)){
				path = String.format(UserResourceProxy.USER_FOLDER_FORMAT, ProxyMailServer.hostname, tokens[0]);
				if(GetMeta.run(path))
					recipients.add(tokens[0]);
				else
					failedDeliveries.add(tokens[0]);
			}

		}	

		this.saveMessage(recipients, msg, false);

		System.out.println("postForwardedMessage: Couldn't deliver the message to " + failedDeliveries.size() + " people");
		
		return failedDeliveries;
	}

	@Override
	public void deleteForwardedMessage(long mid, String secret) {
		System.out.println("deleteForwardedMessage: Received request to delete message " + mid);

		if(!secret.equals(ProxyMailServer.secret)){
			System.out.println("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		//Downloads Message to check it
		String path = String.format(MESSAGE_FORMAT, ProxyMailServer.hostname, Long.toString(mid));

		String messageString = DownloadFile.run(path);

		if(messageString == null){
			System.out.println("deleteForwardedMessage: Message does not exist");
			return;
		}

		Message msg = json.fromJson(messageString, Message.class);

		//DeletesMessage
		Delete.run(path);

		System.out.println("deleteForwardedMessage: Deleted message with ID: " + Long.toString(mid));

		List<String> deletePaths = new LinkedList<>();

		//Iterates the recipients in this domain, if they exist, add message to inbox, if not, add to failed deliveries
		for(String recipient: msg.getDestination()){
			String[] tokens = recipient.split("@");
			if(tokens[1].equals(this.domain)){
				path = String.format(UserResourceProxy.USER_MESSAGE_FORMAT, ProxyMailServer.hostname, tokens[0], Long.toString(mid));
				deletePaths.add(path);
			}
		}	

		Delete.run(deletePaths);
		System.out.println("deleteForwardedMessage: Deletion request sent");
	}
}