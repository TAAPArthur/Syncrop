package listener;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.nio.file.WatchEvent;

public class EventQueueMember implements Comparable<EventQueueMember>{
	private final String owner;
	private final WatchedDir dir;
	private final String path;
	
	private final WatchEvent.Kind<?>kind;
	private final long timeStamp;
	EventQueueMember(WatchedDir dir,String path, WatchEvent.Kind<?>kind){
		this.owner=dir.getAccountName();
		this.dir=dir;
		this.path=path;
		this.kind=kind;
		timeStamp=System.currentTimeMillis();
	}
	/**
	 * Returns true if the event is stable -- if the file is thought
	 * to stop changing
	 * @return 
	 */
	public boolean isStable(){
		return System.currentTimeMillis()-timeStamp>getBuffer();
	}
	private int getBuffer(){
		if(kind==ENTRY_CREATE)
			return 1000;
		else if(kind==ENTRY_MODIFY)
			return 1000;
		else if(kind==ENTRY_DELETE)
			return 2500;
		else 
			return 5000;
	}
	
	public WatchEvent.Kind<?> getKind(){return kind;}
	public boolean equals(Object o){
		return o instanceof EventQueueMember&&
				((EventQueueMember)o).owner.equals(owner)&&
				((EventQueueMember)o).path.equals(path);
	}
	public String toString(){
		return "path:"+path+" "+kind;
	}
	/**
	 * Returns the time when the file will become stable if no more events
	 * are triggered
	 * @return timestamp + buffer
	 */
	private long getAdjustedTimeStamp(){
		return timeStamp+getBuffer();
	}
	public long getTimeStamp() {
		return timeStamp;
	}

	@Override
	public int compareTo(EventQueueMember o) {
		return (int) (o.getAdjustedTimeStamp()-getAdjustedTimeStamp());
	}
	public WatchedDir getDir() {
		return dir;
	}
	public String getPath() {
		return path;
	}
}