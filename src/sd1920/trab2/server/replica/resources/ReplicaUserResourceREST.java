package sd1920.trab2.server.replica.resources;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.inject.Singleton;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import sd1920.trab2.api.User;
import sd1920.trab2.api.replicaRest.ReplicaMessageServiceRest;
import sd1920.trab2.api.replicaRest.ReplicaUserServiceRest;

import sd1920.trab2.server.serverUtils.LocalServerUtils;
import sd1920.trab2.server.serverUtils.ServerUtils;
import sd1920.trab2.server.replica.ReplicaMailServerREST;
import sd1920.trab2.server.replica.utils.Operation;

@Singleton
public class ReplicaUserResourceREST implements ReplicaUserServiceRest {

    private Map<String, User> users;

    private ClientConfig config;

    private Client client;

    private String serverRestUri;

    private sd1920.trab2.server.replica.utils.VersionControl vc;

    private static Logger Log = Logger.getLogger(ReplicaMailServerREST.class.getName());

    public ReplicaUserResourceREST() throws UnknownHostException {
        this.config = new ClientConfig();
		this.config.property(ClientProperties.CONNECT_TIMEOUT, LocalServerUtils.TIMEOUT);
		this.config.property(ClientProperties.READ_TIMEOUT, LocalServerUtils.TIMEOUT);

		this.client = ClientBuilder.newClient(config);
        this.users = new HashMap<String, User>();

		this.serverRestUri = String.format("https://%s:%d/rest",InetAddress.getLocalHost().getHostAddress(),ReplicaMailServerREST.PORT);
    
        this.vc = ReplicaMailServerREST.vc;
    }

    protected boolean createUserInbox(String userName){
        boolean error = true;

        Log.info("createUserInbox: Sending request to create a new inbox in MessageResource.");

        int tries = 0;

        WebTarget target = client.target(serverRestUri).path(ReplicaMessageResourceREST.PATH).path("/mbox");

        while(error && tries< LocalServerUtils.N_TRIES){
            error = false;

            try{
                target = target.path(userName).queryParam("secret", ReplicaMailServerREST.secret);
                target.request().head();
            }
            catch(ProcessingException e){
                Log.info("createUserInbox: Failed to send request to MessageResource. Retrying...");
                error = true;
            }
        }

        if(error)
            Log.info("createUserInbox: Failed to repeatedly send request to MessageResource. Giving up...");
        else
            Log.info("createUserInbox: Successfully sent request to MessageResource. More successful than i'll ever be!");		
        
            return error;
        }

