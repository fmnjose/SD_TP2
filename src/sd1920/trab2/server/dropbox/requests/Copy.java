package sd1920.trab2.server.dropbox.requests;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.Gson;

import org.pac4j.scribe.builder.api.DropboxApi20;

import sd1920.trab2.server.dropbox.arguments.CopyArgs;
import sd1920.trab2.server.dropbox.arguments.CopyBatchArgs;

public class Copy {
    private static final String COPY_BATCH_URL = "https://api.dropboxapi.com/2/files/copy_batch_v2";

    private static boolean execute(CopyBatchArgs args) {
		OAuthRequest copy = new OAuthRequest(Verb.POST, COPY_BATCH_URL);
		OAuth20Service service = new ServiceBuilder(DropboxRequest.apiKey)
						.apiSecret(DropboxRequest.apiSecret).build(DropboxApi20.INSTANCE);
		OAuth2AccessToken accessToken = new OAuth2AccessToken(DropboxRequest.accessTokenStr);

		copy.addHeader("Content-Type", DropboxRequest.JSON_CONTENT_TYPE);

		Gson json = new Gson();

		String s = json.toJson(args);

		System.out.println(s);

		copy.setPayload(s.getBytes());   
        
		service.signRequest(accessToken, copy);
		
		Response r = null;
		
		try {
			Long curr = System.currentTimeMillis();
			r = service.execute(copy);
			System.out.println("Time Elapsed Copy: " + (System.currentTimeMillis() - curr));
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		
		if(r.getCode() == 200) {
			try {
				System.out.println(r.getBody());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
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
    
    public static boolean run(List<CopyArgs> copies){
		System.out.println("Copying " + copies.size() +" copies");
		boolean success = false;

		if(copies.size() == 0)
			return true;
		
		for(CopyArgs copy : copies){
			System.out.println("From " + copy.getFromPath() + " ; To " + copy.getToPath());
		}

		System.out.println("FEIJOADA");
        
        CopyBatchArgs args = new CopyBatchArgs(copies);

        for(int i = 0; i < DropboxRequest.RETRIES; i++){
            if(success = execute(args))
				break;
				
			try {
				Thread.sleep(DropboxRequest.SLEEP_TIME);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        }

		if(success){
			System.out.println("Copy: Succesfully copied all files");
			return true;
		}else{
			System.out.println("Copy: Something went wrong");
			return false;
		}
    }

    public static boolean run(CopyArgs copy){	
		List<CopyArgs> copies = new LinkedList<>();

		copies.add(copy);

		return run(copies);
	}
}
