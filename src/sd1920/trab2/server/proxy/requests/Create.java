package sd1920.trab2.server.proxy.requests;

import java.io.IOException;
import java.util.logging.Logger;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.Gson;

import org.pac4j.scribe.builder.api.DropboxApi20;

import sd1920.trab2.server.proxy.ProxyMailServer;
import sd1920.trab2.server.proxy.arguments.CreateFileArgs;

/**
 * Calls dropbox's Create endpoint
 */
public class Create {
    private static final String CREATE_FILE_URL = "https://content.dropboxapi.com/2/files/upload";

    private static Logger Log = Logger.getLogger(ProxyMailServer.class.getName());

    private static boolean execute(String filePath, Object file) {
		OAuthRequest createFile = new OAuthRequest(Verb.POST, CREATE_FILE_URL);
		OAuth20Service service = new ServiceBuilder(ProxyRequest.apiKey)
						.apiSecret(ProxyRequest.apiSecret).build(DropboxApi20.INSTANCE);
		OAuth2AccessToken accessToken = new OAuth2AccessToken(ProxyRequest.accessTokenStr);
		Gson json = new Gson();

        createFile.addHeader("Dropbox-API-Arg", json.toJson(new CreateFileArgs(filePath)));
		createFile.addHeader("Content-Type", ProxyRequest.OCTET_CONTENT_TYPE);

		String s = json.toJson(file);

		createFile.setPayload(s.getBytes());   
        
		service.signRequest(accessToken, createFile);
		
		Response r = null;
		
		try {
			Long curr = System.currentTimeMillis();
			r = service.execute(createFile);
			Log.info("Time Elapsed Upload: " + (System.currentTimeMillis() - curr));
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		
		if(r.getCode() == 200) {
			return true;
		} else {
			System.err.println("HTTP Error Code: " + r.getCode() + ": " + r.getMessage());
			try {
				System.err.println(r.getBody());
			} catch (IOException e) {
				System.err.println("No body in the response");
			}
			return false;
		}
    }
    
    public static boolean run(String directoryPath, Object object){
		Log.info("Creating file on " + directoryPath);
        boolean success = false;
        
        for(int i = 0; i < ProxyRequest.RETRIES; i++){
            if(success = execute(directoryPath, object))
				break;
				
			try {
				Thread.sleep(ProxyRequest.SLEEP_TIME);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        }

		if(success){
			Log.info("Succesfully created file: " + directoryPath);
			return true;
		}else{
			Log.info("Couldn't create file: " + directoryPath);
			return false;
		}
    }
}