package sd1920.trab2.server.dropbox.resources;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Logger;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;


import sd1920.trab2.api.User;
import sd1920.trab2.server.dropbox.DropboxMailServer;
import sd1920.trab2.server.dropbox.requests.CreateDirectory;
import sd1920.trab2.server.dropbox.requests.CreateFile;
import sd1920.trab2.server.dropbox.requests.Delete;
import sd1920.trab2.server.dropbox.requests.DownloadFile;
import sd1920.trab2.server.dropbox.requests.SearchFile;
import sd1920.trab2.server.rest.resources.UserResourceRest;

public class UserResourceDropbox extends UserResourceRest {

    public static final String USERS_DIR_FORMAT = "%s/users/";
    public static final String USER_DATA_DIR_FORMAT = "%s/users/%s/";
    public static final String USER_OBJECT_FILE_FORMAT = "%s/users/%s/user_object";
    public static final String USER_MSGS_FILE_FORMAT = "%s/users/%s/messages_list";

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

            String directoryPath = String.format(USERS_DIR_FORMAT, DropboxMailServer.hostname);

            if(!SearchFile.run(directoryPath, user.getName())){
                Log.info("postUser: User creation was rejected due to the user already existing");
                throw new WebApplicationException(Status.CONFLICT);
            }

            directoryPath = String.format(USER_DATA_DIR_FORMAT, DropboxMailServer.hostname, user.getName());

            if(!CreateDirectory.run(directoryPath))
                throw new WebApplicationException(Status.REQUEST_TIMEOUT);

            directoryPath = String.format(USER_OBJECT_FILE_FORMAT, DropboxMailServer.hostname, user.getName());
            CreateFile.run(directoryPath, user);
            
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

        String directoryPath = String.format(USER_OBJECT_FILE_FORMAT, DropboxMailServer.hostname, name);

        user = (User)DownloadFile.run(directoryPath);

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

        String filePath = String.format(USER_OBJECT_FILE_FORMAT, DropboxMailServer.hostname, name);

        
        existingUser = (User)DownloadFile.run(filePath);

        if(existingUser == null){
            Log.info("updateUser: User update was rejected due to a missing user");
            throw new WebApplicationException(Status.FORBIDDEN);
        }else if(!existingUser.getPwd().equals(pwd)){
            Log.info("updateUser: User update was rejected due to an invalid password");
            throw new WebApplicationException(Status.FORBIDDEN);
        }

        existingUser.setDisplayName(user.getDisplayName() == null ? existingUser.getDisplayName() : user.getDisplayName());

        existingUser.setPwd(user.getPwd() == null ? existingUser.getPwd() : user.getPwd());
        
        CreateFile.run(filePath, existingUser);

        return existingUser;
    }

    @Override
    public User deleteUser(String name, String pwd) {
        User user;

        if(name == null || name.equals("")){
            Log.info("deleteUser: User deletion was rejected due to invalid parameters");
            throw new WebApplicationException(Status.CONFLICT);
        }

        String filePath = String.format(USER_OBJECT_FILE_FORMAT, DropboxMailServer.hostname, name);

        user = (User)DownloadFile.run(filePath);
        
        if(user == null){
            Log.info("deleteUser: User deletion was rejected due to a missing user");
            throw new WebApplicationException(Status.FORBIDDEN);
        }else if(pwd == null || !user.getPwd().equals(pwd)){
            Log.info("deleteUser: User update was rejected due to an invalid password");
            throw new WebApplicationException(Status.FORBIDDEN);
        }
        
        Delete.run(filePath);
        
        return user;
    }

}