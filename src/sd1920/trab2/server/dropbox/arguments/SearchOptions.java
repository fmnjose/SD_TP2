package sd1920.trab2.server.dropbox.arguments;

public class SearchOptions {
    final String path;
    final boolean filename_only;    
    
    public SearchOptions(String path){
        this.path = path;
        this.filename_only = true;
    }

}
