package message;

import java.io.IOException;

public interface Messenger {

		
	/**
	 * 
	 * @return true if and only if the Messenger is connected to a Server
	 */
	public boolean isConnectedToServer();
	/**
	 * 
	 * @return true if and only if the Messenger is connected to a
	 *  Server and at least one other Messenger
	 */
	public boolean isConnectionAccepted();
	
	/**
	 * Closes the connection between the Messenger and the Server. If no connection has been
	 * established or if the connection has already been closed then this method has non effect.
	 * @param reason the reason for the close
	 * @param localClose if the close was requested externally (false) or internally (true)
	 * @see #getReasonToClose()
	 */
	public void closeConnection(String reason,boolean localClose);
	/**
	 * 
	 * @return the reason the connection was closed
	 * @see #closeConnection(String, boolean)
	 */
	public String getReasonToClose();
	
	public Message readMessage() throws IOException;
	public Message readNotification() throws IOException;
	
	/**
	 * Prints a message
	 * @param m the message to print
	 */
	public void printMessage(Message m);
	/**
	 * Constructs a Message with message o and set userID 
	 * @param o the message of Message object to be created
	 * @see Message#Message(Object, String)
	 */
	public void printMessage(Object o);
	public void printMessage(Object o,String header);
	public void printMessage(Object o,int type,String header);
	public void printMessage(Object o,String header,String... target);
	public void printMessage(Object o,String header,String[] targetsToInclude,String... targetsToExclude);
	
	public int getTimeout();
	public int getExpectedRoundTripTime();

}
