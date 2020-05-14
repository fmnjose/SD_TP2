package sd1920.trab2.server.serverUtils;

import java.lang.reflect.Type;
import java.util.HashSet;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import sd1920.trab2.api.Message;
import sd1920.trab2.api.proxy.UserProxy;
import sd1920.trab2.server.dropbox.ProxyMailServer;
import sd1920.trab2.server.dropbox.requests.CreateFile;
import sd1920.trab2.server.dropbox.resources.MessageResourceProxy;
import sd1920.trab2.server.dropbox.resources.UserResourceProxy;

public class DropboxServerUtils extends ServerUtils {

    //Macumbas da documentação da google
	public static final Type LONG_SET_TYPE = new TypeToken<HashSet<Long>>() {}.getType();
	

    public static Gson json = new Gson();

    public DropboxServerUtils(String secret){
        super(secret);
    }

    protected UserProxy getUserProxy(String name){
        System.out.println("Calling UserResourceProxy for a UserProxy " + name);
        Response r = null;

        WebTarget target = client.target(this.serverUri).path(UserResourceProxy.PATH);
        target = target.queryParam("secret", this.secret);

        try {
            target = target.path(name).path("proxy");
            r = target.request().accept(MediaType.APPLICATION_JSON).get();
        } catch (ProcessingException e) {
            System.out.println("getUserProxy: Could not communicate with the UserResource. What?");
        }

        if (r.getStatus() == Status.FORBIDDEN.getStatusCode()) {
            System.out.println("getUserRest: User doesn't exist.");
            return null;
        }

        String proxyString = r.readEntity(String.class);

        if(proxyString == null){
            System.out.println("getUserProxy: Not found");
        }
        UserProxy u = json.fromJson(proxyString, UserProxy.class);

        System.out.println("It answered. Returning " + u);
        return u;
    }

    @Override
    protected void saveErrorMessages(String senderName, String recipientName, Message msg) {
        System.out.println("Saving error message: From " + senderName + " to " + recipientName);
        Long errorMessageId = Math.abs(randomNumberGenerator.nextLong());

        Message m = new Message(errorMessageId, msg.getSender(), msg.getDestination(),
                String.format(ERROR_FORMAT, msg.getId(), recipientName), msg.getContents());

        UserProxy sender = this.getUserProxy(senderName);

        sender.getMids().add(errorMessageId);

        String path = String.format(MessageResourceProxy.MESSAGE_FORMAT, 
                                    ProxyMailServer.hostname, Long.toString(errorMessageId));

        CreateFile.run(path, m);
    }

    @Override
    protected boolean saveMessage(String senderName, String recipient, boolean forwarded, Message msg) {
        System.out.println("Saving message " + msg.getId() + " from " + senderName + " to " + recipient);

        String recipientCanonicalName = getSenderCanonicalName(recipient);
        
        UserProxy recipientUser = this.getUserProxy(recipientCanonicalName);

        if (recipientUser == null) {
            System.out.println("MUDA MUDA");
            if (forwarded){
                System.out.println("saveMessage: user does not exist for forwarded message " + msg.getId());
                return false;
            }else {
                this.saveErrorMessages(senderName, recipientCanonicalName, msg);
            }
        } else {
            System.out.println("Beans1");
            String path = String.format(UserResourceProxy.USER_DATA_FORMAT, 
                                        ProxyMailServer.hostname, recipientCanonicalName);
            System.out.println("Beans2");
            recipientUser.getMids().add(msg.getId());

            System.out.println("Beans3");

            CreateFile.run(path, recipientUser);

            System.out.println("Beans4");
        }

        System.out.println("saveMessage: Sucessfuly saved message " + msg.getId() + " to user " + recipientCanonicalName);
        return true;
    }
    
}