package sd1920.trab2.server.replica;

import java.net.InetAddress;
import java.net.URI;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import sd1920.trab2.api.Discovery;
import sd1920.trab2.replication.VersionControl;
import sd1920.trab2.server.InsecureHostnameVerifier;
import sd1920.trab2.server.replica.resources.ReplicaMessageResourceREST;
import sd1920.trab2.server.replica.resources.ReplicaUserResourceREST;

public class ReplicaMailServerREST {

	static {
		System.setProperty("java.net.preferIPv4Stack", "true");
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
	}

	public static final int PORT = 8080;
	public static final String SERVICE = "MailService";

	public static Discovery serverRecord;

	public static String secret;

	public static VersionControl vc;

	public static void main(String[] args) throws Exception {
		String ip = InetAddress.getLocalHost().getHostAddress();

		secret = args[0];
		HttpsURLConnection.setDefaultHostnameVerifier(new InsecureHostnameVerifier());

		ResourceConfig config = new ResourceConfig();

		config.register(ReplicaMessageResourceREST.class);
		config.register(ReplicaUserResourceREST.class);

		String serverURI = String.format("https://%s:%s/rest", ip, PORT);

		vc = new VersionControl(InetAddress.getLocalHost().getHostName(), serverURI);
		
		try {
			JdkHttpServerFactory.createHttpServer(URI.create(serverURI), config, SSLContext.getDefault());
		} catch (NoSuchAlgorithmException e) {
			System.exit(1);
		}


		serverRecord = new Discovery(SERVICE, serverURI);

		serverRecord.start();
	}
	
}
