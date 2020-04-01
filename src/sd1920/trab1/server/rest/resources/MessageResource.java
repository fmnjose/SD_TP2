package sd1920.trab1.server.rest.resources;

import java.net.InetAddress;
import java.net.UnknownHostException;
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
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import sd1920.trab1.api.rest.MessageService;
import sd1920.trab1.api.rest.UserService;
import sd1920.trab1.server.rest.MessageServer;
import sd1920.trab1.api.Message;
import sd1920.trab1.api.User;
import sd1920.trab1.api.Discovery.DomainInfo;

@Singleton
public class MessageResource implements MessageService {

	private Random randomNumberGenerator;
	private Client client;
	private ClientConfig config;
	private WebTarget target;
	private String domain;
	private String serverRestUri;
	private final Map<Long, Message> allMessages = new HashMap<Long, Message>();
	private final Map<String, Set<Long>> userInboxs = new HashMap<String, Set<Long>>();

	private static final String ERROR_FORMAT = "FALHA NO ENVIO DE %s PARA %s";
	private static final String SENDER_FORMAT = "%s <%s@%s>";
	private static Logger Log = Logger.getLogger(MessageResource.class.getName());

	public MessageResource() throws UnknownHostException {
		this.randomNumberGenerator = new Random(System.currentTimeMillis());
		this.config = new ClientConfig();
		this.config.property(ClientProperties.CONNECT_TIMEOUT, MessageServer.TIMEOUT);
		this.config.property(ClientProperties.READ_TIMEOUT, MessageServer.TIMEOUT);

		this.client = ClientBuilder.newClient(config);

		this.domain = InetAddress.getLocalHost().getHostName();

		this.serverRestUri = String.format("http://%s:%d/rest",InetAddress.getLocalHost().getHostAddress(),MessageServer.PORT);
	}

	/**
	 * Saves a message in our domain. If the recipient does not exist, adds an error
	 * message
	 * 
	 * @param recipientName name of a recipient in this domain. Always in this domain.
	 * @param mid  id to be assigned
	 */
	private void saveMessage(String senderName, String recipientName, Message msg) {
		synchronized (this.userInboxs) {
			synchronized (this.allMessages) {
				if (!userInboxs.containsKey(recipientName)) {
					Long errorMessageId = Math.abs(randomNumberGenerator.nextLong());
					Message m = new Message(errorMessageId, msg.getSender(), msg.getDestination(),
							String.format(ERROR_FORMAT, msg.getId(), recipientName), msg.getContents());
					
					allMessages.put(errorMessageId, m);
					userInboxs.get(senderName).add(errorMessageId);
				} else
					userInboxs.get(recipientName).add(msg.getId());
			}
		}
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

	
		target = client.target(serverRestUri).path(UserService.PATH);
		target = target.queryParam("pwd", pwd);

		int tries = 0;

		while (error && tries < MessageServer.N_TRIES) {
			error = false;

			try {
				r = target.path(name).request().accept(MediaType.APPLICATION_JSON).get();
			} catch (ProcessingException e) {
				Log.info("Could not communicate with the UserResource. Retrying...");
				try {
					Thread.sleep(MessageServer.SLEEP_TIME);
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

	/**
	 * Forwards a message to the needed domains
	 * 
	 * @param recipientDomains domains to be contacted
	 * @param msg              message to be forwarded
	 */
	private void forwardMessage(Set<String> recipientDomains, Message msg) {

		for (String domain : recipientDomains) {
			boolean error = true;
			DomainInfo info = MessageServer.servers.get(domain);
			if (info != null) {

				Log.info("forwardMessage: Trying to forward message " + msg.getId() + " to " + domain);
				int tries = 0;

				// DUVIDA e necessario enviar pwd?
				target = client.target(info.getUri()).path(MessageService.PATH);

				while (error && tries < MessageServer.N_TRIES) {
					error = false;

					try {
						target.request().accept(MediaType.APPLICATION_JSON)
								.post(Entity.entity(msg, MediaType.APPLICATION_JSON));
					} catch (ProcessingException e) {
						Log.info("forwardMessage: Failed to forward message to " + domain + ". Retrying...");
						try {
							Thread.sleep(MessageServer.SLEEP_TIME);
						} catch (InterruptedException e1) {
							e1.printStackTrace();
						}
						error = true;
					}
				}

				if(error)
					Log.info("forwardMessage: Failed to forward message to " + domain + ". Giving up...");
				else
					Log.info("forwardMessage: Successfully sent message to " + domain + ". Nice!");
			}else{
				Log.info("forwardMessage: " + domain + " does not exist or is offline.");
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
			DomainInfo info = MessageServer.servers.get(domain);

			if(info != null){

				Log.info("Sending delete request to domain: " + domain);
				int tries = 0;

				target = client.target(info.getUri()).path(MessageResource.PATH);

				while(error && tries< MessageServer.N_TRIES){
					error = false;

					try{
						target.path(user).path(mid).request().delete();
					}
					catch(ProcessingException e){
						Log.info("deleteFromDomains: Failed to redirect request to " + domain + ". Retrying...");
						error = true;
					}
				}

				if(error)
					Log.info("deleteFromDomains: Failed to redirect request to " + domain + ". Giving up...");
				else
					Log.info("deleteFromDomains: Successfully redirected request to " + domain + ". More successful than i'll ever be!");		
			}
			else
				Log.info("deleteFromDomains: " + domain + " does not exist or is offline.");
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
			throw new WebApplicationException( Status.CONFLICT );
		}
		
		int nTokens = sender.split(" <").length;
		String senderName = this.getSenderCanonicalName(sender);

		if(nTokens == 1){
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
		}

		for(String recipient: msg.getDestination()){
			String[] tokens = recipient.split("@");
			if(tokens[1].equals(this.domain)){
				saveMessage(senderName,tokens[0], msg);
			}
			else{
				recipientDomains.add(tokens[1]);
			}
		}	

		System.out.println("MESSAGE ID: " + msg.getId());

		if(nTokens == 1)
			this.forwardMessage(recipientDomains, msg);
		//Return the id of the registered message to the client (in the body of a HTTP Response with 200)
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

	/*@Override
	public void checkMessage(long mid) {
		synchronized(this.allMessages){
			if(!this.allMessages.containsKey(mid))
				throw new WebApplicationException(Status.NOT_FOUND);
		}
	}*/

	@Override
	public void deleteMessage(String user, long mid, String pwd) 
	{
		
		Log.info("Received request to delete a message with the id: " + String.valueOf(mid));
		
		User sender = null;

		int nTokens = user.split(" <").length;
		
		Message msg;

		sender  = this.getUser(user, pwd);

		if(sender == null){
			Log.info("Delete message: User not found or wrong password");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		synchronized(this.allMessages){
			msg = this.allMessages.get(mid);
		}

		if(msg == null || !getSenderCanonicalName(msg.getSender()).equals(user))
			return;
		
		if(nTokens == 1){
			synchronized(this.userInboxs){
				this.userInboxs.get(user).remove(mid);
			}
		}

	
		Set<String> recipientDomains = new HashSet<>();
		
		synchronized(this.allMessages){
			allMessages.remove(mid);
		}


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
		
		if(nTokens == 1)
			deleteFromDomains(recipientDomains, 
						String.format(SENDER_FORMAT, sender.getDisplayName(),sender.getName(),sender.getDomain())
						,String.valueOf(mid));
	
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
}
