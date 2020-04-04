package sd1920.trab1.server.soap.resources;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
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

import javax.jws.WebService;
import javax.ws.rs.ProcessingException;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import com.sun.xml.ws.client.BindingProviderProperties;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;

import sd1920.trab1.api.Message;
import sd1920.trab1.api.User;
import sd1920.trab1.api.soap.MessageServiceSoap;
import sd1920.trab1.api.soap.MessagesException;
import sd1920.trab1.api.soap.UserServiceSoap;
import sd1920.trab1.server.soap.SOAPMailServer;

@WebService(serviceName=MessageServiceSoap.NAME, 
	targetNamespace=MessageServiceSoap.NAMESPACE, 
	endpointInterface=MessageServiceSoap.INTERFACE)
public class MessageResourceSoap implements MessageServiceSoap {

	private static final QName MESSAGE_QNAME = new QName(MessageServiceSoap.NAMESPACE, MessageServiceSoap.NAME);
	private static final QName USER_QNAME = new QName(UserServiceSoap.NAMESPACE, UserServiceSoap.NAME);
	private static final String ERROR_FORMAT = "FALHA NO ENVIO DE %s PARA %s";
	private static final String SENDER_FORMAT = "%s <%s@%s>";
	private static final String MESSAGES_WSDL = String.format("/%s/?wsdl", MessageServiceSoap.NAME);
	private static final String USERS_WSDL = String.format("/%s/?wsdl", UserServiceSoap.NAME);

	private Random randomNumberGenerator;
	private String domain;
	private String serverSoapUri;
	private final Map<Long, Message> allMessages = new HashMap<Long, Message>();
	private final Map<String, Set<Long>> userInboxs = new HashMap<String, Set<Long>>();
	private static Logger Log = Logger.getLogger(MessageResourceSoap.class.getName());
	

