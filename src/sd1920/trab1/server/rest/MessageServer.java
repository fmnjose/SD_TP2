package sd1920.trab1.server.rest;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.logging.Logger;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import sd1920.trab1.api.Discovery;
import sd1920.trab1.api.Discovery.DomainInfo;
import sd1920.trab1.server.rest.resources.MessageResource;
import sd1920.trab1.server.rest.resources.UserResource;

public class MessageServer {

	private static Logger Log = Logger.getLogger(MessageServer.class.getName());

	static {
		System.setProperty("java.net.preferIPv4Stack", "true");
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
	}
	
	public static final int PORT = 8080;
	public static final String SERVICE = "MailService";

	public static final int TIMEOUT = 10000;
	public static final int SLEEP_TIME = 5000;
	public static final int N_TRIES = 5;

	public static HashMap<String, DomainInfo> servers;
	
	public static void main(String[] args) throws UnknownHostException {
		String ip = InetAddress.getLocalHost().getHostAddress();
			
		ResourceConfig config = new ResourceConfig();

        config.register(MessageResource.class);
		config.register(UserResource.class);

		String serverURI = String.format("http://%s:%s/rest", ip, PORT);
	
		JdkHttpServerFactory.createHttpServer( URI.create(serverURI), config);

		Discovery lurker = new Discovery(SERVICE, serverURI);

		lurker.start();

		servers = lurker.record;
		
		//Log.info(String.format("%s Server ready @ %s\n",  SERVICE, serverURI));

		new Thread(() -> {
			try{
				while(true){
					Thread.sleep(1000);
					for (DomainInfo df : servers.values()) {
						//Log.info(df.toString()+"\n");
					}
				}
			}
			catch(Exception e){
				//saasf
			}
		}).start();
		//More code can be executed here...
	}
	
}
