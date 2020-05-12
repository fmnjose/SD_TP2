package sd1920.trab2.server.serverUtils;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import sd1920.trab2.api.Message;
import sd1920.trab2.server.dropbox.DropboxMailServer;
import sd1920.trab2.server.dropbox.requests.CreateFile;
import sd1920.trab2.server.dropbox.requests.DownloadFile;
import sd1920.trab2.server.dropbox.requests.SearchFile;
import sd1920.trab2.server.dropbox.resources.MessageResourceDropbox;
import sd1920.trab2.server.dropbox.resources.UserResourceDropbox;

public class DropboxServerUtils extends ServerUtils {

    //Macumbas da documentação da google
	public static final Type LONG_SET_TYPE = new TypeToken<HashSet<Long>>() {}.getType();
	

    public static Gson json = new Gson();

    public DropboxServerUtils(String secret){
        super(secret);
    }

    @Override
    protected void saveErrorMessages(String senderName, String recipientName, Message msg) {
        Long errorMessageId = Math.abs(randomNumberGenerator.nextLong());

        Message m = new Message(errorMessageId, msg.getSender(), msg.getDestination(),
                String.format(ERROR_FORMAT, msg.getId(), recipientName), msg.getContents());

        String path = String.format(MessageResourceDropbox.MESSAGE_FORMAT, 
                        DropboxMailServer.hostname, Long.toString(m.getId()));
        CreateFile.run(path, m);

        path = String.format(UserResourceDropbox.USER_MSGS_FILE_FORMAT, DropboxMailServer.hostname,
                    senderName);

        String messagesString = DownloadFile.run(path);
        
        if(messagesString == null)
                return;

        Set<Long> messageIds = json.fromJson(messagesString, LONG_SET_TYPE);                    
        messageIds.add(m.getId());

        CreateFile.run(path, messageIds);
    }

    @Override
    protected boolean saveMessage(String senderName, String recipient, boolean forwarded, Message msg) {
        
        String recipientCanonicalName = getSenderCanonicalName(recipient);
        
        String path = String.format(UserResourceDropbox.USERS_DIR_FORMAT, DropboxMailServer.hostname);

        if (!SearchFile.run(path, recipientCanonicalName)) {
            if (forwarded){
                System.out.println("saveMessage: user does not exist for forwarded message " + msg.getId());
                return false;
            }else {
                this.saveErrorMessages(senderName, recipient, msg);
            }
        } else {
            path = String.format(MessageResourceDropbox.MESSAGE_FORMAT, 
                            DropboxMailServer.hostname, Long.toString(msg.getId()));
            CreateFile.run(path, msg);
            
            path = String.format(UserResourceDropbox.USER_MSGS_FILE_FORMAT, DropboxMailServer.hostname,
                                recipientCanonicalName);
            
            String messagesString = DownloadFile.run(path);
            if(messagesString == null)
                return false;

            Set<Long> messageIds = json.fromJson(messagesString, LONG_SET_TYPE);                   
            messageIds.add(msg.getId());
            
            CreateFile.run(path, messageIds);
        }

        System.out.println("saveMessage: Sucessfuly saved message " + msg.getId());
        return true;
    }
    
}