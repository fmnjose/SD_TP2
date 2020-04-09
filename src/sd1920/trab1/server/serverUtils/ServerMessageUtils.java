package sd1920.trab1.server.serverUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import sd1920.trab1.api.Message;
import sd1920.trab1.api.User;
import sd1920.trab1.api.Discovery.DomainInfo;
import sd1920.trab1.api.rest.UserServiceRest;
import sd1920.trab1.api.soap.UserServiceSoap;
import sd1920.trab1.server.rest.RESTMailServer;

import java.net.UnknownHostException;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import com.sun.xml.ws.client.BindingProviderProperties;

import sd1920.trab1.api.soap.MessageServiceSoap;
import sd1920.trab1.api.soap.MessagesException;
import sd1920.trab1.server.soap.SOAPMailServer;

public abstract class ServerMessageUtils {
    
    protected Random randomNumberGenerator;
    protected Client client;
    protected ClientConfig config;
    protected String domain;
    protected String serverUri;
    protected static Logger Log;
    protected final Map<Long, Message> allMessages = new HashMap<Long, Message>();
    protected final Map<String, Set<Long>> userInboxs = new HashMap<String, Set<Long>>();
    protected final Map<String, RequestHandler> requests = new HashMap<>();

    public static final String DOMAIN_FORMAT_REST = "http://%s:%d/rest";
    public static final String DOMAIN_FORMAT_SOAP = "http://%s:%d/soap";
    public static final String ERROR_FORMAT = "FALHA NO ENVIO DE %s PARA %s";
    public static final String SENDER_FORMAT = "%s <%s@%s>";
    public static final QName MESSAGE_QNAME = new QName(MessageServiceSoap.NAMESPACE, MessageServiceSoap.NAME);
	public static final QName USER_QNAME = new QName(UserServiceSoap.NAMESPACE, UserServiceSoap.NAME);
	public static final String MESSAGES_WSDL = String.format("/%s/?wsdl", MessageServiceSoap.NAME);
	public static final String USERS_WSDL = String.format("/%s/?wsdl", UserServiceSoap.NAME);

    public static final int TIMEOUT = 5000;
	public static final int SLEEP_TIME = 500;
    public static final int N_TRIES = 5;

    public ServerMessageUtils(){
        this.config = new ClientConfig();
		this.config.property(ClientProperties.CONNECT_TIMEOUT, TIMEOUT);
        this.config.property(ClientProperties.READ_TIMEOUT, TIMEOUT);
        
        this.client = ClientBuilder.newClient(config);
    }
    
    /**
     * Inserts an error message into the sender inbox
     * 
     * @param senderName name of the message sender. No domain anotation
     * @param recipientName name of one of the recipients
     * @param msg message in question
     */
    protected void saveErrorMessages(String senderName, String recipientName, Message msg) {
        Long errorMessageId = Math.abs(randomNumberGenerator.nextLong());

        Message m = new Message(errorMessageId, msg.getSender(), msg.getDestination(),
                String.format(ERROR_FORMAT, msg.getId(), recipientName), msg.getContents());

        synchronized (this.allMessages) {
            this.allMessages.put(errorMessageId, m);
        }
        synchronized (this.userInboxs) {
            this.userInboxs.get(senderName).add(errorMessageId);
        }
    }

    /**
     * Saves a message in the domain. If the recipient does not exist, adds an error
     * message
     * 
     * @param senderName    message sender
     * @param recipientName name of a recipient in this domain. Always in this
     *                      domain.
     * @param forwarded     was the message forwarded?
     * @param mid           id to be assigned
     * @return was it not forwarded and succesful?
     */
    protected boolean saveMessage(String senderName, String recipientName, boolean forwarded, Message msg) {
        synchronized (this.userInboxs) {
            synchronized (this.allMessages) {
                if (!userInboxs.containsKey(recipientName)) {
                    if (forwarded){
                        Log.info("saveMessage: user does not exist for forwarded message " + msg.getId());
                        return false;
                    }else {
                        this.saveErrorMessages(senderName, recipientName, msg);
                    }
                } else {
                    this.allMessages.put(msg.getId(), msg);
                    this.userInboxs.get(recipientName).add(msg.getId());
                }
            }
        }
        Log.info("saveMessage: Sucessfuly saved message " + msg.getId());
        return true;
    }

