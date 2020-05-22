package sd1920.trab2.replication;

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
import sd1920.trab2.api.rest.MessageServiceRest;
import sd1920.trab2.api.rest.UserServiceRest;
import sd1920.trab2.server.dropbox.ProxyMailServer;
import sd1920.trab2.server.replica.ReplicaMailServerREST;
import sd1920.trab2.server.serverUtils.ServerUtils;

public class VersionControl {

    private static Logger Log = Logger.getLogger(ProxyMailServer.class.getName());

    public static final String HEADER_VERSION = "Msgserver-version";

    private static final String NODE_FORMAT = "%s/%s";
    private static final long TTL = 8000;
    private boolean isPrimary;

    private ZookeeperProcessor zk;
    private String domain;
    private String uri;
    private Long version;
    private String primaryUri;
    private SyncPoint sync;
    private Long awaitingVersion;
    private int lastChildrenCount;

    private Client client;

    private List<String> childrenList;

    private List<Operation> ops;

    public VersionControl(String domain, String uri) throws Exception {
        this.domain = "/" + domain;
        this.uri = uri;
        this.version = 0L;
        this.awaitingVersion = new Long(-1);
        this.isPrimary = false;
        this.childrenList = new LinkedList<>();
        this.zk = new ZookeeperProcessor("kafka:2181");
        this.lastChildrenCount = 0;

        ClientConfig config = new ClientConfig();
		config.property(ClientProperties.CONNECT_TIMEOUT, ServerUtils.TIMEOUT);
        config.property(ClientProperties.READ_TIMEOUT, ServerUtils.TIMEOUT);

        client = ClientBuilder.newClient(config);

        String newPath = zk.write(this.domain, CreateMode.PERSISTENT);

        this.startListening();
		
		newPath = zk.write(this.domain + "/server_", uri, CreateMode.EPHEMERAL_SEQUENTIAL);
        System.out.println("Created child znode: " + newPath);
        
        sync = SyncPoint.getInstance();

        ops = new LinkedList<>();
    }
    
    public void addOperation(Operation op){
        System.out.println("Adding operation " + op.getType().toString());

        if(!this.ops.isEmpty())
            purgeList();
            
        ops.add(op);

        synchronized(this.version){
            this.version++;
            sync.setResult(this.getVersion(), null);
        }
    }

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

                //if(!isPrimary && lastChildrenCount < childrenList.size())


