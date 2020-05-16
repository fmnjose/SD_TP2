package sd1920.trab2.server.serverUtils;

import sd1920.trab2.api.Message;
import sd1920.trab2.server.dropbox.ProxyMailServer;
import sd1920.trab2.server.dropbox.arguments.CopyArgs;
import sd1920.trab2.server.dropbox.resources.MessageResourceProxy;
import sd1920.trab2.server.dropbox.resources.UserResourceProxy;

public class CopyRequest extends Request{
    private String senderName, recipientName;
    private Message msg;
    private CopyArgs copy;
    private boolean forwarded;
   
    public CopyRequest(String senderName, String recipientName, Message msg, boolean forwarded) {
        super(null, null, null);
        this.senderName = senderName;
        this.recipientName = recipientName;
        this.msg = msg;
        this.forwarded = forwarded;

        String fromPath = String.format(MessageResourceProxy.MESSAGE_FORMAT, ProxyMailServer.hostname, Long.toString(msg.getId()));
        String toPath = String.format(UserResourceProxy.USER_MESSAGE_FORMAT, ProxyMailServer.hostname, recipientName, Long.toString(msg.getId()));
        this.copy = new CopyArgs(fromPath, toPath);
    } 

    /**
     * 
     * @return canonical sender name
     */
    public String getSender(){
        return this.senderName;
    }

    /**
     * @return canonical recipient name
     */
    public String getRecipient(){
        return this.recipientName;
    }

    public Message getMessage(){
        return this.msg;
    }
    
    public CopyArgs getCopy(){
        return this.copy;
    }

    public boolean isForwarded(){
        return this.forwarded;
    }
}