    /**
     * Fetches a user from the UserResource
     * 
     * @param name name of the user. No domain attached
     * @param pwd  password
     * @return the User Object corresponding to the sender. Null if none is found
     * @throws UnknownHostException can't compile if this isn't declared...
     */
    protected User getUserRest(String name, String pwd) {
        Response r = null;

        WebTarget target = client.target(this.serverUri).path(UserServiceRest.PATH);
        target = target.queryParam("pwd", pwd);

        try {
            r = target.path(name).request().accept(MediaType.APPLICATION_JSON).get();
        } catch (ProcessingException e) {
            Log.info("getUserRest: Could not communicate with the UserResource. What?");
        }

        if (r.getStatus() == Status.FORBIDDEN.getStatusCode()) {
            Log.info("getUserRest: User either doesn't exist or the password is incorrect");
            return null;
        }

        return r.readEntity(User.class);
    }


    /**
     * Given a user in the format name@domain or displayName <name@domain> converts it to name
     * @param senderName name to be converted
     * @return name only
     */
	protected static String getSenderCanonicalName(String senderName){
		String[] tokens = senderName.split(" <");
		int nTokens = tokens.length;

		if(nTokens == 2){
			tokens = tokens[1].split("@");
		}else
			tokens = tokens[0].split("@");
		
		return tokens[0];
	}

	/**
	 * Fetches a sender of a message from the UserResource
	 * 
	 * @param name name of the sender. Without the domain
	 * @param pwd  password
	 * @return the User Object corresponding to the sender. Null if none is found
	 * @throws UnknownHostException can't compile if this isn't declared...
	 */
	protected User getUserSoap(String name, String pwd){		
		User user = null;

		UserServiceSoap userService = null;
				
		try {
			Service	service = Service.create(new URL(this.serverUri + USERS_WSDL), USER_QNAME);
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

		((BindingProvider) userService).getRequestContext().put(BindingProviderProperties.CONNECT_TIMEOUT, TIMEOUT);
		((BindingProvider) userService).getRequestContext().put(BindingProviderProperties.REQUEST_TIMEOUT, TIMEOUT);
		
        try {
            user = userService.getUser(name, pwd);
        }
        catch( MessagesException me){
            Log.info("getUser: Error, could not send the message. Retrying...");
        }
        catch(WebServiceException wse){
            Log.info("getUser: Communication error...");	
        }

		return user;
    }
    
    /**
     * Gives the requestHandler a new message to be posted to other domains
     * @param recipientDomains domains to be contacted
     * @param msg message to be forwarded
     * @param isRest is the calling server REST?
     */
    protected void forwardMessage(Set<String> recipientDomains, Message msg, boolean isRest) {
        for (String domain : recipientDomains) {
            DomainInfo uri = isRest ? RESTMailServer.serverRecord.knownUrisOf(domain)
                                    : SOAPMailServer.serverRecord.knownUrisOf(domain);


            if (uri == null){
				Log.info("forwardMessage: " + domain + " does not exist or is offline.");
				continue;
			}

			Log.info("forwardMessage: Trying to forward message " + msg.getId() + " to " + domain);            
            
            synchronized(this.requests){
                RequestHandler rh = this.requests.get(domain);
                if(rh == null){
                    rh = new RequestHandler(config, this);
                    this.requests.put(domain, rh);

                    new Thread(rh).start();
                }
                
                rh.addRequest(new PostRequest(uri, msg, domain));
            }
        }
	}

	/**
	 * When receiving a delete request for a mid, this function is used
	 * to redirect the request to the domains containing the recipients
	 * of the message
     * 
	 * @param recipientDomains domains to be contacted
	 * @param mid mid of the message to be deleted
     * @param isRest is the calling server REST?
	 */
	protected void forwardDelete(Set<String> recipientDomains, String mid, boolean isRest) {
		for(String domain: recipientDomains){
			DomainInfo uri = isRest ? RESTMailServer.serverRecord.knownUrisOf(domain)
                                    : SOAPMailServer.serverRecord.knownUrisOf(domain);
			
			if(uri == null){
				Log.info("forwardDelete: " + domain + " does not exist or is offline.");
				continue;
			}


			Log.info("forwardDelete: Sending delete request to domain: " + domain);

            synchronized(this.requests){
                RequestHandler rh = this.requests.get(domain);
                if(rh == null){
                    rh = new RequestHandler(config, this);
                    this.requests.put(domain, rh);

                    new Thread(rh).start();
                }

                rh.addRequest(new DeleteRequest(uri, domain, mid));
            }           
        }	
    }



}