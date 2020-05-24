package sd1920.trab2.server.proxy.requests;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
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
import sd1920.trab2.server.proxy.arguments.DeleteArgs;
import sd1920.trab2.server.proxy.arguments.DeleteBatchArgs;

/**
 * Calls dropbox's Delete endpoint
 */
public class Delete{

    private static Logger Log = Logger.getLogger(ProxyMailServer.class.getName());

    private static final String DELETE_BATCH_URL = "https://api.dropboxapi.com/2/files/delete_batch";

    private static boolean execute(List<DeleteArgs> args){
        OAuthRequest deleteBatch = new OAuthRequest(Verb.POST, DELETE_BATCH_URL);
		OAuth20Service service = new ServiceBuilder(ProxyRequest.apiKey)
						.apiSecret(ProxyRequest.apiSecret).build(DropboxApi20.INSTANCE);
		OAuth2AccessToken accessToken = new OAuth2AccessToken(ProxyRequest.accessTokenStr);

		Gson json = new Gson();

		deleteBatch.addHeader("Content-Type", ProxyRequest.JSON_CONTENT_TYPE);

		deleteBatch.setPayload(json.toJson(new DeleteBatchArgs(args)));

        service.signRequest(accessToken, deleteBatch);
        
        Response r = null;

        try {
			r = service.execute(deleteBatch);
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

    public static boolean run(List<String> paths){
		Log.info("Deleting " + paths.size() + " files");
		boolean success = false;

		List<DeleteArgs> args = new LinkedList<>();
		
		for(String path: paths)
			args.add(new DeleteArgs(path));
        
        for(int i = 0; i < ProxyRequest.RETRIES; i++){
            if(success = execute(args))
				break;
				
			try {
				Log.info("I sleep");
				Thread.sleep(ProxyRequest.SLEEP_TIME);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        }

		if(success){
			Log.info("Deletion for " + paths.size() + " files successful");
			return true;
		}else{
			Log.info("Failed to delete " + paths.size() + " files");
			return false;
		}
	}
	
	public static boolean run(String path){
		List<String> paths = new LinkedList<>();

		paths.add(path);

		return run(paths);
	}
}