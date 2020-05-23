package sd1920.trab2.server.proxy;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import sd1920.trab2.api.Discovery;
import sd1920.trab2.server.InsecureHostnameVerifier;
import sd1920.trab2.server.proxy.requests.CreateDirectory;
import sd1920.trab2.server.proxy.requests.Delete;
import sd1920.trab2.server.proxy.resources.MessageResourceProxy;
import sd1920.trab2.server.proxy.resources.UserResourceProxy;

public class ProxyMailServer {
    static {
		System.setProperty("java.net.preferIPv4Stack", "true");
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
	}

	public static final int PORT = 8080;
	public static final String SERVICE = "MailService";
	public static String hostname, messagesPath, usersPath;

	public static Discovery serverRecord;

	public static String secret;

	public static void main(String[] args) throws UnknownHostException {
		String ip = InetAddress.getLocalHost().getHostAddress();

		secret = args[1];
		
		hostname = InetAddress.getLocalHost().getHostName();

		messagesPath = hostname + "/messages";
		usersPath = hostname + "/users";

		boolean freshStart = Boolean.parseBoolean(args[0]);
		String dirName = "/" + InetAddress.getLocalHost().getHostName();

		if(freshStart){		
			//Deletes folder and all its content
			Delete.run(dirName);

			CreateDirectory.run(dirName);

			CreateDirectory.run(dirName + "/messages");

			CreateDirectory.run(dirName + "/users");
		}

		HttpsURLConnection.setDefaultHostnameVerifier(new InsecureHostnameVerifier());

		ResourceConfig config = new ResourceConfig();

		config.register(MessageResourceProxy.class);
		config.register(UserResourceProxy.class);

		String serverURI = String.format("https://%s:%s/rest", ip, PORT);

		try {
			JdkHttpServerFactory.createHttpServer(URI.create(serverURI), config, SSLContext.getDefault());
		} catch (NoSuchAlgorithmException e) {
			System.exit(1);
		}

		serverRecord = new Discovery(SERVICE, serverURI);

		serverRecord.start();
	}
}