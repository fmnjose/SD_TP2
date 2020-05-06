package sd1920.trab2.api.soap;

import java.util.List;

import javax.jws.WebMethod;
import javax.jws.WebService;

import sd1920.trab2.api.Message;

@WebService(serviceName=MessageServiceSoap.NAME, 
	targetNamespace=MessageServiceSoap.NAMESPACE, 
	endpointInterface=MessageServiceSoap.INTERFACE)
public interface MessageServiceSoap {
	
	static final String NAME = "messages";
	static final String NAMESPACE = "http://sd2019";
	static final String INTERFACE = "sd1920.trab2.api.soap.MessageServiceSoap";
	
	/**
	 * Posts a new message to the server, associating it to the inbox of every individual destination.
	 * An outgoing message should be modified before delivering it, by assigning an ID, and
	 * by changing the sender to be in the format "display name <name@domain>", with display name the
	 * display name associated with a user.
	 * NOTE: there might be some destinations that are not from the local domain (see grading for 
	 * how addressing this feature is valued).
	 * 
	 * @param msg the message object to be posted to the server
	 * @param pwd password of the user sending the message
	 * @return the unique numerical identifier for the posted message. 
	 * @throws MessagesException in case of error.
	 */
	@WebMethod
	public long postMessage(String pwd, Message msg) throws MessagesException;
	
	/**
	 * Obtains the message identified by mid of user user
	 * @param user user name for the operation
	 * @param mid the identifier of the message
	 * @param pwd password of the user
	 * @return the message if it exists.
	 * @throws MessagesException in case of error.
	 */
	@WebMethod
	public Message getMessage(String user, String pwd, long mid) throws MessagesException;
		
	/**
	 * Returns a list of all ids of messages stored in the server for a given user
	 * @param user the username of the user whose messages should be returned
	 * @param pwd password of the user
	 * @return a list of ids potentially empty;
	 * @throws MessagesException in case of error.
	 */
	@WebMethod
	public List<Long> getMessages(String user, String pwd) throws MessagesException;

	/**
	 * Removes a message identified by mid from the inbox of user identified by user.
	 * @param user the username of the inbox that is manipulated by this method
	 * @param pwd password of the user
	 * @param mid the identifier of the message to be deleted
	 * @throws MessagesException in case of error.
	 */
	@WebMethod
	void removeFromUserInbox(String user, String pwd, long mid) throws MessagesException;

	/**
	 * Removes the message identified by mid from the inboxes of any server that holds the message.
	 * The deletion can be executed asynchronously and does not generate any error message if the
	 * message does not exist.
	 * 
	 * @param user the username of the sender of the message to be deleted
	 * @param mid the identifier of the message to be deleted
	 * @param pwd password of the user that sent the message
	 * @throws MessagesException in case of error.
	 */
	@WebMethod
	void deleteMessage(String user, String pwd, long mid) throws MessagesException;

	/**
	 * Creates an inbox for the user in the MessageResource
	 * @param user name of user
	 */
	@WebMethod
	void createInbox(String user) throws MessagesException;

	/**
	 * Forwards a message to the needed domains
	 * 
	 * @param msg              message to be forwarded
	 */
	@WebMethod
	List<String> postForwardedMessage(Message msg);

	/**
	 * Deletes a message in this server that was sent from another server
	 * @param mid
	 */
	@WebMethod
	void deleteForwardedMessage(long mid) throws MessagesException;
}
