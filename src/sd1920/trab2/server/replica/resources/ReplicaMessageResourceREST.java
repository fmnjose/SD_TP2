package sd1920.trab2.server.replica.resources;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.Response.Status;

import sd1920.trab2.server.serverUtils.LocalServerUtils;
import sd1920.trab2.server.replica.ReplicaMailServerREST;
import sd1920.trab2.server.replica.utils.Operation;
import sd1920.trab2.server.replica.utils.VersionControl;
import sd1920.trab2.api.Message;
import sd1920.trab2.api.User;
import sd1920.trab2.api.replicaRest.ReplicaMessageServiceRest;

@Singleton
public class ReplicaMessageResourceREST extends LocalServerUtils implements ReplicaMessageServiceRest {

	private VersionControl vc;

	public ReplicaMessageResourceREST() throws UnknownHostException {
		super(ServerTypes.REST_REPLICA);

		this.randomNumberGenerator = new Random(System.currentTimeMillis());

		Log = Logger.getLogger(ReplicaMailServerREST.class.getName());

		this.domain = InetAddress.getLocalHost().getHostName();

		this.serverUri = String.format(DOMAIN_FORMAT_REST, InetAddress.getLocalHost().getHostAddress(),
				ReplicaMailServerREST.PORT);

		vc = ReplicaMailServerREST.vc;
	}

