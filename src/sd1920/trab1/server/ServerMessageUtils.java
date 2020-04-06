package sd1920.trab1.server;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;

import org.glassfish.jersey.client.ClientConfig;

import sd1920.trab1.api.Message;
import sd1920.trab1.api.User;
import sd1920.trab1.api.Discovery.DomainInfo;
import sd1920.trab1.api.rest.MessageServiceRest;
import sd1920.trab1.api.rest.UserServiceRest;
import sd1920.trab1.api.soap.UserServiceSoap;
import sd1920.trab1.server.rest.RESTMailServer;
import sd1920.trab1.server.rest.resources.MessageResourceRest;

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
    protected Logger Log;
    protected final Map<Long, Message> allMessages = new HashMap<Long, Message>();
    protected final Map<String, Set<Long>> userInboxs = new HashMap<String, Set<Long>>();

    public static final String ERROR_FORMAT = "FALHA NO ENVIO DE %s PARA %s";
    public static final String SENDER_FORMAT = "%s <%s@%s>";
    public static final QName MESSAGE_QNAME = new QName(MessageServiceSoap.NAMESPACE, MessageServiceSoap.NAME);
	public static final QName USER_QNAME = new QName(UserServiceSoap.NAMESPACE, UserServiceSoap.NAME);
	public static final String MESSAGES_WSDL = String.format("/%s/?wsdl", MessageServiceSoap.NAME);
	public static final String USERS_WSDL = String.format("/%s/?wsdl", UserServiceSoap.NAME);


    public static final int TIMEOUT = 10000;
	public static final int SLEEP_TIME = 5000;
	public static final int N_TRIES = 5;
    /**
     * Inserts error message into the sender inbox
     * 
     * @param senderName
     * @param recipientName
     * @param msg
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
     * Saves a message in our domain. If the recipient does not exist, adds an error
     * message
     * 
     * @param senderName
     * @param recipientName name of a recipient in this domain. Always in this
     *                      domain.
     * @param forwarded
     * @param mid           id to be assigned
     */
    protected boolean saveMessage(String senderName, String recipientName, boolean forwarded, Message msg) {
        synchronized (this.userInboxs) {
            synchronized (this.allMessages) {
                if (!userInboxs.containsKey(recipientName)) {
                    if (forwarded)
                        return false;
                    else {
                        this.saveErrorMessages(senderName, recipientName, msg);
                    }
                } else {
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
    protected User getUserRest(String name, String pwd) {
        Response r = null;

        boolean error = true;

        WebTarget target = client.target(this.serverUri).path(UserServiceRest.PATH);
        target = target.queryParam("pwd", pwd);

        int tries = 0;

        while (error && tries < N_TRIES) {
            error = false;

            try {
                r = target.path(name).request().accept(MediaType.APPLICATION_JSON).get();
            } catch (ProcessingException e) {
                Log.info("Could not communicate with the UserResource. Retrying...");
                try {
                    Thread.sleep(SLEEP_TIME);
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
	 * @param name name of the sender. Without the @
	 * @param pwd  password
	 * @return the User Object corresponding to the sender. Null if none is found
	 * @throws UnknownHostException can't compile if this isn't declared...
	 */
	protected User getUserSoap(String name, String pwd){		
		User user = null;

		boolean error = true;
		
		int tries = 0;

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
		

		while (error && tries < N_TRIES) {
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
					Thread.sleep(SLEEP_TIME);
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
    
    protected void forwardMessage(Set<String> recipientDomains, Message msg, boolean isRest) {
        Response r = null;
        List<String> failedDeliveries = null;

        System.out.println("ENTREI NO FORWARD");
        for (String domain : recipientDomains) {
            boolean error = true;
            DomainInfo uri = isRest ? RESTMailServer.serverRecord.knownUrisOf(domain)
                                    : SOAPMailServer.serverRecord.knownUrisOf(domain);


            if (uri == null){
				Log.info("forwardMessage: " + domain + " does not exist or is offline.");
				continue;
			}

			System.out.println("forwardMessage: Trying to forward message " + msg.getId() + " to " + domain);
			Log.info("forwardMessage: Trying to forward message " + msg.getId() + " to " + domain);
			int tries = 0;

            if(uri.isRest()){
                WebTarget target = client.target(uri.getUri()).path(MessageServiceRest.PATH).path("mbox");
                System.out.println("ENTREI BEM E COM SAUDE");
                
                while (error && tries < N_TRIES) {
                    
                    error = false;

                    try {
                        System.out.println("ENTREI NO WHILE");
                        r = target.request().accept(MediaType.APPLICATION_JSON)
                                .post(Entity.entity(msg, MediaType.APPLICATION_JSON));
                    } catch (ProcessingException e) {
                        Log.info("forwardMessage: Failed to forward message to " + domain + ". Retrying...");
                        try {
                            Thread.sleep(SLEEP_TIME);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }
                        error = true;
                    }
                    
                }
                
                failedDeliveries = error ? new LinkedList<>() 
                                    : r.readEntity(new GenericType<List<String>>(){});
                
            }
            else{
                System.out.println("NAO BEANS");
                MessageServiceSoap msgService = null;
			
                try {
                    Service	service = Service.create(new URL(uri.getUri() + MESSAGES_WSDL), MESSAGE_QNAME);
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

                ((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.CONNECT_TIMEOUT, TIMEOUT);
                ((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.REQUEST_TIMEOUT, TIMEOUT);
                
                while (error && tries < N_TRIES) {
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
                            Thread.sleep(SLEEP_TIME);
                        }
                        catch(InterruptedException e){
                            Log.info("Log a dizer 'what?'");
                        }
                        error = true;
                    }
                    
                    tries++;
                }
            }

			if(error){
				Log.info("forwardMessage: Failed to forward message to " + domain + ". Giving up...");
				for(String recipient: msg.getDestination()){
					if(recipient.split("@")[1].equals(domain))
						failedDeliveries.add(recipient);
				}
			}
			else
				Log.info("forwardMessage: Successfully sent message to " + domain + ". Nice!");
			
			
			String senderName = getSenderCanonicalName(msg.getSender());
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
	protected void deleteFromDomains(Set<String> recipientDomains, String user, String mid, boolean isRest) {
		for(String domain: recipientDomains){
			boolean error = true;
			DomainInfo uri = isRest ? RESTMailServer.serverRecord.knownUrisOf(domain)
                                    : SOAPMailServer.serverRecord.knownUrisOf(domain);
			
			if(uri == null){
				Log.info("deleteFromDomains: " + domain + " does not exist or is offline.");
				continue;
			}


			System.out.println("Sending delete request to domain: " + domain);
			Log.info("Sending delete request to domain: " + domain);
			int tries = 0;

            if(uri.isRest()){

                WebTarget target = client.target(uri.getUri()).path(MessageResourceRest.PATH).path("msg");

                while(error && tries< N_TRIES){
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
            }
            else{
                MessageServiceSoap msgService = null;
                
                try {
                    Service	service = Service.create(new URL(uri.getUri() + MESSAGES_WSDL), MESSAGE_QNAME);
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

                ((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.CONNECT_TIMEOUT, TIMEOUT);
                ((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.REQUEST_TIMEOUT, TIMEOUT);
                
                while(error && tries< N_TRIES){
                    error = false;
                    try{
                        msgService.deleteForwardedMessage(Long.valueOf(mid));
                    }
                    catch(MessagesException me){
                        Log.info("forwardMessage: Error, could not send the message.");
                    }
                    catch(WebServiceException wse){
                        Log.info("forwardMessage: Communication error. Retrying...");
                        wse.printStackTrace();
                        try{
                            Thread.sleep(SLEEP_TIME);
                        }
                        catch(InterruptedException e){
                            Log.info("Log a dizer 'what?'");
                        }
                        error = true;
                    }
                    tries++;	
                }
            }

			if(error)
				Log.info("deleteFromDomains: Failed to redirect request to " + domain + ". Giving up...");
			else
				Log.info("deleteFromDomains: Successfully redirected request to " + domain + ". More successful than i'll ever be!");		
		}	
	}
}