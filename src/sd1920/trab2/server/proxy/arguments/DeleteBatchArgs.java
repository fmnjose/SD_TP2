package sd1920.trab2.server.proxy.arguments;

import java.util.List;

/**
 * Arguments used by the dropbox DeleteBatch endpoint
 */
public class DeleteBatchArgs {

    final List<DeleteArgs> entries;

    public DeleteBatchArgs(List<DeleteArgs> entries){
        this.entries = entries;
    }
    
}