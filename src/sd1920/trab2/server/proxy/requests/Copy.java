package sd1920.trab2.server.proxy.requests;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import javax.ws.rs.core.Response.Status;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.Gson;

import org.pac4j.scribe.builder.api.DropboxApi20;

import sd1920.trab2.server.proxy.ProxyMailServer;
import sd1920.trab2.server.proxy.arguments.CopyArgs;
import sd1920.trab2.server.proxy.arguments.CopyBatchArgs;

/**
 * Calls dropbox's Copy endpoint
 */
public class Copy {

    private static Logger Log = Logger.getLogger(ProxyMailServer.class.getName());

	private static final String COPY_BATCH_URL = "https://api.dropboxapi.com/2/files/copy_batch_v2";
	private static final String COPY_URL = "https://api.dropboxapi.com/2/files/copy_v2";

    private static boolean execute(CopyBatchArgs args) {
		OAuthRequest copy = new OAuthRequest(Verb.POST, COPY_BATCH_URL);
		OAuth20Service service = new ServiceBuilder(ProxyRequest.apiKey)
						.apiSecret(ProxyRequest.apiSecret).build(DropboxApi20.INSTANCE);
		OAuth2AccessToken accessToken = new OAuth2AccessToken(ProxyRequest.accessTokenStr);

		copy.addHeader("Content-Type", ProxyRequest.JSON_CONTENT_TYPE);

		Gson json = new Gson();

		String s = json.toJson(args);

		Log.info(s);

		copy.setPayload(s.getBytes());   
        
		service.signRequest(accessToken, copy);
		
		Response r = null;
		
		try {
			Long curr = System.currentTimeMillis();
			r = service.execute(copy);
			Log.info("Time Elapsed Copy: " + (System.currentTimeMillis() - curr));
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
	
	private static boolean execute(CopyArgs arg){
		OAuthRequest copy = new OAuthRequest(Verb.POST, COPY_URL);
		OAuth20Service service = new ServiceBuilder(ProxyRequest.apiKey)
						.apiSecret(ProxyRequest.apiSecret).build(DropboxApi20.INSTANCE);
		OAuth2AccessToken accessToken = new OAuth2AccessToken(ProxyRequest.accessTokenStr);

		copy.addHeader("Content-Type", ProxyRequest.JSON_CONTENT_TYPE);

		Gson json = new Gson();

		String s = json.toJson(arg);

		Log.info(s);

		copy.setPayload(s.getBytes());   
        
		service.signRequest(accessToken, copy);
		
		Response r = null;
		
		try {
			Long curr = System.currentTimeMillis();
			r = service.execute(copy);
			Log.info("Time Elapsed Copy: " + (System.currentTimeMillis() - curr));
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		
		if(r.getCode() == 200 || r.getCode() == Status.CONFLICT.getStatusCode())
			return true;
		else{
			System.err.println("HTTP Error Code: " + r.getCode() + ": " + r.getMessage());
			try {
				System.err.println(r.getBody());
			} catch (IOException e) {
				System.err.println("No body in the response");
			}
			return false;
		}
	}
    
    public static boolean run(List<CopyArgs> copies){
		Log.info("Copying " + copies.size() +" copies");
		boolean success = false;

		if(copies.size() == 0)
			return true;
		
		for(CopyArgs copy : copies){
			Log.info("From " + copy.getFromPath() + " ; To " + copy.getToPath());
		}
        
        CopyBatchArgs args = new CopyBatchArgs(copies);

        for(int i = 0; i < ProxyRequest.RETRIES; i++){
            if(success = execute(args))
				break;
				
			try {
				Thread.sleep(ProxyRequest.SLEEP_TIME);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        }

		if(success){
			Log.info("Copy: Succesfully copied all files");
			return true;
		}else{
			Log.info("Copy: Something went wrong");
			return false;
		}
    }

    public static boolean run(CopyArgs copy){	
		Log.info("Copying from " + copy.getFromPath() + " to " + copy.getToPath());
		boolean success = false;
		        
        while(true){
            if(success = execute(copy))
				break;
				
			try {
				Thread.sleep(ProxyRequest.SLEEP_TIME);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        }

		if(success){
			Log.info("Copy: Succesful");
			return true;
		}else{
			Log.info("Copy: Unsuccessful");
			return false;
		}
	}
}
