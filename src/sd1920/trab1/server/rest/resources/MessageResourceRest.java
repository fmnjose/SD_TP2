package sd1920.trab1.server.rest.resources;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import javax.inject.Singleton;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import sd1920.trab1.api.rest.MessageServiceRest;
import sd1920.trab1.api.rest.UserServiceRest;
import sd1920.trab1.server.rest.RESTMailServer;
import sd1920.trab1.api.Message;
import sd1920.trab1.api.User;

@Singleton
public class MessageResourceRest implements MessageServiceRest {

	private Random randomNumberGenerator;
	private Client client;
	private ClientConfig config;
	private String domain;
	private String serverRestUri;
	private final Map<Long, Message> allMessages = new HashMap<Long, Message>();
	private final Map<String, Set<Long>> userInboxs = new HashMap<String, Set<Long>>();

	private static final String ERROR_FORMAT = "FALHA NO ENVIO DE %s PARA %s";
	private static final String SENDER_FORMAT = "%s <%s@%s>";
	private static Logger Log = Logger.getLogger(MessageResourceRest.class.getName());

	public MessageResourceRest() throws UnknownHostException {
		this.randomNumberGenerator = new Random(System.currentTimeMillis());
		this.config = new ClientConfig();
		this.config.property(ClientProperties.CONNECT_TIMEOUT, RESTMailServer.TIMEOUT);
		this.config.property(ClientProperties.READ_TIMEOUT, RESTMailServer.TIMEOUT);

		this.client = ClientBuilder.newClient(config);

		this.domain = InetAddress.getLocalHost().getHostName();

		this.serverRestUri = String.format("http://%s:%d/rest",InetAddress.getLocalHost().getHostAddress(),RESTMailServer.PORT);
	}

	/**
	 * Inserts error message into the sender inbox
	 * @param senderName
	 * @param recipientName
	 * @param msg
	 */
	private void saveErrorMessages(String senderName, String recipientName,Message msg){
		Long errorMessageId = Math.abs(randomNumberGenerator.nextLong());
		Message m = new Message(errorMessageId, msg.getSender(), msg.getDestination(),
				String.format(ERROR_FORMAT, msg.getId(), recipientName), msg.getContents());
		
		this.allMessages.put(errorMessageId, m);
		this.userInboxs.get(senderName).add(errorMessageId);
	}

	/**
	 * Saves a message in our domain. If the recipient does not exist, adds an error
	 * message
	 * 
	 * @param senderName
	 * @param recipientName name of a recipient in this domain. Always in this domain.
	 * @param forwarded
	 * @param mid  id to be assigned
	 */
	private boolean saveMessage(String senderName, String recipientName, boolean forwarded,Message msg) {
		synchronized (this.userInboxs) {
			synchronized (this.allMessages) {
				if (!userInboxs.containsKey(recipientName)) {
					if(forwarded)
						return false;
					else{
						this.saveErrorMessages(senderName, recipientName, msg);
					}
				} else{
					this.allMessages.put(msg.getId(), msg);
					this.userInboxs.get(recipientName).add(msg.getId());
				}
			}
		}
		return true;
	}

