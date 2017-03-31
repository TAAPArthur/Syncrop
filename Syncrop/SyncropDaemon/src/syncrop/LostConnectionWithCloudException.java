package syncrop;

public class LostConnectionWithCloudException extends RuntimeException{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public LostConnectionWithCloudException(){
		super("Lost connection with cloud");
	}

}
