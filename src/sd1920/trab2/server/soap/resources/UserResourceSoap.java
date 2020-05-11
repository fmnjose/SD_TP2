package sd1920.trab2.server.soap.resources;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;

import sd1920.trab2.api.User;
import sd1920.trab2.api.soap.MessageServiceSoap;
import sd1920.trab2.api.soap.MessagesException;
import sd1920.trab2.api.soap.UserServiceSoap;
import sd1920.trab2.server.serverUtils.LocalServerUtils;
import sd1920.trab2.server.rest.resources.UserResourceRest;
import sd1920.trab2.server.soap.SOAPMailServer;
import javax.xml.ws.BindingProvider;
import com.sun.xml.ws.client.BindingProviderProperties;

@WebService(serviceName=UserServiceSoap.NAME, 
	targetNamespace=UserServiceSoap.NAMESPACE, 
	endpointInterface=UserServiceSoap.INTERFACE)
public class UserResourceSoap implements UserServiceSoap{
    private static final QName MESSAGE_QNAME = new QName(MessageServiceSoap.NAMESPACE, MessageServiceSoap.NAME);
    private static final String MESSAGES_WSDL = String.format("/%s/?wsdl", MessageServiceSoap.NAME);


    private final Map<String, User> users = new HashMap<String, User>();

    private String serverSoapUri;

    private static Logger Log = Logger.getLogger(UserResourceRest.class.getName());

    public UserResourceSoap() throws UnknownHostException {
		this.serverSoapUri = String.format("https://%s:%d/soap",InetAddress.getLocalHost().getHostAddress(),SOAPMailServer.PORT);
    }

    private boolean createUserInbox(String userName){
        boolean error = true;

        Log.info("Sending request to create a new inbox in MessageResource.");
        int tries = 0;

        MessageServiceSoap msgService = null;
				
        try {
            Service	service = Service.create(new URL(this.serverSoapUri + MESSAGES_WSDL), MESSAGE_QNAME);
            msgService = service.getPort(MessageServiceSoap.class);							
        }
        catch(MalformedURLException e){
            Log.info("createUserInbox: Bad Url");
            return false;
        } 
        catch(WebServiceException e){
            Log.info("createUserInbox: Failed to send inbox creation request. Retrying...");
            return false;
        }

        ((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.CONNECT_TIMEOUT, LocalServerUtils.TIMEOUT);
        ((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.REQUEST_TIMEOUT, LocalServerUtils.TIMEOUT);
        

        while(error && tries< LocalServerUtils.N_TRIES){
            error = false;

            try{
                msgService.createInbox(userName);
            }
            catch(MessagesException me){
                Log.info("createUserInbox: Error, could not send the request. Retrying...");
                error = true;
            }
            catch(WebServiceException wse){
                Log.info("createUserInbox: Communication error. Retrying...");
                wse.printStackTrace();
                try{
                    Thread.sleep(LocalServerUtils.SLEEP_TIME);
                }
                catch(InterruptedException e){
                    Log.info("Log a dizer 'what?'");
                }
                error = true;
            }
            tries++;
        }

        if(error)
            Log.info("createUserInbox: Failed to repeatedly send request to MessageResource. Giving up...");
        else
            Log.info("createUserInbox: Successfully sent request to MessageResource. More successful than i'll ever be!");		
        
        return error;
    }

    @Override
    public String postUser(User user) throws MessagesException{
        try{
            String serverDomain = InetAddress.getLocalHost().getHostName();
            String name = user.getName();

            if(name == null || name.equals("") || 
                user.getPwd() == null || user.getPwd().equals("") || 
                    user.getDomain() == null || user.getDomain().equals("")){
                Log.info("User creation was rejected due to lack of name, pwd or domain.");
                throw new MessagesException("postUser: User creation was rejected due to lack of name, pwd or domain.");
            }
            else if(!user.getDomain().equals(serverDomain)){
                Log.info("User creation was rejected due to mismatch between the provided domain and the server domain");
                throw new MessagesException("postUser: User creation was rejected due to mismatch between the provided domain and the server domain");
            }
            synchronized(this.users){
                if(this.users.containsKey(name)){
                    Log.info("User creation was rejected due to the user already existing");
                    throw new MessagesException("postUser: User creation was rejected due to the user already existing");
                }
                this.users.put(name, user);
            }
            
            if(this.createUserInbox(name))    
                throw new MessagesException("postUser: Failed while creating new inbox for the user");
            
            Log.info("Created new user with name: " + name);
            
            return String.format("%s@%s", name, user.getDomain());
        }
        catch(UnknownHostException e){
            return null;
        }
    }

    @Override
    public User getUser(String name, String pwd) throws MessagesException{
        User user;

        if(name == null || name.equals("")){
            Log.info("getUser: User fetch was rejected due to invalid parameters:");
            throw new MessagesException("getUser: User fetch was rejected due to invalid parameters");
        }

        synchronized(this.users){
            user = this.users.get(name);
        }

        if(user == null){
            Log.info("getUser: User fetch was rejected due to missing user: " + name + ".");
            throw new MessagesException("getUser: User fetch was rejected due to missing user");
        }else if(pwd == null || !user.getPwd().equals(pwd)){
            Log.info("User fetch was rejected due to an invalid password");
            throw new MessagesException("getUser: User fetch was rejected due to an invalid password");
        }else{
            return user;
        }
    }

    @Override
    public User updateUser(String name, String pwd, User user) throws MessagesException{

        User existingUser;
        
        if(name == null || name.equals("")){
            Log.info("User update was rejected due to invalid parameters");
            throw new MessagesException("updateUser: User update was rejected due to invalid parameters");
        }

        synchronized(this.users){
            existingUser = this.users.get(name);

            if(existingUser == null){
                Log.info("User update was rejected due to a missing user");
                throw new MessagesException("updateUser: User update was rejected due to a missing user");

            }else if(!existingUser.getPwd().equals(pwd)){
                Log.info("User update was rejected due to an invalid password");
                throw new MessagesException("User update was rejected due to an invalid password");
            }

            existingUser.setDisplayName(user.getDisplayName() == null ? existingUser.getDisplayName() : user.getDisplayName());

            existingUser.setPwd(user.getPwd() == null ? existingUser.getPwd() : user.getPwd());
        }

        return existingUser;
    }

    @Override
    public User deleteUser(String name, String pwd) throws MessagesException{
        
        User user;

        if(name == null || name.equals("")){
            Log.info("User deletion was rejected due to invalid parameters");
            throw new MessagesException("deleteUser: User deletion was rejected due to invalid parameters");

        }

        synchronized(this.users){
            user = this.users.get(name);
            
            if(user == null){
                Log.info("User deletion was rejected due to a missing user");
                throw new MessagesException("deleteUser: User deletion was rejected due to a missing user");

            }else if(pwd == null || !user.getPwd().equals(pwd)){
                Log.info("User update was rejected due to an invalid password");
                throw new MessagesException("deleteUser: User update was rejected due to an invalid password");
            }

            this.users.remove(name);
        }


        return user;
    }
}