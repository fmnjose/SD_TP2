package sd1920.trab2.server.dropbox.requests;

import java.io.IOException;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.Gson;

import org.pac4j.scribe.builder.api.DropboxApi20;

import sd1920.trab2.server.dropbox.arguments.SearchFileArgs;

public class SearchFile {
    
	private static final String CREATE_FOLDER_V2_URL = "https://api.dropboxapi.com/2/files/search_v2";

    private static boolean execute(String directory, String userName){
        OAuthRequest createFolder = new OAuthRequest(Verb.POST, CREATE_FOLDER_V2_URL);
		OAuth20Service service = new ServiceBuilder(DropboxRequest.apiKey)
						.apiSecret(DropboxRequest.apiSecret).build(DropboxApi20.INSTANCE);
		OAuth2AccessToken accessToken = new OAuth2AccessToken(DropboxRequest.accessTokenStr);
        Gson json = new Gson();

        createFolder.addHeader("Content-Type", DropboxRequest.JSON_CONTENT_TYPE);

        createFolder.setPayload(json.toJson(new SearchFileArgs(directory, userName)));
        
        service.signRequest(accessToken, createFolder);
		
		Response r = null;
		
		try {
			r = service.execute(createFolder);
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

    public static boolean run(String directoryPath, String userName){
        boolean success = execute(directoryPath, userName);
		if(success){
			System.out.println("User with name " + userName + " was found.");
			return true;
		}else{
			System.out.println("Couldn't find user with name " + userName + ".");
			return false;
		}
    }
}