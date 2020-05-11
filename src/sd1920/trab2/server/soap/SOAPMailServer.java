package sd1920.trab2.server.soap;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.xml.ws.Endpoint;

import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;

import sd1920.trab2.api.Discovery;
import sd1920.trab2.server.InsecureHostnameVerifier;
import sd1920.trab2.server.soap.resources.MessageResourceSoap;
import sd1920.trab2.server.soap.resources.UserResourceSoap;

public class SOAPMailServer {

	private static Logger Log = Logger.getLogger(SOAPMailServer.class.getName());

	public static Discovery serverRecord;

	static {
		System.setProperty("java.net.preferIPv4Stack", "true");
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
	}

	public static final int PORT = 8080;
	public static final String SERVICE = "MessageService";
	public static final String SOAP_MESSAGES_PATH = "/soap/messages";
	public static final String SOAP_USERS_PATH = "/soap/users";

	public static String secret;
	public static void main(String[] args) throws Exception {

		String ip = InetAddress.getLocalHost().getHostAddress();
		String serverURI = String.format("https://%s:%s/soap", ip, PORT);

		secret = args[0];
		
		// Create an HTTP server, accepting requests at PORT (from all local interfaces)	
		HttpsURLConnection.setDefaultHostnameVerifier(new InsecureHostnameVerifier());
		
		HttpsConfigurator configurator = new HttpsConfigurator(SSLContext.getDefault());
		
		HttpsServer server = HttpsServer.create(new InetSocketAddress(ip, PORT), 0);

		server.setHttpsConfigurator(configurator);
		
		// Provide an executor to create threads as needed...
		server.setExecutor(Executors.newCachedThreadPool());

		System.out.println(String.format("\n%s Server ready @ %s\n", SERVICE, serverURI));

		// Create a SOAP Endpoint (you need one for each service)
		Endpoint soapMessagesEndpoint = Endpoint.create(new MessageResourceSoap());
		Endpoint soapUsersEndpoint = Endpoint.create(new UserResourceSoap());

		// Publish a SOAP webservice, under the "http://<ip>:<port>/soap"
		soapMessagesEndpoint.publish(server.createContext(SOAP_MESSAGES_PATH));
		soapUsersEndpoint.publish(server.createContext(SOAP_USERS_PATH));

		server.start();

		Log.info(String.format("\n%s Server ready @ %s\n", SERVICE, serverURI));

		serverRecord = new Discovery(SERVICE, serverURI);

		serverRecord.start();
		//More code can be executed here...
	}
	
}
