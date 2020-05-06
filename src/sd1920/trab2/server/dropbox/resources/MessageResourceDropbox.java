package sd1920.trab2.server.dropbox.resources;

import java.util.List;

import sd1920.trab2.api.Message;
import sd1920.trab2.api.rest.MessageServiceRest;

public class MessageResourceDropbox implements MessageServiceRest {

    @Override
    public long postMessage(String pwd, Message msg) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Message getMessage(String user, long mid, String pwd) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Long> getMessages(String user, String pwd) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void removeFromUserInbox(String user, long mid, String pwd) {
        // TODO Auto-generated method stub

    }

    @Override
    public void deleteMessage(String user, long mid, String pwd) {
        // TODO Auto-generated method stub

    }

    @Override
    public void createInbox(String user) {
        // TODO Auto-generated method stub

    }

    @Override
    public List<String> postForwardedMessage(Message msg) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void deleteForwardedMessage(long mid) {
        // TODO Auto-generated method stub

    }

}