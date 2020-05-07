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

import sd1920.trab2.server.dropbox.arguments.DeleteFolderArgs;

public class DeleteDirectory{

    private static final String DELETE_FOLDER_URL = "https://api.dropboxapi.com/2/files/delete_v2";

    private static boolean execute(String directory){
        OAuthRequest deleteFolder = new OAuthRequest(Verb.POST, DELETE_FOLDER_URL);
		OAuth20Service service = new ServiceBuilder(DropboxRequest.apiKey)
						.apiSecret(DropboxRequest.apiSecret).build(DropboxApi20.INSTANCE);
		OAuth2AccessToken accessToken = new OAuth2AccessToken(DropboxRequest.accessTokenStr);
		Gson json = new Gson();

		deleteFolder.addHeader("Content-Type", DropboxRequest.JSON_CONTENT_TYPE);

		deleteFolder.setPayload(json.toJson(new DeleteFolderArgs(directory)));

        service.signRequest(accessToken, deleteFolder);
        
        Response r = null;

        try {
			r = service.execute(deleteFolder);
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

    public static boolean run(String directoryPath){
        boolean success = false;
        
        for(int i = 0; i < DropboxRequest.RETRIES; i++){
            if(success = execute(directoryPath))
                break;
        }

		if(success){
			System.out.println("Directory '" + directoryPath + "' deleted successfuly.");
			return true;
		}else{
			System.out.println("Failed to delete directory '" + directoryPath + "'");
			return false;
		}
    }
}