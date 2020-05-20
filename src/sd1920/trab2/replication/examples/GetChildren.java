package sd1920.trab2.replication.examples;

import java.util.List;
import java.util.Scanner;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

import sd1920.trab2.replication.ZookeeperProcessor;

public class GetChildren {
	// Main just for testing purposes
	public static void main( String[] args) throws Exception {
		Scanner sc = new Scanner(System.in);

		System.out.println("Provide a path (should start with /) :");
		String path = sc.nextLine().trim();

		final ZookeeperProcessor zk = new ZookeeperProcessor( "kafka:2181");
		zk.getChildren( path, new Watcher() {
			@Override
			public void process(WatchedEvent event) {
				List<String> lst = zk.getChildren( path, this);
				lst.stream().forEach( e -> System.out.println(e));
				System.out.println();
			}
			
		});
		
		System.out.println("Enter for stop observing changes :");
		sc.nextLine().trim();

		sc.close();

	}

}
