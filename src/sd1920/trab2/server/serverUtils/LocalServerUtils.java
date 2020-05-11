package sd1920.trab2.server.serverUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;


import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import sd1920.trab2.api.Message;

import sd1920.trab2.api.soap.UserServiceSoap;

import javax.xml.namespace.QName;


import sd1920.trab2.api.soap.MessageServiceSoap;


public abstract class LocalServerUtils extends ServerUtils{
    
    protected Random randomNumberGenerator;
    protected Client client;
    protected ClientConfig config;
    protected String domain;
    protected String serverUri;
    protected static Logger Log;
    protected final Map<Long, Message> allMessages = new HashMap<Long, Message>();
    protected final Map<String, Set<Long>> userInboxs = new HashMap<String, Set<Long>>();
    protected final Map<String, RequestHandler> requests = new HashMap<>();

    public static final String DOMAIN_FORMAT_REST = "https://%s:%d/rest";
    public static final String DOMAIN_FORMAT_SOAP = "https://%s:%d/soap";
    public static final String ERROR_FORMAT = "FALHA NO ENVIO DE %s PARA %s";
    public static final String SENDER_FORMAT = "%s <%s@%s>";
    public static final QName MESSAGE_QNAME = new QName(MessageServiceSoap.NAMESPACE, MessageServiceSoap.NAME);
	public static final QName USER_QNAME = new QName(UserServiceSoap.NAMESPACE, UserServiceSoap.NAME);
	public static final String MESSAGES_WSDL = String.format("/%s/?wsdl", MessageServiceSoap.NAME);
	public static final String USERS_WSDL = String.format("/%s/?wsdl", UserServiceSoap.NAME);

    public static final int TIMEOUT = 1000;
	public static final int SLEEP_TIME = 500;
    public static final int N_TRIES = 5;

    public LocalServerUtils(){
        this.config = new ClientConfig();
		this.config.property(ClientProperties.CONNECT_TIMEOUT, TIMEOUT);
        this.config.property(ClientProperties.READ_TIMEOUT, TIMEOUT);
        
        this.client = ClientBuilder.newClient(config);
    }
    
    @Override
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

   @Override
    protected boolean saveMessage(String senderName, String recipient, boolean forwarded, Message msg) {
        synchronized (this.userInboxs) {
            synchronized (this.allMessages) {
                String recipientCanonicalName = getSenderCanonicalName(recipient);
                if (!userInboxs.containsKey(recipientCanonicalName)) {
                    if (forwarded){
                        Log.info("saveMessage: user does not exist for forwarded message " + msg.getId());
                        return false;
                    }else {
                        this.saveErrorMessages(senderName, recipient, msg);
                    }
                } else {
                    this.allMessages.put(msg.getId(), msg);
                    this.userInboxs.get(recipientCanonicalName).add(msg.getId());
                }
            }
        }
        Log.info("saveMessage: Sucessfuly saved message " + msg.getId());
        return true;
    }

   



}