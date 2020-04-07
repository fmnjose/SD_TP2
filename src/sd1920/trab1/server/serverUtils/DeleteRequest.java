package sd1920.trab1.server.serverUtils;

import sd1920.trab1.api.Discovery.DomainInfo;



public class DeleteRequest extends Request {

    private String mid;
    public DeleteRequest(DomainInfo uri, String domain, String mid){
        super(uri,domain);
        this.mid = mid;
    }

    public String getMid(){
        return this.mid;
    }
}