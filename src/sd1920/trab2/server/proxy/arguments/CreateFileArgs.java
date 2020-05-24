package sd1920.trab2.server.proxy.arguments;

/**
 * Arguments used by the dropbox CreateFile endpoint
 */
public class CreateFileArgs {

    final String path;
    final String mode;
    final boolean autorename;
    final boolean mute;
    final boolean strict_conflict;

    public CreateFileArgs(String path){
        this.path = path;
        this.mode = "overwrite";
        this.autorename = false;
        this.mute = false;
        this.strict_conflict = false;
    }
}