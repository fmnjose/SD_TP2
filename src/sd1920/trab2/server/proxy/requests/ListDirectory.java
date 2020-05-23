package sd1920.trab2.server.proxy.requests;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.pac4j.scribe.builder.api.DropboxApi20;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.Gson;

import sd1920.trab2.server.proxy.ProxyMailServer;
import sd1920.trab2.server.proxy.arguments.ListFolderArgs;
import sd1920.trab2.server.proxy.arguments.ListFolderContinueArgs;
import sd1920.trab2.server.proxy.replies.ListFolderReturn;
import sd1920.trab2.server.proxy.replies.ListFolderReturn.FolderEntry;

public class ListDirectory {
	
    private static Logger Log = Logger.getLogger(ProxyMailServer.class.getName());

	protected static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
	
	private static final String LIST_FOLDER_URL = "https://api.dropboxapi.com/2/files/list_folder";
	private static final String LIST_FOLDER_CONTINUE_URL = "https://api.dropboxapi.com/2/files/list_folder/continue";
	
	
	private static List<String> execute(String directoryName) {
		OAuthRequest listDir = new OAuthRequest(Verb.POST, LIST_FOLDER_URL);
		OAuth20Service service = new ServiceBuilder(ProxyRequest.apiKey)
						.apiSecret(ProxyRequest.apiSecret).build(DropboxApi20.INSTANCE);
		OAuth2AccessToken accessToken = new OAuth2AccessToken(ProxyRequest.accessTokenStr);
		Gson json = new Gson();
		
		List<String> directoryContents = new ArrayList<String>();
		
		listDir.addHeader("Content-Type", JSON_CONTENT_TYPE);
		listDir.setPayload(json.toJson(new ListFolderArgs(directoryName, false)));
		
		service.signRequest(accessToken, listDir);
		
		Response r = null;
		
		try {
			while(true) {
				r = service.execute(listDir);
				
				if(r.getCode() != 200) {
					System.err.println("ListDir: Failed to list directory '" + directoryName + "'. Status " + r.getCode() + ": " + r.getMessage());
					System.err.println(r.getBody());
					return null;
				}
				
				ListFolderReturn reply = json.fromJson(r.getBody(), ListFolderReturn.class);
				
				for(FolderEntry e: reply.getEntries()) {
					directoryContents.add(e.toString());
				}
				
				if(reply.has_more()) {
					//There are more elements to read, prepare a new request (now a continuation)
					listDir = new OAuthRequest(Verb.POST, LIST_FOLDER_CONTINUE_URL);
					listDir.addHeader("Content-Type", JSON_CONTENT_TYPE);
					//In this case the arguments is just an object containing the cursor that was returned in the previous reply.
					listDir.setPayload(json.toJson(new ListFolderContinueArgs(reply.getCursor())));
					service.signRequest(accessToken, listDir);
				} else {
					break; //There are no more elements to read. Operation can terminate.
				}
			}			
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		
		
		return directoryContents;
	}
	
	public static List<String> run(String directoryPath) {
		System.out.println("Listing " + directoryPath);

		List<String> result = null;

		Boolean success = false;
		
        for(int i = 0; i < ProxyRequest.RETRIES; i++){
			try{
                result = execute(directoryPath);
				if(result != null){
                    success = true;
                    break;
				}
				System.out.println("I SLEEP");
				Thread.sleep(ProxyRequest.SLEEP_TIME);
			} catch(InterruptedException e){
				System.out.println("SearchFile: What the frog");
			}

        }		
		
		if(success){
			System.out.println("Folder: " + directoryPath + " was listed");
			return result;
		}else{
			System.out.println("Folder: " + directoryPath + " was NOT found");
			return null;
		}
	}
}
