package sd1920.trab2.server.dropbox.arguments;

public class SearchFileArgs {
    final String path;
    final String query;
    final boolean include_highlights;
    final boolean filename_only;

    public SearchFileArgs(String path, String query) {
        this.path = path;
        this.query = query;
        this.include_highlights = false;
        this.filename_only = true;
    }
}