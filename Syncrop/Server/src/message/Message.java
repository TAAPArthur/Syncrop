package message;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;

/**
 * This class is used to send information between two {@link Messenger}s. A Message is sent
 * from one Messenger to another a Server to another Messenger 
 * There are 4 types of messages: 
 * <li>{@link  #TYPE_DEFAULT} - the default message type. This type is used to send 
 * important information from one Messenger to another and is ignored by the server
 * <li>{@link  #TYPE_NOTIFICATION} - This type is used to send notifications (information
 * that reports status of application) from one Messenger to another and is ignored by the server
 * <li>{@link  #TYPE_CONNECTION_INFO} - This type is used to send information regarding
 * the connection settings from a Messenger to the Sever and is not sent to another Messenger.
 * This type of Message is only used when creating a connection between a Messenger and the 
 * Server.
 * <li>{@link  #TYPE_MESSAGE_TO_CLIENT} - The only Message sent from the Server to a 
 * Messenger. The Message is read by the Messenger itself and is not delivered to the application
 * <li>{@link  #TYPE_MESSAGE_TO_SERVER} - The only Message sent from the Messenger itself
 * to the Sever. The Message is read by the Server and is not delivered to the application
 * 
 * Each Message has a message Object containing the information to send and a header String to 
 * categorize the Message being sent.
 * 
 *  Terminology
 *  <ul>
 *  <li> Server* - the #{@link server.Server} that runs on a remote computer
 *  <li> Primary Client or Primary Connection - the host connection which other clients connect to; there can only be one
 *  <li> Secondary Client or Secondary Connection - the clients that connect (which are on a client computer) to the server. There can be more than one.
 *  
 *  </ul>
 *  The Server can Primary Client can be built into one entity by using #{@link server.InternalServer}.
 * 
 */
public class Message implements Serializable
{
	
	private static final long serialVersionUID = 4L;

	/**
	 * the default message type. This type is used to send 
	 * important information from one Messenger to another and is ignored by the server
	 */
	public static final byte TYPE_DEFAULT=0;
	/**
	 * This type is used to send notifications (information
	 *that reports status of application) from one Messenger to another and 
	 *is ignored by the server
	 */
	public static final byte TYPE_NOTIFICATION=1;

	/**
	 * This type is used to send information regarding
	 * the connection settings from a Messenger to the Sever and is not sent to another 
	 * Messenger.
	 * This type of Message is only used when creating a connection between a Messenger and the 
	 * Server.
	 */
	public static final byte TYPE_CONNECTION_INFO=2;
	/**
	 * The only Message sent from the Server to a 
	 * Messenger. The Message is read by the Messenger itself and is not delivered to the 
	 * application
	 */
	public static final byte TYPE_MESSAGE_TO_CLIENT=3;
	/**
	 * The only Message sent from the Messenger itself
	 * to the Sever. The Message is read by the Server and is not delivered to the application
	 */
	public static final byte TYPE_MESSAGE_TO_SERVER=4;
	

	
	/**
	 * A message from the Server to Primary Client 
	 * indicating that their are no Secondary Clients<br/>
	 * Use with header {@link #TYPE_MESSAGE_TO_CLIENT} 
	 */
	public static final String MESSAGE_NO_CLIENTS="no clients";
	
	/**
	 * Should be sent with header #C
	 */
	public static final String MESSAGE_CONNECTION_ACCEPTED="CONNECTION ACCEPTED";
	public static final String MESSAGE_CONNECTION_REJECTED="CONNECTION REJECTED";
	public static final String MESSAGE_GET_CONNECTIONS="GET CONNECTIONS";
	public static final String MESSAGE_CONNECTION_MADE="CONNECTION MADE";
	
	/**
	 * A header for a Message from Primary to Server; 
	 * The message should contain String[] with the first index being the 
	 * name of the client and the rest being the list of aliases for 
	 * secondary client
	 */
	public static final String HEADER_SET_GROUP="set group";
	/**
	 * This header should be sent with type {@link #TYPE_MESSAGE_TO_CLIENT}. <br/>
	 * The message accompanying this header should return a String array of all the names of connections
	 * that the Secondary Client can connect to (ie application name matches and the connection is not full). 
	 */
	public static final String HEADER_AVAILABLE_CONNECTIONS="AVAILABLE CONNECTIONS";
	public static final String HEADER_MAKE_CONNECTION="make connection";
	public static final String HEADER_CONNECTION_REQUEST="connection request";
	public static final String HEADER_USER_LEFT="user left";
	public static final String HEADER_CLOSE_CONNECTION="CLOSE_CONNECTION";
	public static final String HEADER_REMOVE_USER="REMOVE USER";
	public static final String HEADER_NEW_USER="NEW USER";
	/**
	 * Used to tell recipient that the connection is still active<br/>
	 * Use with header {@link #TYPE_MESSAGE_TO_CLIENT} or {@link #TYPE_MESSAGE_TO_SERVER}
	 */
	
	public static final String HEADER_PING="PING";
	public static final String HEADER_PONG="PONG";
	