                lastChildrenCount = childrenList.size();
                primaryUri = puri;
			}
		});
    }

    public List<Operation> getOperations(long version){
        return ops.subList((int)(version - this.getHeadVersion()), ops.size() - 1);
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
        System.out.println("All Messages size: " + allMessages.size());
        //SET
        target = client.target(this.primaryUri);
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
        System.out.println("All userInboxes size: " + userInboxes.size());
        
        //SET
        target = client.target(this.primaryUri);
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
        System.out.println("All users size: " + users.size());
        //SET
        target = client.target(this.primaryUri);
            target = target.path(MessageServiceRest.PATH).path("update");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
        
            r = target.request()
            .post(Entity.entity(users, MediaType.APPLICATION_JSON));   
    }

    public void syncVersion(long version){
        if (version > this.getVersion() + 1) {
            System.out.println("Version Mismatch: Got " + Long.toString(version) + ". Local " + Long.toString(this.getVersion()));
            WebTarget target = client.target(primaryUri);
            target = target.path(MessageServiceRest.PATH).path("replica");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
            
            Response r = target.request()
            .header(HEADER_VERSION, this.getVersion())
            .accept(MediaType.APPLICATION_JSON)
            .get();

            if(r.getStatus() == Status.ACCEPTED.getStatusCode()){
                System.out.println("Got updated ops");
                List<Operation> updatedOperations = r.readEntity(new GenericType<List<Operation>>() {});
                
                for (Operation operation : updatedOperations) {
                    processOperation(operation);
                    this.addOperation(operation);
                }
            }
            else if(r.getStatus() == Status.GONE.getStatusCode()){
                System.out.println("Updating state");
                this.updateState();
                this.version = version;
            }
		}
    }

    public void purgeList(){
        Long currentTime = System.currentTimeMillis();
        while(currentTime - ops.get(0).getCreationTime() >= TTL)
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


    //METHODS CALLED BY THE RESOURCES UPON RECEIVING A REQUEST
        //USER_RESOURCE

        private void execPostUser(String uri, User user) throws ProcessingException {
            WebTarget target = client.target(uri);
            target = target.path(UserServiceRest.PATH).path("replica");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
            
            Response r = target.request()
            .header(HEADER_VERSION, this.getVersion())
            .post(Entity.entity(user, MediaType.APPLICATION_JSON));
            
            if(r.getStatus() != Status.NO_CONTENT.getStatusCode()){
                System.out.println("execPostUser: Failed exec");
                System.out.println(String.valueOf(r.getStatus()));
            }
        }

        public void postuser(User user){   
            short failedReplications = 0;

            for(String child : this.childrenList){
                String uri = this.getReplicaUri(child);
               
                try{
                    this.execPostUser(uri, user);
                }catch(ProcessingException e){
                    failedReplications++;
                }
            }


        }

        private void execUpdateUSer(String uri, String name, User user) throws ProcessingException {
            WebTarget target = client.target(uri);
            target = target.path(UserServiceRest.PATH).path(name).path("replica");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
            
            Response r = target.request()
            .header(HEADER_VERSION, this.getVersion())
            .put(Entity.entity(user, MediaType.APPLICATION_JSON));
            
            if(r.getStatus() != Status.NO_CONTENT.getStatusCode()){
                System.out.println("execUpdateUser: Failed execUpdate");
                System.out.println(String.valueOf(r.getStatus()));
            }
        }

        public void updateUser(String name, User user){
            short failedReplications = 0;
            
            for(String child : this.childrenList){
                String uri = this.getReplicaUri(child);
                
                try{
                    this.execUpdateUSer(uri, name, user);
                }catch(ProcessingException e){
                    failedReplications++;
                }
            }
        }

        private void execDeleteUser(String uri, String name) throws ProcessingException{
            WebTarget target = client.target(uri);
            target = target.path(UserServiceRest.PATH).path(name).path("replica");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
            
            Response r = target.request()
            .header(HEADER_VERSION, this.getVersion())
            .delete();
            
            if(r.getStatus() != Status.NO_CONTENT.getStatusCode()){
                System.out.println("execDeleteUser: Failed delete user");
                System.out.println(String.valueOf(r.getStatus()));
            }
        }

        public void deleteUser(String name){   
            short failedReplications = 0;
            
            for(String child : this.childrenList){
                String uri = this.getReplicaUri(child);
                
               try {
                   this.execDeleteUser(uri, name);
               } catch (ProcessingException e) {
                   failedReplications++;
               }
               
            }
        }

        private void execPostMessage(String uri, Message msg) throws ProcessingException{
            WebTarget target = client.target(uri);
            target = target.path(MessageServiceRest.PATH).path("replica");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
            
            Response r = target.request()
            .header(HEADER_VERSION, this.getVersion())
            .post(Entity.entity(msg, MediaType.APPLICATION_JSON));
            
            if(r.getStatus() != Status.NO_CONTENT.getStatusCode()){
                System.out.println("execPostMessage: failed post message");
                System.out.println(String.valueOf(r.getStatus()));
            }
        }

        //MESSAGE_RESOURCE
        public void postMessage(Message msg){     
            short failedReplications = 0;
            
            for(String child : this.childrenList){
                String uri = this.getReplicaUri(child);
                
               try{
                    this.execPostMessage(uri, msg);
               }catch(ProcessingException e){
                    failedReplications++;
               }
            }
        }

        private void execDeleteMessage(String uri, String name, long mid) throws ProcessingException {
            WebTarget target = client.target(uri);
            target = target.path(MessageServiceRest.PATH).path("msg").path(name).path(Long.toString(mid)).path("replica");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
            
            Response r = target.request()
            .header(HEADER_VERSION, this.getVersion())
            .delete();
            
            if(r.getStatus() != Status.NO_CONTENT.getStatusCode()){
                System.out.println("execDeleteMessage: failed delete message");
                System.out.println(String.valueOf(r.getStatus()));
            }
        }

        public void deleteMessage(String name, long mid){  
            short failedReplications = 0;
            
            for(String child : this.childrenList){
                String uri = this.getReplicaUri(child);
                
                try {
                    this.execDeleteMessage(uri, name, mid); 
                } catch (ProcessingException e) {
                    failedReplications++;
                }


            }
        }

        private void execRemoveFromUserInbox(String uri, String name, long mid) throws ProcessingException {
            WebTarget target = client.target(uri);
            target = target.path(MessageServiceRest.PATH).path("mbox").path(name).path(Long.toString(mid)).path("replica");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
            
            Response r = target.request()
            .header(HEADER_VERSION, this.getVersion())
            .delete();
            
            if(r.getStatus() != Status.NO_CONTENT.getStatusCode()){
                System.out.println("execRemoveFromUserInbox: Failed remove from user inbox");
                System.out.println(String.valueOf(r.getStatus()));
            }
        }

        public void removeFromUserInbox(String name, long mid){   
            short failedReplications = 0;
            
            for(String child : this.childrenList){
                String uri = this.getReplicaUri(child);
                
                try {
                    this.execRemoveFromUserInbox(uri, name, mid);                    
                } catch (ProcessingException e) {
                    failedReplications++;
                }
                
            }
        }

        private List<String> execPostforwardedMessage(String uri, Message msg) throws ProcessingException {
            WebTarget target = client.target(uri);
            target = target.path(MessageServiceRest.PATH).path("mbox").path("replica");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
            
            Response r = target.request()
            .header(HEADER_VERSION, this.getVersion())
            .post(Entity.entity(msg, MediaType.APPLICATION_JSON));
            
            if(r.getStatus() != Status.OK.getStatusCode()){
                System.out.println("execPostForwardedMessage: failed post forwarded");
                System.out.println(String.valueOf(r.getStatus()));
            }
            
            return r.readEntity(new GenericType<List<String>>(){});
        }

        public List<String> postForwardedMessage(Message msg){
            List<String> failedDeliveries = new LinkedList<>();
            short failedReplications = 0;

            for(String child : this.childrenList){
                String uri = this.getReplicaUri(child);

                try {
                    failedDeliveries = this.execPostforwardedMessage(uri, msg);
                } catch (ProcessingException e) {
                    failedReplications++;
                }

            }
                        
            return failedDeliveries;
        }

        private void execDeleteForwardedMessage(String uri, long mid) throws ProcessingException {
            WebTarget target = client.target(uri);
            target = target.path(MessageServiceRest.PATH).path("msg").path(Long.toString(mid)).path("replica");
            target = target.queryParam("secret", ReplicaMailServerREST.secret);
            
            Response r = target.request()
            .header(HEADER_VERSION, this.getVersion())
            .delete();
            
            if(r.getStatus() != Status.NO_CONTENT.getStatusCode()){
                System.out.println("execDeleteForwardedMessage: Failed forward delete message");
                System.out.println(String.valueOf(r.getStatus()));
            }
        }

        public void deleteForwardedMessage(long mid){            
            for(String child : this.childrenList){
                String uri = this.getReplicaUri(child);
                
               this.execDeleteForwardedMessage(uri, mid);
            }
        }


}