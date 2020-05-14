package sd1920.trab2.server.dropbox.requests;

import java.io.IOException;

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

import sd1920.trab2.server.dropbox.arguments.GetMetaArgs;

public class GetMeta {

    public static final String GET_META_URL = "https://api.dropboxapi.com/2/files/get_metadata";

    private static boolean execute(String path) throws WebApplicationException, IOException {
        OAuthRequest getMeta = new OAuthRequest(Verb.POST, GET_META_URL);
		OAuth20Service service = new ServiceBuilder(DropboxRequest.apiKey)
						.apiSecret(DropboxRequest.apiSecret).build(DropboxApi20.INSTANCE);
		OAuth2AccessToken accessToken = new OAuth2AccessToken(DropboxRequest.accessTokenStr);
        Gson json = new Gson();

        getMeta.addHeader("Content-Type", DropboxRequest.JSON_CONTENT_TYPE);

		getMeta.setPayload(json.toJson(new GetMetaArgs(path)));
		     
        service.signRequest(accessToken, getMeta);
		
		Response r = null;
		
		try {
			Long curr = System.currentTimeMillis();
			r = service.execute(getMeta);
			System.out.println(r.getBody());
			System.out.println("Time Elapsed GetMeta: " + (System.currentTimeMillis() - curr));
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		
		
		if(r.getCode() == 200) {
			return true;
		} else {
			System.err.println("HTTP Error Code: " + r.getCode() + ": " + r.getMessage());
			System.err.println(r.getBody());

			throw new WebApplicationException(Status.BAD_REQUEST);
		}
    }

    public static boolean run(String path){
		System.out.println("getMeta: " + path);
		boolean result = false;
        
        for(int i = 0; i < DropboxRequest.RETRIES; i++){
			try{
				result = execute(path);
				break;
				
			}catch(IOException e){
				System.out.println("getMeta: What the frog");
			}catch(WebApplicationException e){
				System.out.println("getMeta: What the frog");
			}
        }		
		
		if(result){
			System.out.println("File with name " + path + " was found.");
			return true;
		}
			
		System.out.println("Couldn't find file with name " + path + ".");
		return false;
		
    }
    
}