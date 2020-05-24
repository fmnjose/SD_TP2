package sd1920.trab2.server.proxy.arguments;

/**
 * Arguments used by the dropbox ListFolderContinue endpoint
 */
public class ListFolderContinueArgs {
	final String cursor;
	
	public ListFolderContinueArgs(String cursor) {
		this.cursor = cursor;
	}	
}
