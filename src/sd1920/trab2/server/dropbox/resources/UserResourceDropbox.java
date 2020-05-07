package sd1920.trab2.server.dropbox.resources;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Logger;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response.Status;

import sd1920.trab2.api.User;
import sd1920.trab2.server.dropbox.DropboxMailServer;
import sd1920.trab2.server.dropbox.requests.CreateFile;
import sd1920.trab2.server.dropbox.requests.SearchFile;
import sd1920.trab2.server.rest.resources.UserResourceRest;

public class UserResourceDropbox extends UserResourceRest {

    private static Logger Log = Logger.getLogger(UserResourceDropbox.class.getName());

    public UserResourceDropbox() throws UnknownHostException{
        super();
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

            String directoryPath = DropboxMailServer.hostname + "/users/";

            if(!SearchFile.run(directoryPath, user.getName())){
                Log.info("postUser: User creation was rejected due to the user already existing");
                throw new WebApplicationException(Status.CONFLICT);
            }

            CreateFile.run(directoryPath + user.getName(), user);
            
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