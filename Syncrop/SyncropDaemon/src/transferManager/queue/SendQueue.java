package transferManager.queue;

import java.util.PriorityQueue;

import file.SyncROPItem;

public class SendQueue {
	
	PriorityQueue<QueueMember>queue=new PriorityQueue<QueueMember>();
	public boolean add(SyncROPItem fileToAddToQueue,String target){
		return add(new QueueMember(fileToAddToQueue, target));
	}
	public boolean add(QueueMember queueMember){
		if(queue.contains(queueMember))
			queue.remove(queueMember);
		queue.add(queueMember);
		return true;
	}
	/**
	 * Retrieves and removes the first (lowest) element, or returns null if this set is empty.
	 * @return the first element, or null if this set is empty
	 */
	public QueueMember poll(){
		return queue.poll();
	}
	/**
	 * Returns the first (lowest) element currently in this set.
	 * @return
	 * 	the first (lowest) element currently in this set.
	 */
	public QueueMember peek(){
		return queue.peek();
	}
	/**
	 * Returns true if this set contains no elements.
	 * @return
	 * 	true if this set contains no elements.

	 */
	public boolean isEmpty(){return queue.isEmpty();}
	/**
	 * Returns the number of elements in this set (its cardinality).
	 * @return the number of elements in this set (its cardinality)
	 */
	public int size(){return queue.size();}
	/**
	 * Removes all of the elements from this set. The set will be empty after this call returns.
	 */
	public void clear(){queue.clear();}
	
}