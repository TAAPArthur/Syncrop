package transferManager.queue;

import daemon.SyncDaemon;

public class QueueMember implements Comparable<QueueMember>{
	private String path;
	private String owner;
	String target;
	final long timeStamp;
	boolean smallFile;
	boolean wasConnectionActiveAtQueueEntry=SyncDaemon.isConnectionActive();
	public QueueMember(String path,String owner,String target,boolean smallFile){
		this.path=path;
		this.owner=owner;
		this.target=target;
		this.smallFile=smallFile;
		timeStamp=System.currentTimeMillis();
	}
	@Override
	public boolean equals(Object o){
		if(!(o instanceof QueueMember))return false;
		QueueMember queueMember=(QueueMember) o;
		return path.equals(queueMember.path)&&
				((target==null&&queueMember.target==null)||target.equals(queueMember.target));
	}
	public String getPath(){
		return path;
	}
	public String getOwner(){
		return owner;
	}
	public String getTarget(){
		return target;
	}
	public long getTimeStamp(){return timeStamp;}
	@Override
	public String toString(){
		return "path:"+path+"; owner:"+owner+"; target:"+target;
	}
	@Override
	public int compareTo(QueueMember o) {
		if(wasConnectionActiveAtQueueEntry^o.wasConnectionActiveAtQueueEntry)
			if(smallFile^o.smallFile)
				return (int)(timeStamp-o.timeStamp);
			else if(smallFile)
				return -1;
			else return 1;
		else if(wasConnectionActiveAtQueueEntry)
			return -1;
		else return 1;
		
	}
}