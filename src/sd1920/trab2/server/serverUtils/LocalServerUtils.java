package sd1920.trab2.server.serverUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.client.ClientBuilder;


import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import sd1920.trab2.api.Message;
import sd1920.trab2.server.rest.RESTMailServer;
import sd1920.trab2.server.soap.SOAPMailServer;


public abstract class LocalServerUtils extends ServerUtils{
    
    protected final Map<Long, Message> allMessages = new HashMap<Long, Message>();
    protected final Map<String, Set<Long>> userInboxs = new HashMap<String, Set<Long>>();
    protected final Map<String, RequestHandler> requests = new HashMap<>();


    public LocalServerUtils(boolean isRest){
        super(isRest ? RESTMailServer.secret : SOAPMailServer.secret);
        this.config = new ClientConfig();
		this.config.property(ClientProperties.CONNECT_TIMEOUT, TIMEOUT);
        this.config.property(ClientProperties.READ_TIMEOUT, TIMEOUT);
        
        this.client = ClientBuilder.newClient(config);
    }
    
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

    protected boolean saveMessage(String senderName, String recipient, boolean forwarded, Message msg) {
        synchronized (this.userInboxs) {
            synchronized (this.allMessages) {
                String recipientCanonicalName = getSenderCanonicalName(recipient);
                if (!userInboxs.containsKey(recipientCanonicalName)) {
                    if (forwarded){
                        System.out.println("saveMessage: user does not exist for forwarded message " + msg.getId());
                        return false;
                    }else {
                        System.out.println("saveMessage: Void User");
                        this.saveErrorMessages(senderName, recipient, msg);
                    }
                } else {
                    System.out.println("saveMessage: SAVE TIME -> " + recipientCanonicalName + " : " + msg.getId());
                    this.allMessages.put(msg.getId(), msg);
                    this.userInboxs.get(recipientCanonicalName).add(msg.getId());
                }
            }
        }
        System.out.println("saveMessage: Sucessfuly saved message " + msg.getId());
        return true;
    }

   



}