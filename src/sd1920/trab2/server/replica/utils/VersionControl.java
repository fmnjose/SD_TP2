package sd1920.trab2.server.replica.utils;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.ws.rs.ProcessingException;
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
import sd1920.trab2.api.replicaRest.ReplicaMessageServiceRest;
import sd1920.trab2.api.rest.MessageServiceRest;
import sd1920.trab2.api.rest.UserServiceRest;
import sd1920.trab2.server.replica.ReplicaMailServerREST;
import sd1920.trab2.server.serverUtils.ServerUtils;

/**
 * Utilized by the replica servers
 * Manages all of the replication mechanisms
 */
public class VersionControl {

    private static Logger Log = Logger.getLogger(ReplicaMailServerREST.class.getName());

    private static final String NODE_FORMAT = "%s/%s";
    private static final long TTL = 8000;
    private static final float REPLICA_FAILURE_TOLERANCE_RATIO = 0.2f;
    private boolean isPrimary;

    private ZookeeperProcessor zk;
    private String domain;
    private String uri;
    private Long version;
    private String primaryUri;
    private SyncPoint sync;
    private Long awaitingVersion;
    private int maxReplicaFailures;

    private Client client;

    private List<String> childrenList;

    private List<Operation> ops;

    public VersionControl(String domain, String uri) throws Exception {
        this.domain = "/" + domain;
        this.uri = uri;
        this.version = 0L;
        this.awaitingVersion = -1L;
        this.isPrimary = false;
        this.childrenList = new LinkedList<>();
        this.zk = new ZookeeperProcessor("kafka:2181");
        this.maxReplicaFailures = 0;

        ClientConfig config = new ClientConfig();
		config.property(ClientProperties.CONNECT_TIMEOUT, ServerUtils.TIMEOUT);
        config.property(ClientProperties.READ_TIMEOUT, ServerUtils.TIMEOUT);

        client = ClientBuilder.newClient(config);

        String newPath = zk.write(this.domain, CreateMode.PERSISTENT);

        this.startListening();
		
		newPath = zk.write(this.domain + "/server_", uri, CreateMode.EPHEMERAL_SEQUENTIAL);
        Log.info("Created child znode: " + newPath);
        
        sync = SyncPoint.getInstance();

        ops = new LinkedList<>();
    }

    //PRIVATE METHODS
    private String getReplicaUri(String node){
       return new String(zk.getData(String.format(NODE_FORMAT, this.domain, node)));
    }

