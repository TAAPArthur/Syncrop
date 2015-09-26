package client;

import java.io.IOException;

import message.Message;

public class SecondaryClient extends GenericClient 
{
	public SecondaryClient(String username,String application)throws IOException
	{
		this(username,DEFAULT_HOST,DEFAULT_PORT, application);
	}
	public SecondaryClient(String username,String host, int port, String application) throws IOException
	{
		super(username,host, port);
		
		//tells the Server about this client
		printMessage(new Object[]{application,false},Message.TYPE_CONNECTION_INFO);
	}

	/**
	 * Connects to the first available Primary Client on the Server
	 * @throws IOException If an I/O error occurs during this request 
	 * @throws IndexOutOfBoundsException If no connects are available 
	 * @see {@link #getConnections()} 
	 * {@link #makeConnection(String)}
	 */
	public void autoConnect() throws IOException,IndexOutOfBoundsException
	{
		if(isConnectionAccepted())
			throw new AlreadyConnectedException("Cannot connect to Cloud because " +
					"already connected to Cloud");
		makeConnection(getConnections()[0]);
	}
	/**
	 * Gets all the available Primary Clients on Server that this Client can connect to<br/>
	 * A Primary Client if considered available if
	 * <ul> 
	 * <li>It is connected to Cloud
	 * <li>It has the same application name as this Client
	 * <li>A client with the same user name is not connected to it
	 * </ul> 
	 * @return an array of all the available Primary Clients on Server that this Client can connect to
	 * @throws IOException if an I/O error occurs while requesting the list of Primary Clients
	 */
	public String[] getConnections() throws IOException,NullPointerException
	{
		printMessage(Message.MESSAGE_GET_CONNECTIONS,Message.TYPE_CONNECTION_INFO);
		Message availableConnections=readConnectionInfo();
		return (String[])availableConnections.getMessage();			
	}
	/**
	 * Connects to primary by id
	 * @param connection A String containing the connection information needed to connect to the specified Primary Client 
	 * @return true If the connection was accepted by the Primay Client false otherwise
	 * @throws IOException If an I/O error occurs while trying to connect to the specified Primary Client
	 */
	public boolean makeConnection(String connection) throws IOException
	{
		if(isConnectionAccepted())
			throw new AlreadyConnectedException("Cannot connect to Cloud because already connected to Cloud");
		String id=connection;
		printMessage(id, Message.TYPE_CONNECTION_INFO, Message.HEADER_MAKE_CONNECTION);
		
		Message message=readConnectionInfo();
		if(message.getMessage().equals(Message.MESSAGE_CONNECTION_MADE))
		{
			connectionAccepted=true;
			return true;
		}
		else return false;
	}	
}