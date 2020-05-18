package sd1920.trab2.replication;

import java.util.LinkedList;
import java.util.List;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

public class VersionControl {

    private static final long TTL = 18000;
    private boolean isPrimary;

    private ZookeeperProcessor zk;
    private String domain;
    private String uri;
    private int headVersion;
    private String primaryUri;

    private List<Operation> ops;

    public VersionControl(String domain, String uri) throws Exception{
        this.domain = domain;
        this.uri = uri;
        isPrimary = false;
        this.zk = new ZookeeperProcessor( "localhost:2181,kafka:2181");
		String newPath = zk.write(domain, CreateMode.PERSISTENT);
		
		newPath = zk.write(domain + "/server_", uri, CreateMode.EPHEMERAL_SEQUENTIAL);
        System.out.println("Created child znode: " + newPath);
        
        this.headVersion = 0;
        ops = new LinkedList<>();
    }

    public void startListening(){
        zk.getChildren( this.domain, new Watcher() {
			@Override
			public void process(WatchedEvent event) {
                List<String> lst = zk.getChildren( domain, this);
                byte[] b = zk.getData("/" + lst.get(0));
                String puri = new String(b);
                isPrimary = uri.equals(puri);

                if(!isPrimary)
                    primaryUri = puri;
			}
		});
    }

    public void addOperation(Operation op){
        purgeList();
        ops.add(op);
    }
    
    public void purgeList(){
        while(System.currentTimeMillis() - ops.get(0).getCreationTime() >= TTL){
            headVersion++;
            ops.remove(0);
        }
    }

    public boolean isPrimary(){
        return this.isPrimary;
    }

    public String getPrimaryUri(){
        return this.primaryUri;
    }
}