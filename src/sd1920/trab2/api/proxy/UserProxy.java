package sd1920.trab2.api.proxy;

import java.util.HashSet;
import java.util.Set;

import sd1920.trab2.api.User;

public class UserProxy {

    private User user;
    private Set<Long> mids;
    
    public UserProxy(User user){
        this.user = user;
        this.mids = new HashSet<>();
    }

    public User getUser(){
        return this.user;
    }

    public Set<Long> getMids(){
        return this.mids;
    }
}