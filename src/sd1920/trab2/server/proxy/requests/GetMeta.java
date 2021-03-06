package sd1920.trab2.server.proxy.requests;

import java.io.IOException;
import java.util.logging.Logger;

import javax.ws.rs.WebApplicationException;
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
import sd1920.trab2.server.proxy.arguments.GetMetaArgs;

/**
 * Calls dropbox's GetMeta endpoint
 */
public class GetMeta {

    private static Logger Log = Logger.getLogger(ProxyMailServer.class.getName());

    public static final String GET_META_URL = "https://api.dropboxapi.com/2/files/get_metadata";

    private static boolean execute(String path) throws WebApplicationException, IOException {
        OAuthRequest getMeta = new OAuthRequest(Verb.POST, GET_META_URL);
		OAuth20Service service = new ServiceBuilder(ProxyRequest.apiKey)
						.apiSecret(ProxyRequest.apiSecret).build(DropboxApi20.INSTANCE);
		OAuth2AccessToken accessToken = new OAuth2AccessToken(ProxyRequest.accessTokenStr);
        Gson json = new Gson();

        getMeta.addHeader("Content-Type", ProxyRequest.JSON_CONTENT_TYPE);

		getMeta.setPayload(json.toJson(new GetMetaArgs(path)));
		     
        service.signRequest(accessToken, getMeta);
		
		Response r = null;
		
		try {
			Long curr = System.currentTimeMillis();
			r = service.execute(getMeta);
			Log.info(r.getBody());
			Log.info("Time Elapsed GetMeta: " + (System.currentTimeMillis() - curr));
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		
		
		if(r.getCode() == 200) {
			return true;
		} else if(r.getCode() == Status.CONFLICT.getStatusCode()){
			return false;
		}else{
			System.err.println("HTTP Error Code: " + r.getCode() + ": " + r.getMessage());
			System.err.println(r.getBody());

			throw new WebApplicationException(Status.BAD_REQUEST);
		}
    }

    public static boolean run(String path){
		Log.info("getMeta: " + path);
		boolean result = false;
        
        for(int i = 0; i < ProxyRequest.RETRIES; i++){
			try{
				result = execute(path);
				break;
				
			}catch(IOException e){
				Log.info("getMeta: What the frog");
			}catch(WebApplicationException e){
				Log.info("getMeta: What the frog");
			}
        }		
		
		if(result){
			Log.info("File with name " + path + " was found.");
			return true;
		}
			
		Log.info("Couldn't find file with name " + path + ".");
		return false;
		
    }
    
}