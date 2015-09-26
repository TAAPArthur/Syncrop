package syncrop;

public class SyncropCloseException extends RuntimeException{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public SyncropCloseException(){
		super("operation aborted because Syncrop is closing");
	}

}
