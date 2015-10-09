package client;

import java.awt.HeadlessException;
import java.io.IOException;

import javax.swing.JOptionPane;

import message.Message;

public class PrimaryClient extends GenericClient 
{
	/**
	 * Automatically accepts all connections 
	 */
	public static final int AUTOMATIC_YES=0;
	/**
	 * Displays a confirmation box and will only accepts a user if ok is clicked
	 */
	public static final int CONFERMATION_BOX=1;
	/**
	 * how many connects are allowed. Only applies to primary
	 */
	public static final int UNLIMITED_CONNECTIONS=-1;
	
	/**
	 * How the host wants to decide who joins; either AUTOMATIC_YES(0), CONFERMATION_BOX(1);
	 */
	int acceptUser;
	
	/**
	 * Creates a Client and connects it to Cloud; This Client will be the 
	 * acts as the "host" of the connection and can control how and when other users join  
	 * @param username  the username of the user 
	 * @param application the name of the application
	 * @param acceptProtocol How users will be accepted
	 * @param maxConnections the max number of connections allowed
	 * @throws IOException If the Client could not connect to Cloud
	 */
	public PrimaryClient(String username,String application,int acceptProtocol,int maxConnections)throws IOException
	{
		this(username,"localhost",DEFAULT_PORT, application, acceptProtocol, maxConnections, 7000L);
	}
	/**
	 * Creates a Client and connects it to Cloud; This Client will be the 
	 * acts as the "host" of the connection and can control how and when other users join  
	 * @param username  the username of the user 
	 * @param application the name of the application
	 * @param acceptProtocol How users will be accepted
	 * @param maxConnections the max number of connections allowed
	 * @throws IOException If the Client could not connect to Cloud
	 */
	public PrimaryClient(String username,String application,int acceptProtocol,int maxConnections,long milliSecondsPerPing)throws IOException
	{
		this(username,DEFAULT_HOST,DEFAULT_PORT, application, acceptProtocol, maxConnections, milliSecondsPerPing);
	}
	
	/**
	 * Creates a Client and connects it to Cloud; This Client will be the 
	 * acts as the "host" of the connection and can control how and when other users join  
	 * @param username  the username of the user 
	 * @param host the host name
	 * @param port the port number
	 * @param application the name of the application
	 * @param acceptProtocol How users will be accepted
	 * @param maxConnections the max number of connections allowed
	 * @param milliSecondsPerPing How often the Client should ping the client and how often the Server should ping the client;
	 * @throws IOException If the Client could not connect to Cloud
	 */
	public PrimaryClient(String username,String host, int port,String application,int type,int maxConnections,long milliSecondsPerPing)throws IOException
	{
		super(username,host, port);
	
		acceptUser=type;
		setMilliSecondsPerPing(milliSecondsPerPing);
		//initiliazes this client as the host
		printMessage(new Object[]{application,true,maxConnections,milliSecondsPerPing},Message.TYPE_CONNECTION_INFO);
		waitForConnectionThread.start();		
	}

	/**
	 * waits for user to try connect to game;
	 */	
	Thread waitForConnectionThread =new Thread("wait for connection"){
		
		public void run()
		{
			while(isConnectedToServer())
			{
				try {
					while(connectionInfo.size()==0&&isConnectedToServer())
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e1) {}
					Message message=readConnectionInfo();
					
					if(!isConnectedToServer())return;
					if(message.getHeader().equals(Message.HEADER_CONNECTION_REQUEST))
					{
							int c=(acceptUser==0)?0:
								JOptionPane.showConfirmDialog
								(null, "Connect to "+message.getUserID(),(String)message.getMessage(),JOptionPane.YES_NO_OPTION);
							if(c==0)
							{
								//new Message
								printMessage(Message.MESSAGE_CONNECTION_ACCEPTED,Message.TYPE_CONNECTION_INFO);
								connectionAccepted=true;
							}
							else printMessage(Message.MESSAGE_CONNECTION_REJECTED,Message.TYPE_CONNECTION_INFO);
					}
				} catch (HeadlessException|IOException e) {
					closeConnection(e.toString(), true);
				}
			}
		}
	};
}