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
import javax.ws.rs.PathParam;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.QueryParam;
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
	
	private final Map<Long,Message> allMessages = new HashMap<Long, Message>();
	private final Map<String,Set<Long>> userInboxs = new HashMap<String, Set<Long>>();
	
	private static final int TIMEOUT = 10000;
	private static final int N_TRIES = 5;
	private static final String ERROR_FORMAT = "FALHA NO ENVIO DE %s PARA %s";
	private static final String SENDER_FORMAT = "%s <%@%>";
	private static Logger Log = Logger.getLogger(MessageResource.class.getName());
	

	public MessageResource() throws UnknownHostException {
		this.randomNumberGenerator = new Random(System.currentTimeMillis());
		config = new ClientConfig();
		config.property(ClientProperties.CONNECT_TIMEOUT, TIMEOUT);
		config.property(ClientProperties.READ_TIMEOUT, TIMEOUT);
		
		client = ClientBuilder.newClient(config);

		domain = InetAddress.getLocalHost().getHostName();
		
	}
	
	/**
	 * Saves a message in our domain. If the recipient does not exist, adds an error message
	 * @param name name of a recipient in this domain. Always in this domain.
	 * @param mid id to be assigned
	 */
	private void saveMessage(String name, Message msg){
		synchronized(this.userInboxs){
			synchronized(this.allMessages){
				//DUVIDA perguntar à estrutura local ou à Resource?
				if(!userInboxs.containsKey(name)){
					Long errorMessageId = Math.abs(randomNumberGenerator.nextLong());
					Message m = new Message(errorMessageId, msg.getSender(), msg.getDestination(),
						String.format(ERROR_FORMAT, msg.getId()),
						msg.getContents());
					allMessages.put(errorMessageId, m);
					userInboxs.get(name).add(errorMessageId);
				}else
					userInboxs.get(name).add(msg.getId());
			}
		}
	}
	
	/**
	 * Fetches a sender of a message from the UserResource
	 * @param name name of the sender. Without the @
	 * @param pwd password
	 * @return the User Object corresponding to the sender. Null if none is found
	 * @throws UnknownHostException can't compile if this isn't declared...
	 */
	private User getUser(String name, String pwd) throws UnknownHostException {
		Response r = null;;
		String[] tokens = name.split("@");
		boolean error = true;

		target = client.target(InetAddress.getLocalHost().getHostAddress()).path(UserResource.PATH);
		target = target.queryParam("pwd", pwd);

		int tries = 0;

		while(error && tries < N_TRIES){
			error = false;

			try{
				r = target.path(tokens[0]).request().get();
			}catch(ProcessingException e){
				Log.info("Could not communicate with the UserResource. Retrying...");
				error = true;
			}

			tries ++;
		}

		if(error){
			Log.info("GetSender: Exceeded number of tries. Assuming user does not exist...");
			return null;
		}

		if(r.getStatus() == Status.FORBIDDEN.getStatusCode()){
			Log.info("GetSender: User either doesn't exist or the password is incorrect");
			return null;
		}

		return (User)r.getEntity();
	}

	/**
	 * Forwards a message to the needed domains
	 * @param recipientDomains domains to be contacted
	 * @param msg message to be forwarded
	 */
	private void forwardMessage(Set<String> recipientDomains, Message msg) {

		for(String domain : recipientDomains){
			boolean error = true;
			DomainInfo info = MessageServer.servers.get(domain);
			if(info != null){

				Log.info("forwardMessage: Trying to forward message " + msg.getId()+ " to " + domain);
				int tries = 0;

				//DUVIDA e necessario enviar pwd?
				target = client.target(info.getUri()).path( MessageService.PATH );
		
				while(error && tries < N_TRIES){
					error = false;

					try{
						target.request()
							.accept(MediaType.APPLICATION_JSON)
							.post(Entity.entity(msg, MediaType.APPLICATION_JSON));
					}catch(ProcessingException e){
						Log.info("forwardMessage: Failed to forward message to " + domain + ". Retrying...");
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

				while(error && tries< N_TRIES){
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

	@Override
	public long postMessage(@QueryParam("pwd") String pwd, Message msg) {
		System.out.println("ANTES BEANS");
		String sender = msg.getSender();
		System.out.println("DEPOIS BEANS");

		Set<String> recipientDomains = new HashSet<>();

		Log.info("Received request to register a new message (Sender: " + sender + "; Subject: "+msg.getSubject()+")");
		
		//Check if message is valid, if not return HTTP CONFLICT (409)
		if(sender == null || msg.getDestination() == null || msg.getDestination().size() == 0) {
			Log.info("Message was rejected due to lack of recepients.");
			throw new WebApplicationException( Status.CONFLICT );
		}
		
		String[] tokens = sender.split(" <");
		int nTokens = tokens.length;
		try{
			if(nTokens == 1){
				User user = this.getUser(tokens[0], pwd);

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
			}

			for(String recipient: msg.getDestination()){
				tokens = recipient.split("@");
				if(tokens[1].equals(this.domain))
					saveMessage(tokens[0], msg);
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
		catch(UnknownHostException e){
			Log.info("What?");
			return -1;
		}
	}

	@Override
	public Message getMessage(@PathParam("user") String user, @PathParam("mid") long mid,
    @QueryParam("pwd") String pwd) {
	
		try{
			User u = this.getUser(user, pwd);
			
			if(u == null){
				throw new WebApplicationException(Status.FORBIDDEN);
			}

			Log.info("Received request for message with id: " + mid +".");
			synchronized(this.allMessages){
				if(!allMessages.containsKey(mid)) { //check if message exists
					Log.info("Requested message does not exists.");
					throw new WebApplicationException( Status.NOT_FOUND ); //if not send HTTP 404 back to client
				}
			}
			
			Log.info("Returning requested message to user.");
			return allMessages.get(mid); //Return message to the client with code HTTP 200
		}
		catch(UnknownHostException e){
			System.out.println("wtf");
			return null;
		}
	}

	@Override
	public List<Long> getMessages(@PathParam("user") String user, @QueryParam("pwd") String pwd) {
		Log.info("Received request for messages with optional user parameter set to: '" + user + "'");
		Set<Long> mids = new HashSet<Long>();
		if(user == null) {
			Log.info("Collecting all messages in server");
			mids.addAll(allMessages.keySet());
		} else {
			Log.info("Collecting all messages in server for user " + user);
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
	public void deleteMessage(@PathParam("user") String user, @PathParam("mid") long mid, 
								@QueryParam("pwd") String pwd) 
	{
		
		Log.info("Received request to delete a message with the id: " + String.valueOf(mid));
		
		User sender = null;
		String[] tokens = user.split(" <");
		int nTokens = tokens.length;
		try {
			Message msg;
			synchronized(this.allMessages){
				msg = this.allMessages.get(mid);
			}
			
			if(msg != null){
				if(nTokens == 1){
					sender = this.getUser(user, pwd);
					
					if(sender == null)
						throw new WebApplicationException(Status.FORBIDDEN);
					
					synchronized(this.userInboxs){
						this.userInboxs.get(user).remove(mid);
					}
				}

			
				Set<String> recipientDomains = new HashSet<>();
				
				synchronized(this.allMessages){
					allMessages.remove(mid);
				}

				Set<String> users = msg.getDestination();

				for(String u : users){
					tokens = u.split("@");
					if(tokens[1].equals(this.domain)){
						synchronized(this.userInboxs){
							if(userInboxs.containsKey(tokens[0])) userInboxs.get(tokens[0]).remove(mid);
							Log.info("Removing message for user " + u);
						}
					}
					else
						recipientDomains.add(tokens[1]);
				}
				
				if(nTokens == 1)
					deleteFromDomains(recipientDomains, 
								String.format(SENDER_FORMAT, sender.getDisplayName(),sender.getName(),sender.getDomain())
								,String.valueOf(mid));
			}
		} catch (UnknownHostException e) {
			//DO something
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
