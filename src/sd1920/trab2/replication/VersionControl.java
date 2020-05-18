package sd1920.trab2.replication;

import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import sd1920.trab2.api.Message;
import sd1920.trab2.api.rest.MessageServiceRest;
import sd1920.trab2.server.rest.RESTMailServer;
import sd1920.trab2.server.serverUtils.ServerUtils;

public class VersionControl {

    private static final long TTL = 18000;
    private boolean isPrimary;

    private ZookeeperProcessor zk;
    private String domain;
    private String uri;
    private long headVersion;
    private String primaryUri;

    private Client client;

    private List<String> childrenList;

    private List<Operation> ops;

    public VersionControl(String domain, String uri) throws Exception {
        this.domain = domain;
        this.uri = uri;
        this.headVersion = 0;
        this.isPrimary = false;
        this.childrenList = new LinkedList<>();
        this.zk = new ZookeeperProcessor("localhost:2181,kafka:2181");

        ClientConfig config = new ClientConfig();
		config.property(ClientProperties.CONNECT_TIMEOUT, ServerUtils.TIMEOUT);
        config.property(ClientProperties.READ_TIMEOUT, ServerUtils.TIMEOUT);

        client = ClientBuilder.newClient(config);

		String newPath = zk.write(domain, CreateMode.PERSISTENT);
		
		newPath = zk.write(domain + "/server_", uri, CreateMode.EPHEMERAL_SEQUENTIAL);
        System.out.println("Created child znode: " + newPath);
        
        ops = new LinkedList<>();
    }
    
    private void addOperation(Operation op){
        purgeList();
        ops.add(op);
    }

    public void startListening(){
        zk.getChildren( this.domain, new Watcher() {
			@Override
			public void process(WatchedEvent event) {
                childrenList = zk.getChildren( domain, this);
                byte[] b = zk.getData("/" + childrenList.get(0));
                String puri = new String(b);
                isPrimary = uri.equals(puri);

                if(!isPrimary)
                    primaryUri = puri;
			}
		});
    }

    
    public void purgeList(){
        while(System.currentTimeMillis() - ops.get(0).getCreationTime() >= TTL){
            headVersion++;
            ops.remove(0);
        }
    }

    public boolean isPrimary(){
        return this.isPrimary;
    }

    public String getPrimaryUri(){
        return this.primaryUri;
    }

    public long getVersion(){
        return this.headVersion;
    }

    public void postMessage(Message msg){
        int failCounter = 0;

        this.addOperation(new Operation(Operation.Type.POST_MESSAGE, msg));

        for(String child : this.childrenList){
            String uri = new String(zk.getData("/" + child));

            WebTarget target = client.target(uri);
            target = target.path(MessageServiceRest.PATH).path("mbox").path("replica");
            target = target.queryParam("secret", RESTMailServer.secret);
            
            Response r = target.request()
                    .header(MessageServiceRest.HEADER_VERSION, this.headVersion)
                    .accept(MediaType.APPLICATION_JSON)
                    .post(Entity.entity(msg, MediaType.APPLICATION_JSON));

            if(r.getStatus() != Status.ACCEPTED.getStatusCode()){
                failCounter++;
                System.out.println("FODA SE");
            }
        }
    }
}