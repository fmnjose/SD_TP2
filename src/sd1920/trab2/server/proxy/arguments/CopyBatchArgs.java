package sd1920.trab2.server.proxy.arguments;

import java.util.List;

public class CopyBatchArgs {
    final List<CopyArgs> entries;
    final boolean autorename;

    public CopyBatchArgs(List<CopyArgs> entries){
        this.entries = entries;
        this.autorename = false;
    }
    
}