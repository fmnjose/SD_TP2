package sd1920.trab2.server.serverUtils;

import sd1920.trab2.api.Message;
import sd1920.trab2.api.Discovery.DomainInfo;
import sd1920.trab2.server.serverUtils.ServerUtils.ServerTypes;

/**
 * Represents a Request to post a message. Used for the RequestHandler
 */
public class PostRequest extends Request {

    private Message msg;

    public PostRequest(ServerTypes type, Message msg, String domain, String secret) {
        super(type, domain, secret);
        this.msg = msg;
    }

    public Message getMessage(){
        return this.msg;
    }
}