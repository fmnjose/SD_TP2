package sd1920.trab2.server.dropbox.arguments;

import java.util.List;

public class DeleteBatchArgs {

    final List<DeleteArgs> entries;

    public DeleteBatchArgs(List<DeleteArgs> entries){
        this.entries = entries;
    }
    
}