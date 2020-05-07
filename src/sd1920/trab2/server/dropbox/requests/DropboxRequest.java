package sd1920.trab2.server.dropbox.requests;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.Gson;

import org.pac4j.scribe.builder.api.DropboxApi20;

public abstract class DropboxRequest {
	static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    static final String OCTET_CONTENT_TYPE = "application/octet-stream";
    static final int RETRIES = 5;

    static final String apiKey = "620x4wxxxg78fjp";
	static final String apiSecret = "annohkvglmoo9e9";
	static final String accessTokenStr = "X0-2OZhLyrAAAAAAAAAAFgnb4-4u4yALgLmgLVCpO4FWw6VRj2MIHeRhmhkJPUeB";

    OAuth20Service service;
	OAuth2AccessToken accessToken;

	Gson json;

    public DropboxRequest(){
        service = new ServiceBuilder(apiKey).apiSecret(apiSecret).build(DropboxApi20.INSTANCE);
        accessToken = new OAuth2AccessToken(accessTokenStr);
        this.json = new Gson();
    }

    public abstract boolean run();
}
