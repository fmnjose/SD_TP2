package sd1920.trab2.server.serverUtils;

import sd1920.trab2.api.Discovery.DomainInfo;
import sd1920.trab2.server.serverUtils.ServerUtils.ServerTypes;

/**
 * Represents a Request. Used for the RequestHandler
 */
public abstract class Request {
    private ServerTypes type;
    private String domain, secret;
    
    public Request(ServerTypes type, String domain, String secret){
        this.type = type;
        this.domain = domain;
        this.secret = secret;
    }

    public ServerTypes getType(){
        return this.type;
    }    

    public String getDomain(){
        return this.domain;
    }

    public String getSecret(){
        return this.secret;
    }
}