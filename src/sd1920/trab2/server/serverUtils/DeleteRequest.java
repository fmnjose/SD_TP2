package sd1920.trab2.server.serverUtils;

import sd1920.trab2.server.serverUtils.ServerUtils.ServerTypes;


/**
 * Represents a Request to delete a message. Used for the RequestHandler
 */
public class DeleteRequest extends Request {

    private long mid;
    
    public DeleteRequest(ServerTypes type, String domain, long mid, String secret){
        super(type,domain, secret);
        this.mid = mid;
    }

    public long getMid(){
        return this.mid;
    }
}