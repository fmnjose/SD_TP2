package sd1920.trab2.server.proxy.arguments;

/**
 * Arguments used by the dropbox Delete endpoint
 */
public class DeleteArgs {
    final String path;

    public DeleteArgs(String path){
        this.path = path;
    }
}