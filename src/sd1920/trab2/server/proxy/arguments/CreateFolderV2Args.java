package sd1920.trab2.server.proxy.arguments;

/**
 * Arguments used by the dropbox CreateFolderV2 endpoint
 */
public class CreateFolderV2Args {
	final String path;
	final boolean autorename;

	public CreateFolderV2Args(String path, boolean autorename) {
		this.path = path;
		this.autorename = autorename;
	}
}