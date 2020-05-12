package sd1920.trab2.server.dropbox.arguments;

public class SearchFileArgs {
    final String query;
    final SearchOptions options;
    final boolean include_highlights;

    public SearchFileArgs(String path, String query) {
        this.query = query;
        this.options = new SearchOptions(path);
        this.include_highlights = false;
    }
}