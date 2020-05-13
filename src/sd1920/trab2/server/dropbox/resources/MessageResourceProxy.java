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
import sd1920.trab2.api.proxy.UserProxy;
import sd1920.trab2.api.rest.MessageServiceRest;
import sd1920.trab2.server.dropbox.ProxyMailServer;
import sd1920.trab2.server.dropbox.requests.CreateFile;
import sd1920.trab2.server.dropbox.requests.Delete;
import sd1920.trab2.server.dropbox.requests.DownloadFile;
import sd1920.trab2.server.rest.RESTMailServer;
import sd1920.trab2.server.serverUtils.DropboxServerUtils;

public class MessageResourceProxy extends DropboxServerUtils implements MessageServiceRest {

	public static final String MESSAGES_DIR_FORMAT = "/%s/messages";
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

		System.out.println("postMessage: Received request to register a new message (Sender: " + sender + "; Subject: "+msg.getSubject()+")");
		
		if(sender == null || msg.getDestination() == null || msg.getDestination().size() == 0) {
			System.out.println("postMessage: Message was rejected due to lack of recepients.");
			throw new WebApplicationException(Status.CONFLICT );
		}

		String senderName = getSenderCanonicalName(sender);


		long curr = System.currentTimeMillis();
		user = this.getUserRest(senderName, pwd);

		if(user == null)
			throw new WebApplicationException(Status.FORBIDDEN);

		long newID = Math.abs(randomNumberGenerator.nextLong());

		newID = Math.abs(randomNumberGenerator.nextLong());
		
		msg.setSender(String.format(SENDER_FORMAT, user.getDisplayName(), user.getName(), user.getDomain()));
		msg.setId(newID);	
		
		String path = String.format(MESSAGE_FORMAT, ProxyMailServer.hostname, Long.toString(newID));

		CreateFile.run(path, msg);

		System.out.println("postMessage: Created new message with id: " + newID);

		for(String recipient: msg.getDestination()){
			String[] tokens = recipient.split("@");
			if(tokens[1].equals(this.domain))
				saveMessage(senderName,recipient, false, msg);
			else
				recipientDomains.add(tokens[1]);
		}	

		this.forwardMessage(recipientDomains, msg, true);

		System.out.println("Time elapsed PostMessage: " + (System.currentTimeMillis() - curr));

		return msg.getId();
	}

	@Override
	public Message getMessage(String user, long mid, String pwd) {
		System.out.println("getMessage: Received request for message with id: " + mid +".");
		
		UserProxy u = this.getUserProxy(user);
		
		if(u == null){
			System.out.println("getMessage: User does not exist");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		if(!u.getUser().getPwd().equals(pwd)){
			System.out.println("getMessage: Invalid Password");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		
		if(!u.getMids().contains(mid)){
			System.out.println("getMessage: Requested message does not exist.");
			throw new WebApplicationException( Status.NOT_FOUND );
		}

		String path = String.format(MESSAGE_FORMAT, ProxyMailServer.hostname, Long.toString(mid));

		String messageString = DownloadFile.run(path);

		Message message = json.fromJson(messageString , Message.class);
		
		return message; 
	}

	@Override
	public List<Long> getMessages(String user, String pwd) {
		System.out.println("getMessages: Received request for messages to '" + user + "'");

		
		UserProxy u = this.getUserProxy(user);
		
		if(u == null){
			System.out.println("getMessages: User does not exist");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		if(!u.getUser().getPwd().equals(pwd)){
			System.out.println("getMessages: Invalid Password");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		System.out.println("getMessages: Returning message list to user with " + u.getMids().size() + " messages.");
		return new ArrayList<>(u.getMids());
	}

	@Override
	public void deleteMessage(String user, long mid, String pwd) {
		System.out.println("deleteMessage: Received request to delete a message with the id: " + String.valueOf(mid));
		
		String senderName = getSenderCanonicalName(user);
		
		UserProxy sender = this.getUserProxy(senderName);
				
		if(sender == null){
			System.out.println("getMessage: User does not exist");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		if(!sender.getUser().getPwd().equals(pwd)){
			System.out.println("getMessage: Invalid Password");
			throw new WebApplicationException(Status.FORBIDDEN);
		}
		
		String path = String.format(UserResourceProxy.USER_DATA_FORMAT, 
					ProxyMailServer.hostname, Long.toString(mid));

		sender.getMids().remove(mid);

		CreateFile.run(path, sender);
	
		removeMessageFromInbox(senderName, mid);

		path = String.format(MESSAGE_FORMAT, ProxyMailServer.hostname, Long.toString(mid));

		String messageString = DownloadFile.run(path);

		if(messageString == null){
			System.out.println("deleteMessage: Message does not exist");
			return;
		}

		Message msg = json.fromJson(messageString, Message.class);

		if(!getSenderCanonicalName(msg.getSender()).equals(senderName)){
			System.out.println("deleteMessage: Sender mismatch");
			return;
		}

		//this will be used to compile the domains that we'll need to forward the request to
		Set<String> recipientDomains = new HashSet<>();

		for(String u : msg.getDestination()){
			String[] tokens = u.split("@");
			if(tokens[1].equals(this.domain)){				
				removeMessageFromInbox(tokens[0], mid);
			}else
				recipientDomains.add(tokens[1]);
		}

		forwardDelete(recipientDomains, String.valueOf(mid), true);
	}

	@Override
	public void removeFromUserInbox(String user, long mid, String pwd) {
		System.out.println("removeFromUserInbox: Received request to delete message " + String.valueOf(mid) + " from the inbox of " + user);
		
		String name = getSenderCanonicalName(user);

		UserProxy u = this.getUserProxy(name);

		if(u == null || !u.getUser().getPwd().equals(pwd)){
			System.out.println("removeFromUserInbox: User does not exist or invalid Password");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		if(u.getMids().contains(mid)){
			System.out.println("removeFromUSerInbox: Message does not exist");
			throw new WebApplicationException(Status.NOT_FOUND);
		}

		u.getMids().remove(mid);

		String path = String.format(UserResourceProxy.USER_DATA_FORMAT, 
									ProxyMailServer.hostname, name);

		CreateFile.run(path, u);
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

		if(!secret.equals(ProxyMailServer.secret)){
			System.out.println("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		Set<String> recipients = null;


		String path = String.format(MESSAGE_FORMAT, 
				ProxyMailServer.hostname, Long.toString(mid));

		String messageString = DownloadFile.run(path);

		if(messageString == null){
			System.out.println("deleteForwardedMessage: Message does not exist");
			return;
		}

		Message msg = json.fromJson(messageString , Message.class);;

		recipients = msg.getDestination();

		Delete.run(path);

		for(String s : recipients){
			String[] tokens = s.split("@");
			if(tokens[1].equals(this.domain)){
				removeMessageFromInbox(tokens[0],mid);
			}
		}
	}
	
	private void removeMessageFromInbox(String user, long mid){
		String path = String.format(UserResourceProxy.USER_DATA_FORMAT, 
				ProxyMailServer.hostname, user);

		UserProxy u = this.getUserProxy(user);

		u.getMids().remove(mid);

		CreateFile.run(path, u);
	}

}