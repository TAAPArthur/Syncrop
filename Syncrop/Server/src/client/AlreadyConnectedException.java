package client;

/**
 * 
 * This error is thrown when the trying to connect to a server when a connection 
 * has already been made
 *
 */
public class AlreadyConnectedException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public AlreadyConnectedException(String msg){
		super(msg);
	}



}
