package sd1920.trab2.server.dropbox.arguments;

public class CreateFolderV2Args {
	final String path;
	final boolean autorename;

	public CreateFolderV2Args(String path, boolean autorename) {
		this.path = path;
		this.autorename = autorename;
	}
}