package sd1920.trab2.server.dropbox.requests;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
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
import com.google.gson.JsonSyntaxException;

import org.pac4j.scribe.builder.api.DropboxApi20;

import sd1920.trab2.server.dropbox.arguments.DownloadFileArgs;

public class DownloadFile {
    private static final String DOWNLOAD_FILE_URL = "https://content.dropboxapi.com/2/files/download";
	
    private static Logger Log = Logger.getLogger(SearchFile.class.getName());

	private static String execute(String filePath) throws JsonSyntaxException, IOException, ClassNotFoundException{
        OAuthRequest downloadFile = new OAuthRequest(Verb.POST, DOWNLOAD_FILE_URL);
		OAuth20Service service = new ServiceBuilder(DropboxRequest.apiKey)
						.apiSecret(DropboxRequest.apiSecret).build(DropboxApi20.INSTANCE);
		OAuth2AccessToken accessToken = new OAuth2AccessToken(DropboxRequest.accessTokenStr);
        Gson json = new Gson();

        downloadFile.addHeader("Content-Type", DropboxRequest.OCTET_CONTENT_TYPE);
        downloadFile.addHeader("Dropbox-API-Arg", json.toJson(new DownloadFileArgs(filePath)));

        service.signRequest(accessToken, downloadFile);
		
		Response r = null;
		
		try {
			Long curr = System.currentTimeMillis();
			r = service.execute(downloadFile);
			System.out.println("Time Elapsed Download: " + (System.currentTimeMillis() - curr));
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		
		
		if(r.getCode() == 200) { 
			String jstring = new String(r.getBody().getBytes());
            return jstring;
		} else if(r.getCode() == 409){
			System.out.println("DownloadFile: File does not exist");
			throw new WebApplicationException(Status.CONFLICT);
		} else {
			System.err.println("HTTP Error Code: " + r.getCode() + ": " + r.getMessage());
			try {
				System.err.println(r.getBody());
			} catch (IOException e) {
				System.err.println("No body in the response");
			}
			return null;
		}
    }

    public static String run(String filePath){
		boolean success = false;
		String o = null;
		
        for(int i = 0; i < DropboxRequest.RETRIES; i++){
			try{
                o = execute(filePath);
				if(o != null){
                    success = true;
                    break;
				}
				System.out.println("I SLEEP");
				Thread.sleep(5000);
			} catch(WebApplicationException e){
				break;
			} catch(Exception e){
				System.out.println("SearchFile: What the frog");
			}

        }		
		
		if(success){
			System.out.println("File: " + filePath + " was downloaded");
			return o;
		}else{
			System.out.println("File: " + filePath + " was NOT found");
			return null;
		}
    }
}