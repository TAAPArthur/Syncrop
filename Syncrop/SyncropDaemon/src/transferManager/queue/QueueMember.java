package transferManager.queue;

import daemon.SyncDaemon;
import file.SyncROPItem;

public class QueueMember implements Comparable<QueueMember>{
	private String path;
	private String owner;
	private String target;
	private final long timeStamp;
	private long dateModified;
	private int fileSizeTier;
	
	public QueueMember(SyncROPItem fileToAddToQueue,String target){
		this(fileToAddToQueue.getPath(),fileToAddToQueue.getOwner(),fileToAddToQueue.getDateModified(), target,fileToAddToQueue.getSize());
	}
	private QueueMember(String path,String owner,long dateModifed,String target,long size){
		this.path=path;
		this.owner=owner;
		this.target=target;
		timeStamp=System.currentTimeMillis();
		fileSizeTier=(int) (size/SyncDaemon.TRANSFER_SIZE);
		this.dateModified=dateModifed;
	}
	public long getDateModified(){return dateModified;}
	public boolean isLargeFile(){
		return fileSizeTier>0;
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
	public long getTimeInQueue(){
		return System.currentTimeMillis()-timeStamp;
	}
	
	@Override
	public String toString(){
		return "path:"+path+"; owner:"+owner+"; target:"+target;
	}
	@Override
	public int compareTo(QueueMember o) {	
		if(fileSizeTier==o.fileSizeTier)
			if(dateModified==o.dateModified)
				return (int)(timeStamp-o.timeStamp);
			else return (int)(dateModified-o.dateModified);
		else return fileSizeTier-o.fileSizeTier;
		
	}
}