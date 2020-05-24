package sd1920.trab2.server.proxy.resources;

import java.net.UnknownHostException;
import java.util.logging.Logger;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import com.google.gson.Gson;

import sd1920.trab2.api.User;
import sd1920.trab2.api.rest.UserServiceRest;
import sd1920.trab2.server.proxy.ProxyMailServer;
import sd1920.trab2.server.proxy.requests.CreateDirectory;
import sd1920.trab2.server.proxy.requests.Create;
import sd1920.trab2.server.proxy.requests.Delete;
import sd1920.trab2.server.proxy.requests.DownloadFile;
import sd1920.trab2.server.proxy.requests.GetMeta;

public class UserResourceProxy implements UserServiceRest {

    public static final String USERS_DIR_FORMAT = "/%s/users";
    public static final String USER_FOLDER_FORMAT = "/%s/users/%s";
    public static final String USER_DATA_FORMAT = USER_FOLDER_FORMAT + "/data";
    public static final String USER_INBOX_FOLDER = USER_FOLDER_FORMAT + "/inbox";
    public static final String USER_MESSAGE_FORMAT = USER_INBOX_FOLDER + "/%s";

    private static Logger Log = Logger.getLogger(ProxyMailServer.class.getName());

    private static Gson json = new Gson();

    public UserResourceProxy() throws UnknownHostException {
        super();
    }
    
    @Override
    public String postUser(User user) {
        Log.info("postUser: request to post " + user.getName());

        String name = user.getName();

        if(name == null || name.equals("") || 
            user.getPwd() == null || user.getPwd().equals("") || 
                user.getDomain() == null || user.getDomain().equals("")){
            Log.info("postUser: User creation was rejected due to lack of name, pwd or domain.");
            throw new WebApplicationException(Status.CONFLICT);
        }

        if(!user.getDomain().equals(ProxyMailServer.hostname)){
            Log.info("postUser: Wrong domain");
            throw new WebApplicationException(Status.FORBIDDEN);
        }

        String directoryPath = String.format(USER_FOLDER_FORMAT, ProxyMailServer.hostname, name);

        if(GetMeta.run(directoryPath)){
            Log.info("postUser: User creation was rejected due to the user already existing");
            throw new WebApplicationException(Status.CONFLICT);
        }

        directoryPath = String.format(USER_FOLDER_FORMAT, ProxyMailServer.hostname, user.getName());
        
        CreateDirectory.run(directoryPath);

        directoryPath = String.format(USER_INBOX_FOLDER, ProxyMailServer.hostname, user.getName());

        CreateDirectory.run(directoryPath);
        
        directoryPath = String.format(USER_DATA_FORMAT, ProxyMailServer.hostname,user.getName());   

        Create.run(directoryPath, user);
        
        return String.format("%s@%s", name, user.getDomain());
    }

    @Override
    public User getUser(String name, String pwd) {
        Log.info("getUser: Request to get " + name);
        if(name == null || name.equals("") || pwd == null){
            Log.info("getUser: Invalid parameters");
            throw new WebApplicationException(Status.FORBIDDEN);
        }
        
        String filePath = String.format(USER_DATA_FORMAT, ProxyMailServer.hostname, name);
        
        String userString = DownloadFile.run(filePath);

        if(userString == null){
            Log.info("getUser: Missing user");
            throw new WebApplicationException(Status.FORBIDDEN);
        }

        Log.info(userString);

        User user = json.fromJson(userString, User.class);

        if(!user.getPwd().equals(pwd)){
            Log.info("getUser: Invalid password");
            throw new WebApplicationException(Status.FORBIDDEN);
        }
        
        return user;
    }

    @Override
    public User updateUser(String name, String pwd, User user) {
        Log.info("updateUser: Request to update user " + name);
        
        User existingUser = this.getUser(name, pwd);

        existingUser.setDisplayName(user.getDisplayName() == null ? existingUser.getDisplayName() : user.getDisplayName());

        existingUser.setPwd(user.getPwd() == null ? existingUser.getPwd() : user.getPwd());
        
        String filePath = String.format(USER_DATA_FORMAT, ProxyMailServer.hostname, existingUser.getName());
        
        Create.run(filePath, existingUser);

        return existingUser;
    }

    @Override
    public User deleteUser(String name, String pwd) {
        Log.info("deleteUser: Request to delete user " + name);

        User user = this.getUser(name, pwd);

        String filePath = String.format(USER_DATA_FORMAT, ProxyMailServer.hostname, name);
        
        Delete.run(filePath);
        
        return user;
    }
}