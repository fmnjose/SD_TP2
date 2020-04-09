package sd1920.trab1.server.serverUtils;

import sd1920.trab1.api.Message;
import sd1920.trab1.api.Discovery.DomainInfo;

/**
 * Represents a Request to post a message. Used for the RequestHandler
 */
public class PostRequest extends Request {

    private Message msg;

    public PostRequest(DomainInfo uri, Message msg, String domain) {
        super(uri, domain);
        this.msg = msg;
    }

    public Message getMessage(){
        return this.msg;
    }
}