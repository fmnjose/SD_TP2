package sd1920.trab2.server.proxy.arguments;

/**
 * Arguments used by the dropbox GetMeta endpoint
 */
public class GetMetaArgs {
    final String path;
    final boolean include_deleted;

    public GetMetaArgs(String path){
        this.path = path;
        this.include_deleted = false;
    }
}