	public MessageResourceSoap() throws UnknownHostException {
		this.randomNumberGenerator = new Random(System.currentTimeMillis());
		

		this.domain = InetAddress.getLocalHost().getHostName();

		this.serverSoapUri = String.format("http://%s:%d/soap",InetAddress.getLocalHost().getHostAddress(),SOAPMailServer.PORT);
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
	 * message.
	 * 
	 * @param recipientName name of a recipient in this domain. Always in this domain.
	 * @param mid  id to be assigned
	 * 
	 * returns false if it is a forwarded message and the recipient does not exist in the current domain.
	 * true otherwise;
	 */
	private boolean saveMessage(String senderName, String recipientName, boolean forwarded, Message msg) throws MessagesException{
		synchronized (this.userInboxs) {
			synchronized (this.allMessages) {
				if (!userInboxs.containsKey(recipientName)) {
					if(forwarded){
						Log.info("saveMessage: User "+ recipientName+" does not exist");
						return false;
					}else{
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
		User user = null;

		boolean error = true;
		
		int tries = 0;

		UserServiceSoap userService = null;
				
		try {
			Service	service = Service.create(new URL(this.serverSoapUri + USERS_WSDL), USER_QNAME);
			userService = service.getPort(UserServiceSoap.class);							
		}
		catch(MalformedURLException e){
			Log.info("getUser: Bad Url");
			return null;
		} 
		catch(WebServiceException e){
			Log.info("getUser: Failed to forward message to " + domain + ". Retrying...");
			return null;
		}

		((BindingProvider) userService).getRequestContext().put(BindingProviderProperties.CONNECT_TIMEOUT, SOAPMailServer.TIMEOUT);
		((BindingProvider) userService).getRequestContext().put(BindingProviderProperties.REQUEST_TIMEOUT, SOAPMailServer.TIMEOUT);
		

		while (error && tries < SOAPMailServer.N_TRIES) {
			error = false;

			try {
				user = userService.getUser(name, pwd);
			}
			catch( MessagesException me){
				Log.info("getUser: Error, could not send the message. Retrying...");
			}
			catch(WebServiceException wse){
				Log.info("getUser: Communication error. Retrying...");
				wse.printStackTrace();
				try{
					Thread.sleep(SOAPMailServer.SLEEP_TIME);
				}
				catch(InterruptedException e){
					Log.info("getUser: Log a dizer 'what?'");
				}
				error = true;
			}
			tries++;
		}

		if (error) {
			Log.info("GetSender: Exceeded number of tries. Assuming user does not exist...");
			return null;
		}

		return user;
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
			String uri = SOAPMailServer.serverRecord.knownUrisOf(domain);
			if (uri == null){
				Log.info("forwardMessage: " + domain + " does not exist or is offline.");
				continue;
			}

			Log.info("forwardMessage: Trying to forward message " + msg.getId() + " to " + domain);
			int tries = 0;

			MessageServiceSoap msgService = null;
			
			try {
				Service	service = Service.create(new URL(uri + MESSAGES_WSDL), MESSAGE_QNAME);
				msgService = service.getPort(MessageServiceSoap.class);							
			}
			catch(MalformedURLException e){
				Log.info("forwardMessage: Bad Url");
				return;
			} 
			catch(WebServiceException e){
				Log.info("forwardMessage: Failed to forward message to " + domain + ".");
				return;
			}

			((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.CONNECT_TIMEOUT, SOAPMailServer.TIMEOUT);
			((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.REQUEST_TIMEOUT, SOAPMailServer.TIMEOUT);
			
			List<String> failedDeliveries = null;
			
			while (error && tries < SOAPMailServer.N_TRIES) {
				error = false;
				try{
					failedDeliveries = msgService.postForwardedMessage(msg);
				}
				catch(MessagesException me){
					Log.info("forwardMessage: Error, could not send the message.");
				}
				catch(WebServiceException wse){
					Log.info("forwardMessage: Communication error. Retrying...");
					wse.printStackTrace();
					try{
						Thread.sleep(SOAPMailServer.SLEEP_TIME);
					}
					catch(InterruptedException e){
						Log.info("Log a dizer 'what?'");
					}
					error = true;
				}
				
				tries++;
			}

			if(error){
				Log.info("forwardMessage: Failed to forward message to " + domain + ". Giving up...");
				failedDeliveries = new LinkedList<>();
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
	private void deleteFromDomains(Set<String> recipientDomains, String user, long mid){
		for(String domain: recipientDomains){
			boolean error = true;
			String uri = SOAPMailServer.serverRecord.knownUrisOf(domain);
			
			if(uri == null){
				Log.info("deleteFromDomains: " + domain + " does not exist or is offline.");
				continue;
			}
			System.out.println("Sending delete request to domain: " + domain);
			Log.info("Sending delete request to domain: " + domain);
			int tries = 0;

			MessageServiceSoap msgService = null;
			
			try {
				Service	service = Service.create(new URL(uri + MESSAGES_WSDL), MESSAGE_QNAME);
				msgService = service.getPort(MessageServiceSoap.class);							
			}
			catch(MalformedURLException e){
				Log.info("forwardMessage: Bad Url");
				return;
			} 
			catch(WebServiceException e){
				Log.info("forwardMessage: Failed to forward message to " + domain + ".");
				return;
			}

			((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.CONNECT_TIMEOUT, SOAPMailServer.TIMEOUT);
			((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.REQUEST_TIMEOUT, SOAPMailServer.TIMEOUT);
			
			while(error && tries< SOAPMailServer.N_TRIES){
				error = false;
				try{
					msgService.deleteForwardedMessage(mid);
				}
				catch(MessagesException me){
					Log.info("forwardMessage: Error, could not send the message.");
				}
				catch(WebServiceException wse){
					Log.info("forwardMessage: Communication error. Retrying...");
					wse.printStackTrace();
					try{
						Thread.sleep(SOAPMailServer.SLEEP_TIME);
					}
					catch(InterruptedException e){
						Log.info("Log a dizer 'what?'");
					}
					error = true;
				}
				tries++;	
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
	public long postMessage(String pwd, Message msg) throws MessagesException{
		User user;
		String sender = msg.getSender();
		Set<String> recipientDomains = new HashSet<>();

		Log.info("Received request to register a new message (Sender: " + sender + "; Subject: "+msg.getSubject()+")");
		
		if(sender == null || msg.getDestination() == null || msg.getDestination().size() == 0) {
			Log.info("Message was rejected due to lack of recepients.");
			throw new MessagesException("postMessage: Message was rejected due to lack of recepients");
		}
		
		String senderName = this.getSenderCanonicalName(sender);

		
		user = this.getUser(senderName, pwd);

		if(user == null)
			throw new MessagesException("postMessage: Message was rejected due to unexisting user.");

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
			if(tokens[1].equals(this.domain)){
				saveMessage(senderName, tokens[0], false, msg);
			}
			else{
				Log.info("TOKEN: " + tokens[1] + ".");
				recipientDomains.add(tokens[1]);
			}
		}
		
		this.forwardMessage(recipientDomains,msg);
		

		//Return the id of the registered message to the client (in the body of a HTTP Response with 200)
		Log.info("Recorded message with identifier: " + msg.getId());
		return msg.getId();
	
	}

	@Override
	public Message getMessage(String user, String pwd, long mid) throws MessagesException {
	
		
		User u = this.getUser(user, pwd);
		
		if(u == null){
			throw new MessagesException("getMessage: User does not exist or password is incorrect");
		}

		Log.info("Received request for message with id: " + mid +".");
		synchronized(this.allMessages){
			synchronized(this.userInboxs){
				System.out.println(this.allMessages.containsKey(mid));
				System.out.println(this.userInboxs.get(user).contains(mid));
				if(!this.allMessages.containsKey(mid) || !this.userInboxs.get(user).contains(mid)) { //check if message exists
					Log.info("Requested message does not exists.");
					throw new MessagesException("getMessage: Requested message does not exists."); //if not send HTTP 404 back to client
				}
			}
		}
		
		Log.info("Returning requested message to user.");
		return allMessages.get(mid); //Return message to the client with code HTTP 200
	
	}

	@Override
	public List<Long> getMessages(String user, String pwd) throws MessagesException{
		Log.info("Received request for messages with optional user parameter set to: '" + user + "'");
		User u = this.getUser(user, pwd);

		if(u == null){
			Log.info("User with name " + user + " does not exist in the domain.");
			throw new MessagesException("getMessageS: User with name " + user + " does not exist in the domain.");
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
	public void deleteMessage(String user, String pwd, long mid) throws MessagesException{
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
			throw new MessagesException("Delete message: User not found or wrong password");
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
					Log.info("Removing message for user " + u);
				}
			}else
				recipientDomains.add(tokens[1]);
		}
		deleteFromDomains(recipientDomains, sender.getName(), mid);
	
	}

	@Override
	public void removeFromUserInbox(String user, String pwd, long mid) throws MessagesException{
		Log.info("Received request to delete message " + mid + " from the inbox of " + user);
		System.out.println(mid);
		

		User u = this.getUser(user, pwd);

		if(u == null){
			Log.info("User with name " + user + " does not exist in the domain.");
			throw new MessagesException("User with name " + user + " does not exist in the domain.");
		}
		
		synchronized(this.allMessages){
			synchronized(this.userInboxs){
				if(!this.allMessages.containsKey(mid) || !this.userInboxs.get(user).contains(mid)){
					Log.info("Message not found");
					throw new MessagesException("Message not found");
				}
			}
		}
		Log.info("Deleting message from user inbox");
		synchronized(this.userInboxs){
			this.userInboxs.get(user).remove(mid);
		}
	}

	@Override
	public void createInbox(String user) throws MessagesException{
		synchronized(this.userInboxs){
			this.userInboxs.put(user, new HashSet<>());
		}
	}

	@Override
	public List<String> postForwardedMessage(Message msg) throws MessagesException {
		List<String> failedDeliveries = new LinkedList<>();

		Log.info("postForwardedMessage: Received forwarded message from " + msg.getSender() + ". ID: " + msg.getId());

		for(String recipient: msg.getDestination()){
			String[] tokens = recipient.split("@");
			if(tokens[1].equals(this.domain) && !this.saveMessage(msg.getSender(), tokens[0], true, msg))
				failedDeliveries.add(recipient);
		}
		return failedDeliveries;
	}

	@Override
	public void deleteForwardedMessage(long mid) throws MessagesException{
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