package sd1920.trab2.server.replica.utils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import sd1920.trab2.server.replica.ReplicaMailServerREST;

public class SyncPoint
{		
    private static Logger Log = Logger.getLogger(ReplicaMailServerREST.class.getName());

	private static SyncPoint instance;
	public static SyncPoint getInstance() {
		if( instance == null)
			instance = new SyncPoint();
		return instance;
	}

	private Map<Long,String> result;
	private long version;
	
	
	private SyncPoint() {
		result = new HashMap<Long,String>();
	}
	
	/**
	 * Waits for version to be at least equals to n
	 */
	public synchronized void waitForVersion( long n) {
		while( version < n) {
			Log.info("waitForVersion: Waiting: " + Long.toString(n) + "; Current: " + Long.toString(version));
			try {
				wait();
			} catch (InterruptedException e) {
				// do nothing
			}
		}

		Log.info("waitForVersion: Free -> " + n);
	}

	/**
	 * Assuming that results are added sequentially, returns null if the result is not available.
	 */
	public synchronized String waitForResult( long n) {
		while( version < n) {
			try {
				wait();
			} catch (InterruptedException e) {
				// do nothing
			}
		}
		return result.remove(n);
	}

	/**
	 * Updates the version and stores the associated result
	 */
	public synchronized void setResult( long n, String res) {
		if( res != null)
			result.put(n, res);
		version = n;
		notifyAll();
	}

	/**
	 * Cleans up results that will not be consumed
	 */
	public synchronized void cleanupUntil( long n) {
		Iterator<Entry<Long,String>> it = result.entrySet().iterator();
		while( it.hasNext()) {
			if( it.next().getKey() < n)
				it.remove();
		}
	}

}
