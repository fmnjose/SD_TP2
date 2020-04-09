package sd1920.trab1.api;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.HashMap;

/**
 * <p>
 * A class to perform service discovery, based on periodic service contact
 * endpoint announcements over multicast communication.
 * </p>
 * 
 * <p>
 * Servers announce their *name* and contact *uri* at regular intervals. The
 * server actively collects received announcements.
 * </p>
 * 
 * <p>
 * Service announcements have the following format:
 * </p>
 * 
 * <p>
 * &lt;service-name-string&gt;&lt;delimiter-char&gt;&lt;service-uri-string&gt;
 * </p>
 */
public class Discovery {
	static {
		// addresses some multicast issues on some TCP/IP stacks
		System.setProperty("java.net.preferIPv4Stack", "true");
		// summarizes the logging format
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
	}

	// The pre-aggreed multicast endpoint assigned to perform discovery.
	static final InetSocketAddress DISCOVERY_ADDR = new InetSocketAddress("226.226.226.226", 2266);
	static final int DISCOVERY_PERIOD = 1000;
	static final int DISCOVERY_TIMEOUT = 5000;
	static final int DEAD_PERIOD = 10000;

	// Used separate the two fields that make up a service announcement.
	private static final String DELIMITER = "\t";

	private InetSocketAddress addr;
	private String serviceURI;
	private String domainName;
	public HashMap<String, DomainInfo> record;

	/**
	 * @param serviceName the name of the service to announce
	 * @param serviceURI  an uri string - representing the contact endpoint of the
	 *                    service being announced
	 * @throws UnknownHostException
	 */
	public Discovery(String serviceName, String serviceURI) throws UnknownHostException {
		this.addr = DISCOVERY_ADDR;
		this.serviceURI = serviceURI;
		this.record = new HashMap<String, DomainInfo>();
		this.domainName = InetAddress.getLocalHost().getCanonicalHostName();
	}

	public class DomainInfo {
		private String uri;
		private long time;
		private boolean isRest;

		private DomainInfo(String uri) {
			//Gets the last 4 chars of a URI to check if it's a soap or rest server
			this.isRest = uri.substring(uri.length() - 4).equalsIgnoreCase("rest");
			this.uri = uri;
			this.time = System.currentTimeMillis();
		}

		private long getTime() {
			return this.time;
		}

		private void setTime() {
			this.time = System.currentTimeMillis();
		}

		public String getUri() {
			return this.uri;
		}

		public boolean isRest() {
			return this.isRest;
		}

		@Override
		public String toString() {
			return String.format("URI: %s Received at: %s", this.uri, this.time);
		}
	}

	/**
	 * Starts sending service announcements at regular intervals...
	 */
	public void start() {
		byte[] announceBytes = String.format("%s%s%s", this.domainName, DELIMITER, serviceURI).getBytes();
		DatagramPacket announcePkt = new DatagramPacket(announceBytes, announceBytes.length, addr);

		try {
			MulticastSocket ms = new MulticastSocket(addr.getPort());
			ms.joinGroup(addr.getAddress());

			// start thread to send periodic announcements
			new Thread(() -> {
				for (;;) {
					try {
						ms.send(announcePkt);
						Thread.sleep(DISCOVERY_PERIOD);
					} catch (Exception e) {
						e.printStackTrace();
						// do nothing
					}
				}
			}).start();

			// start thread to collect announcements
			new Thread(() -> {
				DatagramPacket pkt = new DatagramPacket(new byte[1024], 1024);
				String uri;
				DomainInfo info;

				for (;;) {
					try {
						pkt.setLength(1024);
						ms.receive(pkt);
						String msg = new String(pkt.getData(), 0, pkt.getLength());
						String[] msgElems = msg.split(DELIMITER);
						if (msgElems.length == 2) { // periodic announcement
							String domainName = pkt.getAddress().getHostName().split("\\.")[0];

							uri = msgElems[1];
							info = record.get(domainName);

							if (info == null) 
								record.put(domainName, new DomainInfo(uri));
							 else 
								info.setTime();
							
						}
					} catch (IOException e) {
						// do nothing
					}
				}
			}).start();

			//Cleanup thread. Removes inactive servers.
			new Thread(() -> {

				for (;;) {
					for (String domain : this.record.keySet()) {
						if (System.currentTimeMillis() - this.record.get(domain).getTime() > DEAD_PERIOD)
							this.record.remove(domain);
					}

					try {
						Thread.sleep(DISCOVERY_TIMEOUT);
					} catch (InterruptedException e) {
						
					}
				}

			});
		} catch (Exception e) {
			//do nothing
		}
	}

	/**
	 * Returns the known server for a service.
	 * 
	 * @param  serviceName the name of the service being discovered
	 * @return DomainInfo Object for the given domain. Null in none found 
	 * 
	 */
	public DomainInfo knownUrisOf(String domainName) {
		return this.record.get(domainName);
	}
}