    private void startListening(){
        zk.getChildren( this.domain, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                childrenList = zk.getChildren( domain, this);
                Collections.sort(childrenList);

                byte[] b = zk.getData(String.format(NODE_FORMAT, domain, childrenList.get(0)));
                String puri = new String(b);
                isPrimary = uri.equals(puri);

                primaryUri = puri;

                maxReplicaFailures = (int) (childrenList.size() * REPLICA_FAILURE_TOLERANCE_RATIO) + 1;
            }
        });
    }

    private void processOperation(Operation op){
        String uri = this.uri;

        List<Object> args;
        
        switch (op.getType()) {
            case POST_MESSAGE:
                this.execPostMessage(uri, (Message)op.getArgs().get(0));
                break;
            case DELETE_MESSAGE:
                args = op.getArgs();
                this.execDeleteMessage(uri, (String) args.get(0), (Long) args.get(1));
                break;
            case REMOVE_FROM_INBOX:
                args = op.getArgs();
                this.execRemoveFromUserInbox(uri, (String) args.get(0), (Long) args.get(1));
                break;
            case POST_FORWARDED:
                this.execPostforwardedMessage(uri, (Message) op.getArgs().get(0));
                break;
            case DELETE_FORWARDED:
                this.execDeleteForwardedMessage(uri, (Long) op.getArgs().get(0));
                break;
            case POST_USER:
                this.execPostUser(uri, (User) op.getArgs().get(0));
                break;
            case UPDATE_USER:
                args = op.getArgs();
                this.execUpdateUSer(uri, (String) args.get(0), (User) args.get(1));
                break;
            case DELETE_USER:
                this.execDeleteUser(uri, (String) op.getArgs().get(0));
                break;
            default:
                break;
        }
    }
    
    private void updateState(){
    //UPDATE ALL MESSAGES
        //GET
        WebTarget target = client.target(this.primaryUri);
            target = target.path(MessageServiceRest.PATH).path("update");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
            
            Response r = target.request()
            .accept(MediaType.APPLICATION_JSON)
            .get();

        Map<Long, Message> allMessages = r.readEntity(new GenericType<Map<Long,Message>>() {});
        //SET
        target = client.target(this.uri);
            target = target.path(MessageServiceRest.PATH).path("update");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
        
            r = target.request()
            .post(Entity.entity(allMessages, MediaType.APPLICATION_JSON));

    //UPDATE USER INBOXES
        //GET
        target = client.target(this.primaryUri);
            target = target.path(MessageServiceRest.PATH).path("update").path("mbox");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
            
            r = target.request()
            .accept(MediaType.APPLICATION_JSON)
            .get();

        Map<String, Set<Long>> userInboxes = r.readEntity(new GenericType<Map<String,Set<Long>>>() {});
                
        //SET
        target = client.target(this.uri);
            target = target.path(MessageServiceRest.PATH).path("update").path("mbox");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
        
            r = target.request()
            .post(Entity.entity(userInboxes, MediaType.APPLICATION_JSON));

    //UPDATE USERS
        //GET
        target = client.target(this.primaryUri);
            target = target.path(UserServiceRest.PATH).path("update");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
            
            r = target.request()
            .accept(MediaType.APPLICATION_JSON)
            .get();

        Map<String, User> users = r.readEntity(new GenericType<Map<String,User>>() {});

        //SET
        target = client.target(this.uri);
            target = target.path(MessageServiceRest.PATH).path("update");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
        
            r = target.request()
            .post(Entity.entity(users, MediaType.APPLICATION_JSON));   
    }

    public void syncVersion(long version){
        if (version > this.getVersion() + 1) {
            Log.info("Version Mismatch: Got " + Long.toString(version) + ". Local " + Long.toString(this.getVersion()));
            WebTarget target = client.target(primaryUri);
            target = target.path(MessageServiceRest.PATH).path("replica");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
            
            Response r = target.request()
            .header(ReplicaMessageServiceRest.HEADER_VERSION, this.getVersion())
            .accept(MediaType.APPLICATION_JSON)
            .get();

            if(r.getStatus() == Status.ACCEPTED.getStatusCode()){
                Log.info("Got updated ops");
                List<Operation> updatedOperations = r.readEntity(new GenericType<List<Operation>>() {});
                
                for (Operation operation : updatedOperations) {
                    processOperation(operation);
                    this.addOperation(operation);
                }
            }
            else if(r.getStatus() == Status.GONE.getStatusCode()){
                Log.info("Updating state");
                this.updateState();
                this.version = version;
            }
		}
    }
    
    //OPERATIONS
    public void addOperation(Operation op){            
        if(!this.ops.isEmpty())
            purgeList();
            
        ops.add(op);

        this.version++;
        sync.setResult(this.getVersion(), null);
    }


    public List<Operation> getOperations(long version){
        return ops.subList((int)(version - this.getHeadVersion()), ops.size() - 1);
    }

    

    public void purgeList(){
        Long currentTime = System.currentTimeMillis();
        while(ops.size() > 1 && currentTime - ops.get(0).getCreationTime() >= TTL)
            ops.remove(0);
    }
    
    //VERSIONS
    public long getVersion(){
        return this.version;
    }

    public long getHeadVersion(){
        return this.version - (this.ops.size() > 1 ? this.ops.size() : 0);
    }  
    
    public void waitForVersion(){
        synchronized(this.awaitingVersion){
            this.awaitingVersion++;
            sync.waitForVersion(this.awaitingVersion);
        }
    }

    public boolean isPrimary(){
        return this.isPrimary;
    }

    public String getPrimaryUri(){
        return this.primaryUri;
    }



    //METHODS CALLED BY THE RESOURCES UPON RECEIVING A REQUEST
        //USER_RESOURCE

        private void execPostUser(String uri, User user) throws ProcessingException {
            WebTarget target = client.target(uri);
            target = target.path(UserServiceRest.PATH).path("replica");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
            
            Response r = target.request()
            .header(ReplicaMessageServiceRest.HEADER_VERSION, this.getVersion())
            .post(Entity.entity(user, MediaType.APPLICATION_JSON));
            
            if(r.getStatus() != Status.NO_CONTENT.getStatusCode()){
                Log.info("execPostUser: failed");
                Log.info(String.valueOf(r.getStatus()));
            }
        }

        public boolean postuser(User user) {   
            int failedReplications = 0;

            for(String child : this.childrenList){
                String uri = this.getReplicaUri(child);
               
                try{
                    this.execPostUser(uri, user);
                }catch(ProcessingException e){
                    failedReplications++;
                }
            }

            return failedReplications <= this.maxReplicaFailures;
        }

        private void execUpdateUSer(String uri, String name, User user) throws ProcessingException {
            WebTarget target = client.target(uri);
            target = target.path(UserServiceRest.PATH).path(name).path("replica");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
            
            Response r = target.request()
            .header(ReplicaMessageServiceRest.HEADER_VERSION, this.getVersion())
            .put(Entity.entity(user, MediaType.APPLICATION_JSON));
            
            if(r.getStatus() != Status.NO_CONTENT.getStatusCode()){
                Log.info("execUpdateUser: failed");
                Log.info(String.valueOf(r.getStatus()));
            }
        }

        public boolean updateUser(String name, User user){
            int failedReplications = 0;
            
            for(String child : this.childrenList){
                String uri = this.getReplicaUri(child);
                
                try{
                    this.execUpdateUSer(uri, name, user);
                }catch(ProcessingException e){
                    failedReplications++;
                }
            }

            return failedReplications <= this.maxReplicaFailures;
        }

        private void execDeleteUser(String uri, String name) throws ProcessingException{
            WebTarget target = client.target(uri);
            target = target.path(UserServiceRest.PATH).path(name).path("replica");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
            
            Response r = target.request()
            .header(ReplicaMessageServiceRest.HEADER_VERSION, this.getVersion())
            .delete();
            
            if(r.getStatus() != Status.NO_CONTENT.getStatusCode()){
                Log.info("execDeleteUser: failed");
                Log.info(String.valueOf(r.getStatus()));
            }
        }

        public boolean deleteUser(String name){   
            int failedReplications = 0;
            
            for(String child : this.childrenList){
                String uri = this.getReplicaUri(child);
                
               try {
                   this.execDeleteUser(uri, name);
               } catch (ProcessingException e) {
                   failedReplications++;
               }
            }

            return failedReplications <= this.maxReplicaFailures;
        }

        //MESSAGE_RESOURCE
        private void execPostMessage(String uri, Message msg) throws ProcessingException{
            WebTarget target = client.target(uri);
            target = target.path(MessageServiceRest.PATH).path("replica");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
            
            Response r = target.request()
            .header(ReplicaMessageServiceRest.HEADER_VERSION, this.getVersion())
            .post(Entity.entity(msg, MediaType.APPLICATION_JSON));
            
            if(r.getStatus() != Status.NO_CONTENT.getStatusCode()){
                Log.info("execPostMessage: failed");
                Log.info(String.valueOf(r.getStatus()));
            }
        }

        public boolean postMessage(Message msg){     
            int failedReplications = 0;
            
            for(String child : this.childrenList){
                String uri = this.getReplicaUri(child);
                
               try{
                    this.execPostMessage(uri, msg);
               }catch(ProcessingException e){
                    failedReplications++;
               }
            }

            return failedReplications <= this.maxReplicaFailures;
        }

        private void execDeleteMessage(String uri, String name, long mid) throws ProcessingException {
            WebTarget target = client.target(uri);
            target = target.path(MessageServiceRest.PATH).path("msg").path(name).path(Long.toString(mid)).path("replica");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
            
            Response r = target.request()
            .header(ReplicaMessageServiceRest.HEADER_VERSION, this.getVersion())
            .delete();
            
            if(r.getStatus() != Status.NO_CONTENT.getStatusCode()){
                Log.info("execDeleteMessage: failed");
                Log.info(String.valueOf(r.getStatus()));
            }
        }

        public boolean deleteMessage(String name, long mid){  
            short failedReplications = 0;
            
            for(String child : this.childrenList){
                String uri = this.getReplicaUri(child);
                
                try {
                    this.execDeleteMessage(uri, name, mid); 
                } catch (ProcessingException e) {
                    failedReplications++;
                }
            }

            return failedReplications <= this.maxReplicaFailures;
        }

        private void execRemoveFromUserInbox(String uri, String name, long mid) throws ProcessingException {
            WebTarget target = client.target(uri);
            target = target.path(MessageServiceRest.PATH).path("mbox").path(name).path(Long.toString(mid)).path("replica");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
            
            Response r = target.request()
            .header(ReplicaMessageServiceRest.HEADER_VERSION, this.getVersion())
            .delete();
            
            if(r.getStatus() != Status.NO_CONTENT.getStatusCode()){
                Log.info("execRemoveFromUserInbox: failed");
                Log.info(String.valueOf(r.getStatus()));
            }
        }

        public boolean removeFromUserInbox(String name, long mid){   
            short failedReplications = 0;
            
            for(String child : this.childrenList){
                String uri = this.getReplicaUri(child);
                
                try {
                    this.execRemoveFromUserInbox(uri, name, mid);                    
                } catch (ProcessingException e) {
                    failedReplications++;
                }
            }

            return failedReplications <= this.maxReplicaFailures;
        }

        private List<String> execPostforwardedMessage(String uri, Message msg) throws ProcessingException {
            WebTarget target = client.target(uri);
            target = target.path(MessageServiceRest.PATH).path("mbox").path("replica");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
            
            Response r = target.request()
            .header(ReplicaMessageServiceRest.HEADER_VERSION, this.getVersion())
            .post(Entity.entity(msg, MediaType.APPLICATION_JSON));
            
            if(r.getStatus() != Status.OK.getStatusCode()){
                Log.info("execPostForwardedMessage: failed");
                Log.info(String.valueOf(r.getStatus()));
            }
            
            return r.readEntity(new GenericType<List<String>>(){});
        }

        public List<String> postForwardedMessage(Message msg){
            int failedReplications = 0;
            
            List<String> failedDeliveries = new LinkedList<>();

            for(String child : this.childrenList){
                String uri = this.getReplicaUri(child);

                try {
                    failedDeliveries = this.execPostforwardedMessage(uri, msg);
                } catch (ProcessingException e) {
                    failedReplications++;
                }
            }
                 
            if(failedReplications <= this.maxReplicaFailures)
                return failedDeliveries;
            else
                return null;
        }

        private void execDeleteForwardedMessage(String uri, long mid) throws ProcessingException {
            WebTarget target = client.target(uri);
            target = target.path(MessageServiceRest.PATH).path("msg").path(Long.toString(mid)).path("replica");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
            
            Response r = target.request()
            .header(ReplicaMessageServiceRest.HEADER_VERSION, this.getVersion())
            .delete();
            
            if(r.getStatus() != Status.NO_CONTENT.getStatusCode()){
                Log.info("execDeleteForwardedMessage: failed");
                Log.info(String.valueOf(r.getStatus()));
            }
        }

        public boolean deleteForwardedMessage(long mid){   
            int failedReplications = 0;
            
            for(String child : this.childrenList){
                String uri = this.getReplicaUri(child);
                
                try{
                    this.execDeleteForwardedMessage(uri, mid);
                }catch(ProcessingException e){
                    failedReplications++;
                }
            }

            return failedReplications <= this.maxReplicaFailures;
        }
}