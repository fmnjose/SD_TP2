package sd1920.trab2.server.serverUtils;

import sd1920.trab2.api.Discovery.DomainInfo;

/**
 * Represents a Request. Used for the RequestHandler
 */
public abstract class Request {
    private DomainInfo uri;
    private String domain, secret;
    
    public Request(DomainInfo uri, String domain, String secret){
        this.uri = uri;
        this.domain = domain;
        this.secret = secret;
    }

    public DomainInfo getUri(){
        return this.uri;
    }    

    public String getDomain(){
        return this.domain;
    }

    public String getSecret(){
        return this.secret;
    }
}