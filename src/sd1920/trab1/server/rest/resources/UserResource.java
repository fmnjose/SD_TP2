package sd1920.trab1.server.rest.resources;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import sd1920.trab1.api.User;
import sd1920.trab1.api.rest.UserService;

public class UserResource implements UserService {

    private final Map<String, User> users = new HashMap<String, User>();

    private static Logger Log = Logger.getLogger(UserResource.class.getName());

    public UserResource(){
        
    }

    @Override
    public String postUser(User user) {
        // TODO Auto-generated method stub
        try{
            String serverDomain = InetAddress.getLocalHost().getHostName();
            String name = user.getName();

            if(name == null || user.getPwd() == null|| user.getDomain() == null){
                Log.info("Message was rejected due to lack of name, pwd or domain.");
                throw new WebApplicationException(Status.CONFLICT);
            }
            else if(!user.getDomain().equals(serverDomain)){
                Log.info("Message was rejected due to mismatch between the provided domain and the server domain");
                throw new WebApplicationException(Status.FORBIDDEN);
            }

            users.put(name, user);
            Log.info("Created new user with name: " + name);
            return String.format("%s@%s", name, user.getDomain());
        }
        catch(UnknownHostException e){
            //Do nothing
        }
    }

    @Override
    public User getUser(String name, String pwd) {
        // TODO Auto-generated method stub
        
        return null;
    }

    @Override
    public User updateUser(String name, String pwd, User user) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public User deleteUser(String name, String pwd) {
        // TODO Auto-generated method stub
        return null;
    }

}