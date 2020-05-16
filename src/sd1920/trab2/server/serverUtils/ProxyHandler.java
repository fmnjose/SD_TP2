package sd1920.trab2.server.serverUtils;

import java.util.LinkedList;
import java.util.List;

import sd1920.trab2.server.dropbox.requests.Copy;

public class CopyHandler implements Runnable{

    private List<CopyRequest> copies;
    private DropboxServerUtils utils;
    private boolean forwarded;

    public CopyHandler(List<CopyRequest> copies, DropboxServerUtils utils, boolean forwarded){
        this.copies = copies;
        this.utils = utils;
    }

    public CopyHandler(CopyRequest copy, DropboxServerUtils utils, boolean forwarded){
        this.copies = new LinkedList<>();
        copies.add(copy);

        this.utils = utils;
    }

    @Override
    public void run() {
        while(!copies.isEmpty()){
            CopyRequest copy = copies.remove(0);
            if(Copy.run(copy.getCopy()))
                System.out.println("CopyHandler: Sucessfully saved");
            else{ 
                System.out.println("CopyHandler: Couldn't copy");
                if(!forwarded)
                    this.utils.saveErrorMessages(copy.getSender(), copy.getRecipient(), copy.getMessage());
            }
        }
    }
    
}