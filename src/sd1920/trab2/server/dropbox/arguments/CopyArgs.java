package sd1920.trab2.server.dropbox.arguments;

public class CopyArgs {
    final String from_path;
    final String to_path;

    public CopyArgs(String from_path, String to_path){
        this.from_path = from_path;
        this.to_path = to_path;
    }

    public String getToPath(){
        return this.to_path;
    }

    public String getFromPath(){
        return this.from_path;
    }
    
}