	@Override
	public void execPostMessage(long version, Message msg, String secret) {
		Log.info("execPostMessage: " + msg.getId());

		if (!secret.equals(ReplicaMailServerREST.secret)) {
			Log.info("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		vc.syncVersion(version);

		Set<String> recipientDomains = new HashSet<>();

		String senderName = getSenderCanonicalName(msg.getSender());

		synchronized (this.allMessages) {
			allMessages.put(msg.getId(), msg);
		}

		for (String recipient : msg.getDestination()) {
			String[] tokens = recipient.split("@");
			if (tokens[1].equals(this.domain))
				saveMessage(senderName, recipient, false, msg);
			else{
				recipientDomains.add(tokens[1]);
			}
		}
		
		this.forwardMessage(recipientDomains, msg, ServerTypes.REST_REPLICA);
		
		vc.addOperation(new Operation(Operation.Type.POST_MESSAGE, msg));
	}

	@Override
	public long postMessage(String pwd, Message msg) {

		if (!vc.isPrimary()){
			String redirectPath = String.format(POST_MESSAGE_FORMAT, vc.getPrimaryUri());
			redirectPath = UriBuilder.fromPath(redirectPath).queryParam("pwd",pwd).toString();
			throw new WebApplicationException(Response.temporaryRedirect(URI.create(redirectPath)).build());
		}

		User user;
		String sender = msg.getSender();

		Log.info("postMessage: Sender: " + sender + "; Subject: " + msg.getSubject() + ")");

		if (sender == null || msg.getDestination() == null || msg.getDestination().size() == 0) {
			Log.info("postMessage: Message was rejected due to lack of recepients.");
			throw new WebApplicationException(Status.CONFLICT);
		}

		String senderName = getSenderCanonicalName(sender);

		user = this.getUserRest(senderName, pwd);

		if (user == null)
			throw new WebApplicationException(Status.FORBIDDEN);

		long newID = Math.abs(randomNumberGenerator.nextLong());

		synchronized (this.allMessages) {
			while (allMessages.containsKey(newID))
				newID = Math.abs(randomNumberGenerator.nextLong());

			msg.setSender(String.format(SENDER_FORMAT, user.getDisplayName(), user.getName(), user.getDomain()));
			msg.setId(newID);
		}

		vc.waitForVersion();

		if(!vc.postMessage(msg))
			throw new WebApplicationException(Status.SERVICE_UNAVAILABLE);

		throw new WebApplicationException(Response.status(200).
			header(ReplicaMessageServiceRest.HEADER_VERSION, vc.getVersion()).
			entity(newID).build());
	}

	@Override
	public Message getMessage(long version, String user, long mid, String pwd) {

		vc.syncVersion(version);

		User u = this.getUserRest(user, pwd);

		if (u == null) {
			throw new WebApplicationException(Status.FORBIDDEN);
		}
		synchronized (this.allMessages) {
			synchronized (this.userInboxs) {
				if (!this.allMessages.containsKey(mid) || !this.userInboxs.get(user).contains(mid)) {
					Log.info("getMessage: Requested message does not exists.");
					throw new WebApplicationException(Status.NOT_FOUND);
				}
			}
		}

		throw new WebApplicationException(Response.status(200).
			header(ReplicaMessageServiceRest.HEADER_VERSION, vc.getVersion()).entity(this.allMessages.get(mid)).build());
	}

	@Override
	public List<Long> getMessages(long version, String user, String pwd) {

		vc.syncVersion(version);
		
		User u = this.getUserRest(user, pwd);

		if (u == null) {
			Log.info("getMessages: User with name " + user + " does not exist in the domain.");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		Set<Long> mids = new HashSet<Long>();

		synchronized (this.userInboxs) {
			mids = userInboxs.getOrDefault(user, Collections.emptySet());
		}

		throw new WebApplicationException(Response.status(200).
			header(ReplicaMessageServiceRest.HEADER_VERSION, vc.getVersion()).
			entity(new ArrayList<>(mids)).build());
	}

	@Override
	public void execDeleteMessage(long version, String user, long mid, String secret) {
		Log.info("execDeleteMessage: " + Long.toString(mid));

		if (!secret.equals(ReplicaMailServerREST.secret)) {
			Log.info("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		vc.syncVersion(version);

		Message msg = null;

		synchronized(this.allMessages){
			msg = this.allMessages.get(mid);
		}

		synchronized (this.userInboxs) {
			this.userInboxs.get(user).remove(mid);
		}

		// this will be used to compile the domains that we'll need to forward the
		// request to
		Set<String> recipientDomains = new HashSet<>();

		for (String u : msg.getDestination()) {
			String[] tokens = u.split("@");
			if (tokens[1].equals(this.domain)) {
				synchronized (this.userInboxs) {
					userInboxs.get(tokens[0]).remove(mid);
				}
			} else
				recipientDomains.add(tokens[1]);
		}

		forwardDelete(recipientDomains, mid, ServerTypes.REST_REPLICA);

		
		List<Object> args = new LinkedList<>();
		
		args.add(user);
		args.add(mid);
		
		vc.addOperation(new Operation(Operation.Type.DELETE_MESSAGE, args));
	}

	@Override
	public void deleteMessage(String user, long mid, String pwd) {
		Log.info("deleteMessage: " + String.valueOf(mid));

		if (!vc.isPrimary()){
			String redirectPath = String.format(DELETE_MESSAGE_FORMAT, vc.getPrimaryUri(), user, mid);
			redirectPath = UriBuilder.fromPath(redirectPath).queryParam("pwd", pwd).toString();
			throw new WebApplicationException(Response.temporaryRedirect(URI.create(redirectPath)).build());
		}

		User sender = null;

		Message msg;

		synchronized (this.allMessages) {
			msg = this.allMessages.get(mid);
		}

		sender = this.getUserRest(user, pwd);

		if (sender == null) {
			Log.info("delete message: User not found or wrong password");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		String userName = getSenderCanonicalName(user);

		if (msg == null || !getSenderCanonicalName(msg.getSender()).equals(userName))
			return;

		vc.waitForVersion();

		if(!vc.deleteMessage(user, mid))
			throw new WebApplicationException(Status.SERVICE_UNAVAILABLE);

		throw new WebApplicationException(Response.status(204).
			header(ReplicaMessageServiceRest.HEADER_VERSION, vc.getVersion()).build());
	}

	@Override
	public void execRemoveFromUserInbox(long version, String user, long mid, String secret) {
		Log.info("execRemoveFromUserInbox: Sender: " + user + "; Message: " + mid);
		
		if (!secret.equals(ReplicaMailServerREST.secret)) {
			Log.info("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		vc.syncVersion(version);

		synchronized (this.userInboxs) {
			this.userInboxs.get(user).remove(mid);
		}

		List<Object> args = new LinkedList<>();

		args.add(user);
		args.add(mid);

		vc.addOperation(new Operation(Operation.Type.REMOVE_FROM_INBOX, args));
	}

	@Override
	public void removeFromUserInbox(String user, long mid, String pwd) {
		Log.info("removeFromUserInbox: Message: " + String.valueOf(mid) + "; Inbox: " + user);

		if (!vc.isPrimary()){
			String redirectPath = String.format(REMOVE_FROM_INBOX_FORMAT, vc.getPrimaryUri(), user, mid);
			redirectPath = UriBuilder.fromPath(redirectPath).queryParam("pwd", pwd).toString();
			throw new WebApplicationException(Response.temporaryRedirect(URI.create(redirectPath)).build());
		}

		User u = this.getUserRest(user, pwd);

		if (u == null) {
			Log.info("removeFromUserInbox: User with name " + user + " does not exist in the domain.");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		synchronized (this.allMessages) {
			synchronized (this.userInboxs) {
				if (!this.allMessages.containsKey(mid) || !this.userInboxs.get(user).contains(mid)) {
					Log.info("removeFromUserInbox: Message not found");
					throw new WebApplicationException(Status.NOT_FOUND);
				}
			}
		}

		vc.waitForVersion();

		if(!vc.removeFromUserInbox(user, mid))
			throw new WebApplicationException(Status.SERVICE_UNAVAILABLE);

		throw new WebApplicationException(Response.status(204).
			header(ReplicaMessageServiceRest.HEADER_VERSION, vc.getVersion()).build());
	}

	@Override
	public void createInbox(String user, String secret) {
		if (!secret.equals(ReplicaMailServerREST.secret)) {
			Log.info("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		synchronized (this.userInboxs) {
			this.userInboxs.put(user, new HashSet<>());
		}
	}

	@Override
	public List<String> execPostForwardedMessage(long version, Message msg, String secret) {
		Log.info("execPostForwardedMessage: " + msg.getId());
		
		if (!secret.equals(ReplicaMailServerREST.secret)) {
			Log.info("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		vc.syncVersion(version);

		List<String> failedDeliveries = new LinkedList<>();


		for (String recipient : msg.getDestination()) {
			String[] tokens = recipient.split("@");
			if (tokens[1].equals(this.domain) && !this.saveMessage(msg.getSender(), tokens[0], true, msg))
				failedDeliveries.add(recipient);
		}

		vc.addOperation(new Operation(Operation.Type.POST_FORWARDED, msg));

		return failedDeliveries;
	}

	@Override
	public List<String> postForwardedMessage(Message msg, String secret) {
		Log.info("postForwardedMessage: " + msg.getId());

		if (!secret.equals(ReplicaMailServerREST.secret)) {
			Log.info("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
		}
		
		if (!vc.isPrimary()){
			String redirectPath = String.format(POST_FORWARDED_FORMAT, vc.getPrimaryUri());
			redirectPath = UriBuilder.fromPath(redirectPath).queryParam("secret", secret).toString();
			throw new WebApplicationException(Response.temporaryRedirect(URI.create(redirectPath)).build());
		}

		vc.waitForVersion();
		
		List<String> failedDeliveries = vc.postForwardedMessage(msg);

		if(failedDeliveries == null)
			throw new WebApplicationException(Status.SERVICE_UNAVAILABLE);

		throw new WebApplicationException(Response.status(200).
			header(ReplicaMessageServiceRest.HEADER_VERSION, vc.getVersion()).
			entity(failedDeliveries).build());
	}

	@Override
	public void execDeleteForwardedMessage(long version, long mid, String secret) {
		Log.info("execDeleteForwardedMessage: " + Long.toString(mid));
		if (!secret.equals(ReplicaMailServerREST.secret)) {
			Log.info("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		vc.syncVersion(version);

		Set<String> recipients = null;

		synchronized(this.allMessages){
			recipients = this.allMessages.get(mid).getDestination();
			this.allMessages.remove(mid);
		}

		for (String s : recipients) {
			String[] tokens = s.split("@");
			if (tokens[1].equals(this.domain)) {
				synchronized (this.userInboxs) {
					userInboxs.get(tokens[0]).remove(mid);
				}
			}
		}
		vc.addOperation(new Operation(Operation.Type.DELETE_FORWARDED, mid));
	}

	@Override
	public void deleteForwardedMessage(long mid, String secret) {
		Log.info("deleteForwardedMessage: " + mid);

		if (!secret.equals(ReplicaMailServerREST.secret)) {
			Log.info("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		if (!vc.isPrimary()){
			String redirectPath = String.format(DELETE_FORWARDED_FORMAT, vc.getPrimaryUri(), mid);
			redirectPath = UriBuilder.fromPath(redirectPath).queryParam("secret", secret).toString();
			throw new WebApplicationException(Response.temporaryRedirect(URI.create(redirectPath)).build());
		}

		synchronized (this.allMessages) {
			if (!this.allMessages.containsKey(mid))
			return;
		}
		
		vc.waitForVersion();
			
		if(!vc.deleteForwardedMessage(mid))
			throw new WebApplicationException(Status.SERVICE_UNAVAILABLE);

		throw new WebApplicationException(Response.status(200).
			header(ReplicaMessageServiceRest.HEADER_VERSION, vc.getVersion()).build());
	}

	@Override
	public List<Operation> getUpdatedOperations(long version, String secret) {
		if (!secret.equals(ReplicaMailServerREST.secret)) {
			Log.info("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
		}
		
		if (version < vc.getHeadVersion())
			throw new WebApplicationException(Status.GONE);

		return vc.getOperations(version);
	}

	@Override
	public Map<Long, Message> getAllMessages(String secret) {
		if (!secret.equals(ReplicaMailServerREST.secret)) {
			Log.info("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		return this.allMessages;
	}

	@Override
	public Map<String, Set<Long>> getUserInboxes(String secret) {
		if (!secret.equals(ReplicaMailServerREST.secret)) {
			Log.info("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		return this.userInboxs;
	}

	@Override
	public void updateAllMessages(Map<Long, Message> allMessages, String secret) {
		if (!secret.equals(ReplicaMailServerREST.secret)) {
			Log.info("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		this.allMessages = allMessages;
	}

	@Override
	public void updateUserInboxes(Map<String, Set<Long>> usersInboxes, String secret) {		
		if (!secret.equals(ReplicaMailServerREST.secret)) {
			Log.info("An intruder!");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		this.userInboxs = usersInboxes;
	}
}