	/**
	 * Fetches a sender of a message from the UserResource
	 * 
	 * @param name name of the sender. Without the @
	 * @param pwd  password
	 * @return the User Object corresponding to the sender. Null if none is found
	 * @throws UnknownHostException can't compile if this isn't declared...
	 */
	private User getUser(String name, String pwd){
		Response r = null;
		
		boolean error = true;

		
		WebTarget target = client.target(serverRestUri).path(UserServiceRest.PATH);
		target = target.queryParam("pwd", pwd);

		int tries = 0;

		while (error && tries < RESTMailServer.N_TRIES) {
			error = false;

			try {
				r = target.path(name).request().accept(MediaType.APPLICATION_JSON).get();
			} catch (ProcessingException e) {
				Log.info("Could not communicate with the UserResource. Retrying...");
				try {
					Thread.sleep(RESTMailServer.SLEEP_TIME);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
				error = true;
			}
			tries++;
		}

		if (error) {
			Log.info("GetSender: Exceeded number of tries. Assuming user does not exist...");
			return null;
		}

		if (r.getStatus() == Status.FORBIDDEN.getStatusCode()) {
			Log.info("GetSender: User either doesn't exist or the password is incorrect");
			return null;
		}

		return r.readEntity(User.class);
	}

	
	private void forwardMessage(Set<String> recipientDomains, Message msg) {

		Response r = null; 

		for (String domain : recipientDomains) {
			boolean error = true;
			String uri = RESTMailServer.serverRecord.knownUrisOf(domain);
			if (uri == null){
				Log.info("forwardMessage: " + domain + " does not exist or is offline.");
				continue;
			}

			Log.info("forwardMessage: Trying to forward message " + msg.getId() + " to " + domain);
			int tries = 0;

			WebTarget target = client.target(uri).path(MessageServiceRest.PATH).path("mbox");

			while (error && tries < RESTMailServer.N_TRIES) {
				error = false;

				try {
					r = target.request().accept(MediaType.APPLICATION_JSON)
							.post(Entity.entity(msg, MediaType.APPLICATION_JSON));
				} catch (ProcessingException e) {
					Log.info("forwardMessage: Failed to forward message to " + domain + ". Retrying...");
					try {
						Thread.sleep(RESTMailServer.SLEEP_TIME);
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
					error = true;
				}
				
			}
			
			 List<String> failedDeliveries = error ? new LinkedList<>() 
			 				: r.readEntity(new GenericType<List<String>>(){});
			
			if(error){
				Log.info("forwardMessage: Failed to forward message to " + domain + ". Giving up...");
				for(String recipient: msg.getDestination()){
					if(recipient.split("@")[1].equals(domain))
						failedDeliveries.add(recipient);
				}
			}
			else
				Log.info("forwardMessage: Successfully sent message to " + domain + ". Nice!");
			
			
			String senderName = this.getSenderCanonicalName(msg.getSender());
			for(String recipient: failedDeliveries){
				this.saveErrorMessages(senderName, recipient, msg);
			}
		}
	}

	/**
	 * When receiving a delete request for a mid, this function is used
	 * to redirect the request to the domains containing the recipients
	 * of the message
	 * @param recipientDomains
	 * @param mid
	 */
	private void deleteFromDomains(Set<String> recipientDomains, String user, String mid) {
		for(String domain: recipientDomains){
			boolean error = true;
			String uri = RESTMailServer.serverRecord.knownUrisOf(domain);
			
			if(uri == null){
				Log.info("deleteFromDomains: " + domain + " does not exist or is offline.");
				continue;
			}

			System.out.println("Sending delete request to domain: " + domain);
			Log.info("Sending delete request to domain: " + domain);
			int tries = 0;

			WebTarget target = client.target(uri).path(MessageResourceRest.PATH).path("msg");

			while(error && tries< RESTMailServer.N_TRIES){
				error = false;

				try{
					target.path(mid).request().delete();
				}
				catch(ProcessingException e){
					System.out.println("deleteFromDomains: Failed to redirect request to " + domain + ". Retrying...");
					Log.info("deleteFromDomains: Failed to redirect request to " + domain + ". Retrying...");
					error = true;
				}
			}

			if(error)
				Log.info("deleteFromDomains: Failed to redirect request to " + domain + ". Giving up...");
			else
				Log.info("deleteFromDomains: Successfully redirected request to " + domain + ". More successful than i'll ever be!");		
		}	
	}

	private String getSenderCanonicalName(String senderName){
		String[] tokens = senderName.split(" <");
		int nTokens = tokens.length;

		if(nTokens == 2){
			tokens = tokens[1].split("@");
		}else
			tokens = tokens[0].split("@");
		
		return tokens[0];
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
		String senderName = this.getSenderCanonicalName(sender);

		user = this.getUser(senderName, pwd);

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
		System.out.println("Created new message with id: " + newID);
		Log.info("Created new message with id: " + newID);

		for(String recipient: msg.getDestination()){
			String[] tokens = recipient.split("@");
			if(tokens[1].equals(this.domain))
				saveMessage(senderName,tokens[0], false, msg);
			else
				recipientDomains.add(tokens[1]);
		}	

		this.forwardMessage(recipientDomains, msg);

		Log.info("Recorded message with identifier: " + msg.getId());
		return msg.getId();
	}

	@Override
	public Message getMessage(String user, long mid, String pwd) {
	
		
		User u = this.getUser(user, pwd);
		
		if(u == null){
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		Log.info("Received request for message with id: " + mid +".");
		synchronized(this.allMessages){
			synchronized(this.userInboxs){
				System.out.println(this.allMessages.containsKey(mid));
				System.out.println(this.userInboxs.get(user).contains(mid));
				if(!this.allMessages.containsKey(mid) || !this.userInboxs.get(user).contains(mid)) { //check if message exists
					System.out.println("BOM DIA, ENTREI NO IF");
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
		User u = this.getUser(user, pwd);

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
		System.out.println("Received request to delete a message with the id: " + String.valueOf(mid));
		Log.info("Received request to delete a message with the id: " + String.valueOf(mid));
		
		User sender = null;
		
		Message msg;
		
		synchronized(this.allMessages){
			msg = this.allMessages.get(mid);
		}

		
		sender  = this.getUser(user, pwd);
		
		if(sender == null){
			Log.info("Delete message: User not found or wrong password");
			throw new WebApplicationException(Status.FORBIDDEN);
		}
		
		synchronized(this.userInboxs){
			this.userInboxs.get(user).remove(mid);
		}

		String userName = getSenderCanonicalName(user);

		if(msg == null || !getSenderCanonicalName(msg.getSender()).equals(userName))
			return;

		Set<String> recipientDomains = new HashSet<>();

		for(String u : msg.getDestination()){
			String[] tokens = u.split("@");
			if(tokens[1].equals(this.domain)){
				synchronized(this.userInboxs){
					userInboxs.get(tokens[0]).remove(mid);
					System.out.println("Removing message for user " + u);
					
					Log.info("Removing message for user " + u);
				}
			}else
				recipientDomains.add(tokens[1]);
		}
		
		deleteFromDomains(recipientDomains, sender.getName(),String.valueOf(mid));
	
	}

	@Override
	public void removeFromUserInbox(String user, long mid, String pwd) {
		Log.info("Received request to delete message " + String.valueOf(mid) + " from the inbox of " + user);
		
		User u = this.getUser(user, pwd);

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
					System.out.println("Removing message for user " + s);
					
					Log.info("Removing message for user " + s);
				}
			}
		}
	}
}
