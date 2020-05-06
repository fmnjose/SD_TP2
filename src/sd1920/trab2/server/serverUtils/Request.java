package sd1920.trab2.server.serverUtils;

import sd1920.trab2.api.Discovery.DomainInfo;

/**
 * Represents a Request. Used for the RequestHandler
 */
public abstract class Request {
    protected DomainInfo uri;
    protected String domain;
    
    public Request(DomainInfo uri, String domain){
        this.uri = uri;
        this.domain = domain;
    }

    public DomainInfo getUri(){
        return this.uri;
    }    

    public String getDomain(){
        return this.domain;
    }
}