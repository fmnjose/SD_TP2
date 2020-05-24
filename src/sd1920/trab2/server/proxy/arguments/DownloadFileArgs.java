package sd1920.trab2.server.proxy.arguments;

/**
 * Arguments used by the dropbox Download endpoint
 */
public class DownloadFileArgs {
    final String path;
    
    public DownloadFileArgs(String path) {
        this.path = path;
    }
}