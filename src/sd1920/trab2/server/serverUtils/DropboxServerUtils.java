package sd1920.trab2.server.serverUtils;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import sd1920.trab2.api.Message;
import sd1920.trab2.server.dropbox.ProxyMailServer;
import sd1920.trab2.server.dropbox.arguments.CopyArgs;
import sd1920.trab2.server.dropbox.requests.Copy;
import sd1920.trab2.server.dropbox.requests.Create;
import sd1920.trab2.server.dropbox.resources.MessageResourceProxy;
import sd1920.trab2.server.dropbox.resources.UserResourceProxy;

public class DropboxServerUtils extends ServerUtils {

    //Macumbas da documentação da google
	public static final Type LONG_SET_TYPE = new TypeToken<HashSet<Long>>() {}.getType();
	

    public static Gson json = new Gson();

    public DropboxServerUtils(){
        super(ServerTypes.PROXY);
    }

    @Override
    protected void saveErrorMessages(String senderName, String recipientName, Message msg) {
        Log.info("Saving error message: From " + senderName + " to " + recipientName);
        Long errorMessageId = Math.abs(randomNumberGenerator.nextLong());

        Message m = new Message(errorMessageId, msg.getSender(), msg.getDestination(),
                String.format(ERROR_FORMAT, msg.getId(), recipientName), msg.getContents());

        String path = String.format(MessageResourceProxy.MESSAGE_FORMAT, 
                                    ProxyMailServer.hostname, Long.toString(errorMessageId));

        Create.run(path, m);
        
        String toPath = String.format(UserResourceProxy.USER_MESSAGE_FORMAT, ProxyMailServer.hostname, senderName, Long.toString(errorMessageId));
        
        Copy.run(new CopyArgs(path, toPath));
    }

    protected void saveMessage(Set<String> recipients, Message msg, boolean forwarded) {
        Log.info("Saving message " + msg.getId() + " from " + msg.getSender() + " to " + recipients.size() + " recipients");

        String senderName = getSenderCanonicalName(msg.getSender());

        synchronized(this.requests){
            RequestHandler rh = this.requests.get(domain);
            if(rh == null){
                rh = new RequestHandler(config, this);
                this.requests.put(domain, rh);

                new Thread(rh).start();
            }

            for(String recipient : recipients)
                rh.addRequest(new CopyRequest(senderName, recipient, msg, forwarded));
        }   
    }
}
