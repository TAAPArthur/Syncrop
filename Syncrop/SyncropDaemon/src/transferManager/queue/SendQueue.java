package transferManager.queue;

import java.util.LinkedList;

import file.SyncROPItem;

public class SendQueue{
	LinkedList<QueueMember>queue=new LinkedList<QueueMember>();
	
	public SendQueue(){	}
	public boolean add(SyncROPItem fileToAddToQueue,String target){
		QueueMember queueMember=new QueueMember(fileToAddToQueue.getPath(),fileToAddToQueue.getOwner(), target);
		queue.removeFirstOccurrence(queueMember);
		queue.add(queueMember);
		//modifyQueue(queueMember);
		return true;
	}
	
	public QueueMember poll(){
		return queue.poll();
	}
	
	public void clear(){
		queue.clear();
	}
	public boolean isEmpty(){
		
		return queue.isEmpty()&&System.currentTimeMillis()-queue.peek().getTimeStamp()>1000;
	}
	public int size(){
		return queue.size();
	}
}