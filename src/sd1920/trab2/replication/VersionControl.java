package sd1920.trab2.replication;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import sd1920.trab2.api.Message;
import sd1920.trab2.api.User;
import sd1920.trab2.api.rest.MessageServiceRest;
import sd1920.trab2.api.rest.UserServiceRest;
import sd1920.trab2.server.rest.RESTMailServer;
import sd1920.trab2.server.serverUtils.ServerUtils;

public class VersionControl {

    private static final long TTL = 18000;
    private boolean isPrimary;

    private ZookeeperProcessor zk;
    private String domain;
    private String uri;
    private long version;
    private String primaryUri;
    private SyncPoint sync;
    private Long awaitingVersion;

    private Client client;

    private List<String> childrenList;

    private List<Operation> ops;

    public VersionControl(String domain, String uri) throws Exception {
        this.domain = domain;
        this.uri = uri;
        this.version = 0;
        this.awaitingVersion = new Long(0);
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
        
        sync = SyncPoint.getInstance();

        ops = new LinkedList<>();
    }
    
    private synchronized void addOperation(Operation op){
        purgeList();
        ops.add(op);
        this.version++;
        sync.setResult(this.getVersion(), null);
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

    public List<Operation> getOperations(long version){
        return ops.subList((int)(version - this.getHeadVersion()), ops.size() - 1);
    }

    private void processOperation(Operation op){
    }
    
    private void updateState(){
    //UPDATE ALL MESSAGES
        //GET
        WebTarget target = client.target(this.primaryUri);
            target = target.path(MessageServiceRest.PATH).path("update");
            target = target.queryParam("secret", RESTMailServer.secret);
            
            Response r = target.request()
            .accept(MediaType.APPLICATION_JSON)
            .get();

        Map<Long, Message> allMessages = r.readEntity(new GenericType<Map<Long,Message>>() {});
        //SET
        target = client.target(this.primaryUri);
            target = target.path(MessageServiceRest.PATH).path("update");
            target = target.queryParam("secret", RESTMailServer.secret);
        
            r = target.request()
            .post(Entity.entity(allMessages, MediaType.APPLICATION_JSON));

    //UPDATE USER INBOXES
        //GET
        target = client.target(this.primaryUri);
            target = target.path(MessageServiceRest.PATH).path("update").path("mbox");
            target = target.queryParam("secret", RESTMailServer.secret);
            
            r = target.request()
            .accept(MediaType.APPLICATION_JSON)
            .get();

        Map<String, Set<Long>> userInboxes = r.readEntity(new GenericType<Map<String,Set<Long>>>() {});
        //SET
        target = client.target(this.primaryUri);
            target = target.path(MessageServiceRest.PATH).path("update").path("mbox");
            target = target.queryParam("secret", RESTMailServer.secret);
        
            r = target.request()
            .post(Entity.entity(userInboxes, MediaType.APPLICATION_JSON));

    //UPDATE USERS
        //GET
        target = client.target(this.primaryUri);
            target = target.path(UserServiceRest.PATH).path("update");
            target = target.queryParam("secret", RESTMailServer.secret);
            
            r = target.request()
            .accept(MediaType.APPLICATION_JSON)
            .get();

        Map<String, User> users = r.readEntity(new GenericType<Map<String,User>>() {});
        //SET
        target = client.target(this.primaryUri);
            target = target.path(MessageServiceRest.PATH).path("update");
            target = target.queryParam("secret", RESTMailServer.secret);
        
            r = target.request()
            .post(Entity.entity(users, MediaType.APPLICATION_JSON));   
    }

    public void syncVersion(long version){
        if (version > this.getVersion() + 1) {
            WebTarget target = client.target(primaryUri);
            target = target.path(MessageServiceRest.PATH).path("replica");
            target = target.queryParam("secret", RESTMailServer.secret);
            
            Response r = target.request()
            .header(MessageServiceRest.HEADER_VERSION, this.getVersion())
            .accept(MediaType.APPLICATION_JSON)
            .get();

            if(r.getStatus() == Status.ACCEPTED.getStatusCode()){
                List<Operation> updatedOperations = r.readEntity(new GenericType<List<Operation>>() {});
                
                for (Operation operation : updatedOperations) {
                    processOperation(operation);
                    this.addOperation(operation);
                }
            }
            else if(r.getStatus() == Status.GONE.getStatusCode()){
                this.updateState();
                this.version = version;
            }
		}
    }

    public void purgeList(){
        while(System.currentTimeMillis() - ops.get(0).getCreationTime() >= TTL)
            ops.remove(0);
    }  

    public synchronized void waitForVersion(){
        this.awaitingVersion++;
        sync.waitForVersion(this.awaitingVersion);
    }

    public boolean isPrimary(){
        return this.isPrimary;
    }

    public String getPrimaryUri(){
        return this.primaryUri;
    }

    public long getVersion(){
        return this.version;
    }

    public long getHeadVersion(){
        return this.version - this.ops.size();
    }  


    public void postMessage(Message msg){
        int failCounter = 0;
        
        for(String child : this.childrenList){
            String uri = new String(zk.getData("/" + child));
            
            WebTarget target = client.target(uri);
            target = target.path(MessageServiceRest.PATH).path("mbox").path("replica");
            target = target.queryParam("secret", RESTMailServer.secret);
            
            Response r = target.request()
            .header(MessageServiceRest.HEADER_VERSION, this.getVersion())
            .accept(MediaType.APPLICATION_JSON)
            .post(Entity.entity(msg, MediaType.APPLICATION_JSON));
            
            if(r.getStatus() != Status.ACCEPTED.getStatusCode()){
                failCounter++;
                System.out.println("FODA SE");
            }
        }

        this.addOperation(new Operation(Operation.Type.POST_MESSAGE, msg));
    }
}