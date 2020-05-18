package sd1920.trab2.replication;

import java.util.List;

public class Operation {
    
    public enum Type{
        POST_MESSAGE,
        DELETE_MESSAGE,
        REMOVE_FROM_INBOX,
        POST_FORWARDED,
        DELETE_FORWARDED,
        POST_USER,
        UPDATE_USER,
        DELETE_USER,
        CREATE_INBOX,
    }

    private Type opType;
    private List<String> args;
    private long bigbang;

    public Operation(Type opType, List<String> args){
        this.opType = opType;
        this.args = args;
        this.bigbang = System.currentTimeMillis();
    }

    public long getCreationTime(){
        return bigbang;
    }
}