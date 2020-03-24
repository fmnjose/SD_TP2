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
        try{
            String serverDomain = InetAddress.getLocalHost().getHostName();
            String name = user.getName();

            if(name == null || user.getPwd() == null|| user.getDomain() == null){
                Log.info("User creation was rejected due to lack of name, pwd or domain.");
                throw new WebApplicationException(Status.CONFLICT);
            }
            else if(!user.getDomain().equals(serverDomain)){
                Log.info("User creation was rejected due to mismatch between the provided domain and the server domain");
                throw new WebApplicationException(Status.FORBIDDEN);
            }
            synchronized(this.users){
                if(this.users.containsKey(name)){
                    Log.info("User creation was rejected due to the user already existing");
                    throw new WebApplicationException(Status.CONFLICT);
                }
                this.users.put(name, user);
            }
            Log.info("Created new user with name: " + name);
            return String.format("%s@%s", name, user.getDomain());
        }
        catch(UnknownHostException e){
            return null;
        }
    }

    @Override
    public User getUser(String name, String pwd) {

        User user;

        if(name == null || pwd == null){
            Log.info("User fetch was rejected due to invalid parameters");
            throw new WebApplicationException(Status.CONFLICT);
        }

        synchronized(this.users){
            user = this.users.get(name);
        }

        if(user == null){
            Log.info("User fetch was rejected due to missing user");
            throw new WebApplicationException(Status.CONFLICT);
        }else if(!user.getPwd().equals(pwd)){
            Log.info("User fetch was rejected due to an invalid password");
            throw new WebApplicationException(Status.CONFLICT);
        }else{
            return user;
        }
    }

    @Override
    public User updateUser(String name, String pwd, User user) {

        User existingUser;
        
        if(name == null){
            Log.info("User update was rejected due to invalid parameters");
            throw new WebApplicationException(Status.CONFLICT);
        }

        synchronized(this.users){
            existingUser = this.users.get(name);

            if(existingUser == null){
                Log.info("User update was rejected due to a missing user");
                throw new WebApplicationException(Status.CONFLICT);
            }else if(!existingUser.getPwd().equals(pwd)){
                Log.info("User update was rejected due to an invalid password");
                throw new WebApplicationException(Status.CONFLICT);
            }

            existingUser.setDisplayName(user.getDisplayName() == null ? existingUser.getDisplayName() : user.getDisplayName());

            existingUser.setPwd(user.getPwd() == null ? existingUser.getPwd() : user.getPwd());
        }

        return existingUser;
    }

    @Override
    public User deleteUser(String name, String pwd) {
        
        User user;

        if(name == null || pwd == null){
            Log.info("User deletion was rejected due to invalid parameters");
            throw new WebApplicationException(Status.CONFLICT);
        }

        synchronized(this.users){
            user = this.users.get(name);
            
            if(user == null){
                Log.info("User deletion was rejected due to a missing user");
                throw new WebApplicationException(Status.CONFLICT);
            }else if(!user.getPwd().equals(pwd)){
                Log.info("User update was rejected due to an invalid password");
                throw new WebApplicationException(Status.CONFLICT);
            }

            this.users.remove(name);
        }


        return user;
    }

}