	private Object message;
	private String userID;
	private String header=null;
	private HashSet<String>targetsToInclude=new HashSet<String>(),
			targetsToExclude=new HashSet<String>();
	/**
	 * 0- default;<br/>1-Notification;<br/>2-connection info<br/>3-Message to Client<br/>4-Message to Server
	 */
	/**
	 * The type of Message to send. There are 4 types:
	 * <li>{@link  #TYPE_DEFAULT} 
	 * <li>{@link  #TYPE_NOTIFICATION}
	 * <li>{@link  #TYPE_CONNECTION_INFO} 
	 * <li>{@link  #TYPE_MESSAGE_TO_CLIENT}
	 * <li>{@link  #TYPE_MESSAGE_TO_SERVER}
	 * @see Message
	 */
	private byte type=0;
	
	public Message(Object message,String username){this(message, username, TYPE_DEFAULT);}
	public Message(Object message,String username,String header){
		this(message, username, TYPE_DEFAULT,header);
	}
	public Message(Object message,String username,String header,String[] targetsToInclude){
		this(message, username, TYPE_DEFAULT,header,targetsToInclude);
	}
	public Message(Object message,String username,String header,String[] targetsToInclude,String... targetsToExclude){
		this(message, username, TYPE_DEFAULT,header,targetsToInclude,targetsToExclude);
	}
	
	public Message(Object message,String username,int type){this(message, username, type, null);}
	public Message(Object message,String username,int type,String header){this(message, username, type, header,(String[])null);}
	public Message(Object message,String username,int type,String header,String... targetsToInclude){this(message, username, type, header, targetsToInclude, (String[])null);}
	public Message(Object message,String username,int type,String header,String[] targetsToInclude,String... targetsToExclude)
	{
		if(message==null)
			try {
				throw new NullPointerException("Message cannot be null "+header);
			} catch (Exception e) {
				
				e.printStackTrace();
				throw e;
			}
		this.message=message;this.userID=username;this.type=(byte) type;this.header=header;
		if(username==null)throw new NullPointerException("username cannot be null");
		addTargetsToInclude(targetsToInclude);
		addTargetsToExclude(targetsToExclude);
	} 
	/**
	 * Creates a new Message which is essentially a copy of message but ignores targets
	 * to include and targets to exclude
	 * @param message - the message to copy
	 */
	public Message(Message message)
	{
		this(message.message,message.userID,message.type,message.header);
	}
	
	/**
	 * Adds names to send this message to
	 * @param targetsToInclude a list of Messengers to send this message to
	 */
	public void addTargetsToInclude(String... targetsToInclude)
	{
		if(targetsToInclude!=null)
			this.targetsToInclude.addAll(Arrays.asList(targetsToInclude));
	}
	/**
	 * Adds names to not send this message to
	 * @param targetsToExclude a list of Messengers to not send this message to
	 */
	public void addTargetsToExclude(String... targetsToExclude)
	{
		if(targetsToExclude!=null)
			this.targetsToExclude.addAll(Arrays.asList(targetsToExclude));
	}
	
	
	/**
	 * @return true if there is a target to this message
	 */
	public boolean hasSpecficTarget(){return !(targetsToInclude.isEmpty()&&targetsToExclude.isEmpty());}
	
	/**
	 * Checks to see if one of the targets of this message is contained in names
	 * @param names a list of names to check
	 * @return true if and only if any member of names is a target of this Message
	 */
	public boolean isTarget(HashSet<String> names)
	{
		if(!hasSpecficTarget())return true;
		boolean isTarget=false;
		for(String name:names)
		{
			if(targetsToExclude.contains(name))
				return false;
			else if(targetsToInclude.contains(name))
				isTarget=true;
		}
		return isTarget;
	}
	
	/**
	 * @return the Message of this Message
	 * @see Message
	 */
	public Object getMessage(){return message;}
	/**
	 * 
	 * @return the name of the user who sent this message.
	 */
	public String getUserID(){return userID;}
	/**
	 * @return the type of this Message
	 * @see #type
	 */
	public byte getType(){return type;}
	
	public boolean hasHeader(){return header!=null;}
	/**
	 * 
	 * @return the header of this message
	 * @see #header
	 */
	public String getHeader(){return header;}
	
	
	/**
	 * @return true is and only if this Message's type is equal to 
	 * {@link #TYPE_DEFAULT}
	 */
	public boolean isDefault(){return type==TYPE_DEFAULT;}
	/**
	 * @return true is and only if this Message's type is equal to 
	 * {@link #TYPE_NOTIFICATION}
	 */
	public boolean isNotification(){return type==TYPE_NOTIFICATION;}
	/**
	 * @return true is and only if this Message's type is equal to 
	 * {@link #TYPE_MESSAGE_TO_CLIENT}
	 */
	public boolean isMessageToClient(){return type==TYPE_MESSAGE_TO_CLIENT;}
	/**
	 * 
	 * @return true is and only if this Message's type is equal to 
	 * {@link #TYPE_MESSAGE_TO_SERVER}
	 * 
	 */
	public boolean isMessageToServer(){return type==TYPE_MESSAGE_TO_SERVER;}
	/**
	 * 
	 * @return true is and only if this Message's type is equal to 
	 * {@link #TYPE_CONNECTION_INFO}
	 * 
	 */
	public boolean isMessageConnectionInfo(){return type==TYPE_CONNECTION_INFO;}
	
	@Override
	public String toString()
	{
		return
				"message:"+((message instanceof Object[])?Arrays.asList((Object[])message):message)+", "+
				"username:"+userID+","+
				"type:"+type+", "+
				"header:"+header;
	}
}