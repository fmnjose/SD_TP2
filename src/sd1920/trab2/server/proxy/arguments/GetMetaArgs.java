package sd1920.trab2.server.proxy.arguments;

public class GetMetaArgs {
    final String path;
    final boolean include_deleted;

    public GetMetaArgs(String path){
        this.path = path;
        this.include_deleted = false;
    }
}