package sd1920.trab2.server.serverUtils;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import sd1920.trab2.api.Message;
import sd1920.trab2.server.dropbox.ProxyMailServer;
import sd1920.trab2.server.dropbox.arguments.CopyArgs;
import sd1920.trab2.server.dropbox.requests.Copy;
import sd1920.trab2.server.dropbox.requests.Create;
import sd1920.trab2.server.dropbox.requests.GetMeta;
import sd1920.trab2.server.dropbox.resources.MessageResourceProxy;
import sd1920.trab2.server.dropbox.resources.UserResourceProxy;

public class DropboxServerUtils extends ServerUtils {

    //Macumbas da documentação da google
	public static final Type LONG_SET_TYPE = new TypeToken<HashSet<Long>>() {}.getType();
	

    public static Gson json = new Gson();

    public DropboxServerUtils(String secret){
        super(secret);
    }

    @Override
    protected void saveErrorMessages(String senderName, String recipientName, Message msg) {
        System.out.println("Saving error message: From " + senderName + " to " + recipientName);
        Long errorMessageId = Math.abs(randomNumberGenerator.nextLong());

        Message m = new Message(errorMessageId, msg.getSender(), msg.getDestination(),
                String.format(ERROR_FORMAT, msg.getId(), recipientName), msg.getContents());

        String path = String.format(MessageResourceProxy.MESSAGE_FORMAT, 
                                    ProxyMailServer.hostname, Long.toString(errorMessageId));

        Create.run(path, m);
        
        String toPath = String.format(UserResourceProxy.USER_MESSAGE_FORMAT, ProxyMailServer.hostname, senderName, Long.toString(errorMessageId));
        
        Copy.run(new CopyArgs(path, toPath));
    }

    protected boolean saveMessage(Set<String> recipients, Message msg, boolean forwarded) {
        System.out.println("Saving message " + msg.getId() + " from " + msg.getSender() + " to " + recipients.size() + " recipients");

        List<CopyArgs> copies = new LinkedList<>();

        String senderName = getSenderCanonicalName(msg.getSender());

        String fromPath = String.format(MessageResourceProxy.MESSAGE_FORMAT, ProxyMailServer.hostname, msg.getId());

        for(String recipient : recipients){

            String recipientName = getSenderCanonicalName(recipient);
            
            String userPath = String.format(UserResourceProxy.USER_FOLDER_FORMAT, ProxyMailServer.hostname, recipient);

            if (!GetMeta.run(userPath)) {
                if (forwarded){
                    System.out.println("saveMessages: user does not exist for forwarded message " + msg.getId());
                }else {
                    this.saveErrorMessages(senderName, recipientName, msg);
                }
            } else {
                String toPath = String.format(UserResourceProxy.USER_MESSAGE_FORMAT, ProxyMailServer.hostname, recipientName, Long.toString(msg.getId()));

                copies.add(new CopyArgs(fromPath, toPath));                               
            }
        }

        if(Copy.run(copies))
            System.out.println("saveMessages: Sucessfuly saved messages");
        else 
            System.out.println("saveMessages: Unsuccessful");

        return true;
    }
}
