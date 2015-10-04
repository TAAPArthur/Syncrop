package syncrop;

/**
 * 
 * This exception is thrown to indiscreet the a method threw an error due to Syncrop safely
 * shutting down. The stack trace should not  be logged
 *
 */
public class SyncropCloseException extends RuntimeException{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public SyncropCloseException(){
		super("operation aborted because Syncrop is closing");
	}

}