    @Override
    public void execPostUser(Long version, User user, String secret) {
        Log.info("execPostUser");
        String name = user.getName();

        if (!secret.equals(ReplicaMailServerREST.secret)) {
			Log.info("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
        }
        
        vc.syncVersion(version);

        synchronized(this.users){
            this.users.put(name, user);
        }
        
        if(this.createUserInbox(name)){
            Log.info("postUser: User creation failed due to unresponsive MessageResource");
            throw new WebApplicationException(Status.CONFLICT);
        }

        vc.addOperation(new Operation(Operation.Type.POST_USER, user));

        Log.info("postUser: Created new user with name: " + name);
    }
        
    @Override
    public String postUser(User user) {
        String serverDomain = null;

        if (!vc.isPrimary()){
			String redirectPath = String.format(ServerUtils.POST_USER_FORMAT, vc.getPrimaryUri());
			Log.info("FORWARDING TO PRIMARY: " + URI.create(redirectPath) + " FOR USER");
			throw new WebApplicationException(Response.temporaryRedirect(URI.create(redirectPath)).build());
		}

        try {
            serverDomain = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            Log.info("What the frog");
        }
        
        String name = user.getName();
        
        if(name == null || name.equals("") || 
        user.getPwd() == null || user.getPwd().equals("") || 
        user.getDomain() == null || user.getDomain().equals("")){
            Log.info("postUser: User creation was rejected due to lack of name, pwd or domain.");
            throw new WebApplicationException(Status.CONFLICT);
        }
        else if(!user.getDomain().equals(serverDomain)){
            Log.info("postUser: User creation was rejected due to mismatch between the provided domain and the server domain");
            throw new WebApplicationException(Status.FORBIDDEN);
        }
        
        synchronized(this.users){
            if(this.users.containsKey(name)){
                Log.info("postUser: User creation was rejected due to the user already existing");
                throw new WebApplicationException(Status.CONFLICT);
            }
        }

        vc.waitForVersion();

        vc.postuser(user);
        
        throw new WebApplicationException(Response.status(200).
            header(ReplicaMessageServiceRest.HEADER_VERSION, vc.getVersion()).
            entity(String.format("%s@%s", name, user.getDomain())).build());
    }

    @Override
    public User getUser(String name, String pwd) {
        if (!vc.isPrimary()){
			String redirectPath = String.format(ServerUtils.GET_USER_FORMAT, vc.getPrimaryUri(), name);
			redirectPath = UriBuilder.fromPath(redirectPath).queryParam("pwd", pwd).toString();
			Log.info("FORWARDING TO PRIMARY: " + URI.create(redirectPath) + " FOR USER " + name);
			throw new WebApplicationException(Response.temporaryRedirect(URI.create(redirectPath)).build());
		}

        User user;

        if(name == null || name.equals("")){
            Log.info("getUser: User fetch was rejected due to invalid parameters");
            throw new WebApplicationException(Status.CONFLICT);
        }

        synchronized(this.users){
            user = this.users.get(name);
        }

        if(user == null){
            Log.info("getUser: User fetch was rejected due to missing user");
            throw new WebApplicationException(Status.FORBIDDEN);
        }else if(pwd == null || !user.getPwd().equals(pwd)){
            Log.info("getUser: User fetch was rejected due to an invalid password");
            throw new WebApplicationException(Status.FORBIDDEN);
        }else{
            return user;
        }
    }
    
    @Override
    public void execUpdateUser(Long version, String name, User user, String secret) {
        if (!secret.equals(ReplicaMailServerREST.secret)) {
			Log.info("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
        }
        
        vc.syncVersion(version);

        synchronized(this.users){
            User existingUser = this.users.get(name);

            existingUser.setDisplayName(user.getDisplayName() == null ? existingUser.getDisplayName() : user.getDisplayName());
            
            existingUser.setPwd(user.getPwd() == null ? existingUser.getPwd() : user.getPwd());
        }

        List<Object> args = new LinkedList<>();

        args.add(name);
        args.add(user);

        vc.addOperation(new Operation(Operation.Type.UPDATE_USER, args));
    }

    @Override
    public User updateUser(String name, String pwd, User user) {

        if (!vc.isPrimary()){
			String redirectPath = String.format(ServerUtils.UPDATE_USER_FORMAT, vc.getPrimaryUri(), name);
			Log.info("FORWARDING TO PRIMARY: " + URI.create(redirectPath) + " FOR NAME");
			throw new WebApplicationException(Response.temporaryRedirect(URI.create(redirectPath)).build());
		}

        User existingUser;
        
        if(name == null || name.equals("")){
            Log.info("updateUser: User update was rejected due to invalid parameters");
            throw new WebApplicationException(Status.CONFLICT);
        }
        
        synchronized(this.users){
            existingUser = this.users.get(name);
            
            if(existingUser == null){
                Log.info("updateUser: User update was rejected due to a missing user");
                throw new WebApplicationException(Status.FORBIDDEN);
            }else if(!existingUser.getPwd().equals(pwd)){
                Log.info("updateUser: User update was rejected due to an invalid password");
                throw new WebApplicationException(Status.FORBIDDEN);
            }
        }

        vc.waitForVersion();

        vc.updateUser(name, user);

        throw new WebApplicationException(Response.status(200).
            header(ReplicaMessageServiceRest.HEADER_VERSION, vc.getVersion()).
            entity(existingUser).build());
    }
    
    @Override
    public void execDeleteUser(Long version, String name, String secret) {
        if (!secret.equals(ReplicaMailServerREST.secret)) {
			Log.info("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
        }
        
        vc.syncVersion(version);
        
        synchronized(this.users){
            this.users.remove(name);
        }

        vc.addOperation(new Operation(Operation.Type.DELETE_USER, name));
    }

    @Override
    public User deleteUser(String name, String pwd) {

        if (!vc.isPrimary()){
			String redirectPath = String.format(ServerUtils.DELETE_USER_FORMAT, vc.getPrimaryUri(), name);
			Log.info("FORWARDING TO PRIMARY: " + URI.create(redirectPath) + " FOR USER");
			throw new WebApplicationException(Response.temporaryRedirect(URI.create(redirectPath)).build());
		}

        User user;

        if(name == null || name.equals("")){
            Log.info("deleteUser: User deletion was rejected due to invalid parameters");
            throw new WebApplicationException(Status.CONFLICT);
        }

        synchronized(this.users){
            user = this.users.get(name);
            
            if(user == null){
                Log.info("deleteUser: User deletion was rejected due to a missing user");
                throw new WebApplicationException(Status.FORBIDDEN);
            }else if(pwd == null || !user.getPwd().equals(pwd)){
                Log.info("deleteUser: User update was rejected due to an invalid password");
                throw new WebApplicationException(Status.FORBIDDEN);
            }
        }

        vc.waitForVersion();

        vc.deleteUser(name);

        throw new WebApplicationException(Response.status(200).
            header(ReplicaMessageServiceRest.HEADER_VERSION, vc.getVersion()).
            entity(user).build());
    }


    @Override
    public Map<String, User> getUsers(String secret) {
        if (!secret.equals(ReplicaMailServerREST.secret)) {
			Log.info("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
        }
        
        return this.users;
    }

    @Override
    public void updateUsers(Map<String, User> users, String secret) {
        if (!secret.equals(ReplicaMailServerREST.secret)) {
			Log.info("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
        }
        
        this.users = users;
    }
}