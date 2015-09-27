package transferManager.queue;

public class QueueMember{
	private String path;
	private String owner;
	String target;
	final long timeStamp;
	public QueueMember(String path,String owner,String target){
		this.path=path;
		this.owner=owner;
		this.target=target;
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
}