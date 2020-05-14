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

import sd1920.trab2.server.dropbox.arguments.CreateFolderV2Args;

public class CreateDirectory {

	private static final String CREATE_FOLDER_V2_URL = "https://api.dropboxapi.com/2/files/create_folder_v2";

	private static boolean execute(String directory) {
		OAuthRequest createFolder = new OAuthRequest(Verb.POST, CREATE_FOLDER_V2_URL);
		OAuth20Service service = new ServiceBuilder(DropboxRequest.apiKey).apiSecret(DropboxRequest.apiSecret)
				.build(DropboxApi20.INSTANCE);
		OAuth2AccessToken accessToken = new OAuth2AccessToken(DropboxRequest.accessTokenStr);
		Gson json = new Gson();

		createFolder.addHeader("Content-Type", DropboxRequest.JSON_CONTENT_TYPE);

		createFolder.setPayload(json.toJson(new CreateFolderV2Args(directory, false)));

		service.signRequest(accessToken, createFolder);

		Response r = null;

		try {
			long curr = System.currentTimeMillis();
			r = service.execute(createFolder);
			System.out.println("Time Elapsed newDir: " + (System.currentTimeMillis() - curr));
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		if (r.getCode() == 200) {
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

	public static boolean run(String directoryPath) {
		boolean success = false;

		for (int i = 0; i < DropboxRequest.RETRIES; i++) {
			if (success = execute(directoryPath))
				break;

			try {
				Thread.sleep(DropboxRequest.SLEEP_TIME);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		if(success){
			System.out.println("Directory '" + directoryPath + "' created successfuly.");
			return true;
		}else{
			System.out.println("Failed to create directory '" + directoryPath + "'");
			return false;
		}
	}

}
