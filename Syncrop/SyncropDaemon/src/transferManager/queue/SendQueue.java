package transferManager.queue;

import java.util.LinkedList;

import file.SyncROPItem;

public class SendQueue extends LinkedList<QueueMember>{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static volatile boolean addLast=true;
	public boolean add(SyncROPItem fileToAddToQueue,String target){
		QueueMember queueMember=new QueueMember(fileToAddToQueue.getPath(),fileToAddToQueue.getOwner(), target);
		removeFirstOccurrence(queueMember);
		if(addLast)
			addLast(queueMember);
		else addFirst(queueMember);
		return true;
	}
	public static void setAddLast(boolean addLast) {
		SendQueue.addLast = addLast;
	}
}