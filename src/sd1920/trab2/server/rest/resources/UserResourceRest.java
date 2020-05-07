package sd1920.trab2.server.rest.resources;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.inject.Singleton;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import sd1920.trab2.api.User;
import sd1920.trab2.api.rest.UserServiceRest;
import sd1920.trab2.server.serverUtils.ServerMessageUtils;
import sd1920.trab2.server.rest.RESTMailServer;

@Singleton
public class UserResourceRest implements UserServiceRest {

    private final Map<String, User> users = new HashMap<String, User>();

    private ClientConfig config;

    private Client client;

    private String serverRestUri;

    private static Logger Log = Logger.getLogger(UserResourceRest.class.getName());

    public UserResourceRest() throws UnknownHostException {
        this.config = new ClientConfig();
		this.config.property(ClientProperties.CONNECT_TIMEOUT, ServerMessageUtils.TIMEOUT);
		this.config.property(ClientProperties.READ_TIMEOUT, ServerMessageUtils.TIMEOUT);

		this.client = ClientBuilder.newClient(config);

		this.serverRestUri = String.format("https://%s:%d/rest",InetAddress.getLocalHost().getHostAddress(),RESTMailServer.PORT);
    }

    protected boolean createUserInbox(String userName){
        boolean error = true;

        Log.info("createUserInbox: Sending request to create a new inbox in MessageResource.");

        int tries = 0;

        WebTarget target = client.target(serverRestUri).path(MessageResourceRest.PATH).path("/mbox");

        while(error && tries< ServerMessageUtils.N_TRIES){
            error = false;

            try{
                target.path(userName).request().head();
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
    public String postUser(User user) {
        try{
            String serverDomain = InetAddress.getLocalHost().getHostName();

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
                this.users.put(name, user);
            }
            
            if(this.createUserInbox(name)){
                Log.info("postUser: User creation failed due to unresponsive MessageResource");
                throw new WebApplicationException(Status.CONFLICT);
            }

            Log.info("postUser: Created new user with name: " + name);
            
            return String.format("%s@%s", name, user.getDomain());
        }
        catch(UnknownHostException e){
            return null;
        }
    }

    @Override
    public User getUser(String name, String pwd) {
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
    public User updateUser(String name, String pwd, User user) {

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

            existingUser.setDisplayName(user.getDisplayName() == null ? existingUser.getDisplayName() : user.getDisplayName());

            existingUser.setPwd(user.getPwd() == null ? existingUser.getPwd() : user.getPwd());
        }

        return existingUser;
    }

    @Override
    public User deleteUser(String name, String pwd) {
        
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
            this.users.remove(name);
        }
        return user;
    }

}