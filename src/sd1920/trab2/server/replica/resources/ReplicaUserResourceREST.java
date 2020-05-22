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
import sd1920.trab2.api.rest.ReplicaUserServiceRest;
import sd1920.trab2.replication.Operation;
import sd1920.trab2.replication.VersionControl;
import sd1920.trab2.server.serverUtils.LocalServerUtils;
import sd1920.trab2.server.serverUtils.ServerUtils;
import sd1920.trab2.server.replica.ReplicaMailServerREST;

@Singleton
public class ReplicaUserResourceREST implements ReplicaUserServiceRest {

    private Map<String, User> users;

    private ClientConfig config;

    private Client client;

    private String serverRestUri;

    private VersionControl vc;

    private static Logger Log = Logger.getLogger(ReplicaUserResourceREST.class.getName());

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

        System.out.println("createUserInbox: Sending request to create a new inbox in MessageResource.");

        int tries = 0;

        WebTarget target = client.target(serverRestUri).path(ReplicaMessageResourceREST.PATH).path("/mbox");

        while(error && tries< LocalServerUtils.N_TRIES){
            error = false;

            try{
                target = target.path(userName).queryParam("secret", ReplicaMailServerREST.secret);
                target.request().head();
            }
            catch(ProcessingException e){
                System.out.println("createUserInbox: Failed to send request to MessageResource. Retrying...");
                error = true;
            }
        }

        if(error)
            System.out.println("createUserInbox: Failed to repeatedly send request to MessageResource. Giving up...");
        else
            System.out.println("createUserInbox: Successfully sent request to MessageResource. More successful than i'll ever be!");		
        
            return error;
        }

    @Override
    public void execPostUser(Long version, User user, String secret) {
        System.out.println("execPostUser");
        String name = user.getName();

        if (!secret.equals(ReplicaMailServerREST.secret)) {
			System.out.println("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
        }
        
        vc.syncVersion(version);

        synchronized(this.users){
            this.users.put(name, user);
        }
        
        if(this.createUserInbox(name)){
            System.out.println("postUser: User creation failed due to unresponsive MessageResource");
            throw new WebApplicationException(Status.CONFLICT);
        }

        vc.addOperation(new Operation(Operation.Type.POST_USER, user));

        System.out.println("postUser: Created new user with name: " + name);
    }
        
    @Override
    public String postUser(User user) {
        String serverDomain = null;

        if (!vc.isPrimary()){
			String redirectPath = String.format(ServerUtils.POST_USER_FORMAT, vc.getPrimaryUri());
			System.out.println("FORWARDING TO PRIMARY: " + URI.create(redirectPath) + " FOR USER");
			throw new WebApplicationException(Response.temporaryRedirect(URI.create(redirectPath)).build());
		}

        try {
            serverDomain = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            System.out.println("What the frog");
        }
        
        String name = user.getName();
        
        if(name == null || name.equals("") || 
        user.getPwd() == null || user.getPwd().equals("") || 
        user.getDomain() == null || user.getDomain().equals("")){
            System.out.println("postUser: User creation was rejected due to lack of name, pwd or domain.");
            throw new WebApplicationException(Status.CONFLICT);
        }
        else if(!user.getDomain().equals(serverDomain)){
            System.out.println("postUser: User creation was rejected due to mismatch between the provided domain and the server domain");
            throw new WebApplicationException(Status.FORBIDDEN);
        }
        
        synchronized(this.users){
            if(this.users.containsKey(name)){
                System.out.println("postUser: User creation was rejected due to the user already existing");
                throw new WebApplicationException(Status.CONFLICT);
            }
        }

        vc.waitForVersion();

        vc.postuser(user);
        
        return String.format("%s@%s", name, user.getDomain());
    }

    @Override
    public User getUser(String name, String pwd) {
        if (!vc.isPrimary()){
			String redirectPath = String.format(ServerUtils.GET_USER_FORMAT, vc.getPrimaryUri(), name);
			redirectPath = UriBuilder.fromPath(redirectPath).queryParam("pwd", pwd).toString();
			System.out.println("FORWARDING TO PRIMARY: " + URI.create(redirectPath) + " FOR USER " + name);
			throw new WebApplicationException(Response.temporaryRedirect(URI.create(redirectPath)).build());
		}

        User user;

        if(name == null || name.equals("")){
            System.out.println("getUser: User fetch was rejected due to invalid parameters");
            throw new WebApplicationException(Status.CONFLICT);
        }

        synchronized(this.users){
            user = this.users.get(name);
        }

        if(user == null){
            System.out.println("getUser: User fetch was rejected due to missing user");
            throw new WebApplicationException(Status.FORBIDDEN);
        }else if(pwd == null || !user.getPwd().equals(pwd)){
            System.out.println("getUser: User fetch was rejected due to an invalid password");
            throw new WebApplicationException(Status.FORBIDDEN);
        }else{
            return user;
        }
    }
    
    @Override
    public void execUpdateUser(Long version, String name, User user, String secret) {
        if (!secret.equals(ReplicaMailServerREST.secret)) {
			System.out.println("An intruder!");
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
			System.out.println("FORWARDING TO PRIMARY: " + URI.create(redirectPath) + " FOR NAME");
			throw new WebApplicationException(Response.temporaryRedirect(URI.create(redirectPath)).build());
		}

        User existingUser;
        
        if(name == null || name.equals("")){
            System.out.println("updateUser: User update was rejected due to invalid parameters");
            throw new WebApplicationException(Status.CONFLICT);
        }
        
        synchronized(this.users){
            existingUser = this.users.get(name);
            
            if(existingUser == null){
                System.out.println("updateUser: User update was rejected due to a missing user");
                throw new WebApplicationException(Status.FORBIDDEN);
            }else if(!existingUser.getPwd().equals(pwd)){
                System.out.println("updateUser: User update was rejected due to an invalid password");
                throw new WebApplicationException(Status.FORBIDDEN);
            }
        }

        vc.waitForVersion();

        vc.updateUser(name, user);

        return existingUser;
    }
    
    @Override
    public void execDeleteUser(Long version, String name, String secret) {
        if (!secret.equals(ReplicaMailServerREST.secret)) {
			System.out.println("An intruder!");
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
			System.out.println("FORWARDING TO PRIMARY: " + URI.create(redirectPath) + " FOR USER");
			throw new WebApplicationException(Response.temporaryRedirect(URI.create(redirectPath)).build());
		}

        User user;

        if(name == null || name.equals("")){
            System.out.println("deleteUser: User deletion was rejected due to invalid parameters");
            throw new WebApplicationException(Status.CONFLICT);
        }

        synchronized(this.users){
            user = this.users.get(name);
            
            if(user == null){
                System.out.println("deleteUser: User deletion was rejected due to a missing user");
                throw new WebApplicationException(Status.FORBIDDEN);
            }else if(pwd == null || !user.getPwd().equals(pwd)){
                System.out.println("deleteUser: User update was rejected due to an invalid password");
                throw new WebApplicationException(Status.FORBIDDEN);
            }
        }

        vc.waitForVersion();

        vc.deleteUser(name);

        return user;
    }


    @Override
    public Map<String, User> getUsers(String secret) {
        if (!secret.equals(ReplicaMailServerREST.secret)) {
			System.out.println("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
        }
        
        return this.users;
    }

    @Override
    public void updateUsers(Map<String, User> users, String secret) {
        if (!secret.equals(ReplicaMailServerREST.secret)) {
			System.out.println("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
        }
        
        this.users = users;
    }
}