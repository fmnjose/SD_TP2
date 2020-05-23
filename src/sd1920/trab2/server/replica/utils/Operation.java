package sd1920.trab2.server.replica.utils;

import java.util.LinkedList;
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
    }

    private Type opType;
    private List<Object> args;
    private long bigbang;

    public Operation(Type opType, List<Object> args){
        this.opType = opType;
        this.args = args;
        this.bigbang = System.currentTimeMillis();
    }

    public Operation(Type opType,  Object arg){
        this.opType = opType;
        this.args = new LinkedList<>();
        this.args.add(arg);
        this.bigbang = System.currentTimeMillis();
    }

    public long getCreationTime(){
        return bigbang;
    }

    public Type getType(){
        return this.opType;
    }

    public List<Object> getArgs(){
        return this.args;
    }
}