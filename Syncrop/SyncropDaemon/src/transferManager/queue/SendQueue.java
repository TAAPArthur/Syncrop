package transferManager.queue;

import java.util.LinkedList;

import file.SyncROPItem;

public class SendQueue extends LinkedList<QueueMember>{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public boolean add(SyncROPItem fileToAddToQueue,String target){
		QueueMember queueMember=new QueueMember(fileToAddToQueue.getPath(),fileToAddToQueue.getOwner(), target);
		removeFirstOccurrence(queueMember);
		add(queueMember);
		return true;
	}
}