package server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;

import logger.Logger;
import message.Message;

public class SecondaryConnectionThread extends GenericConnectionThread{

	private PrimaryConnectionThread primaryClient;
	public SecondaryConnectionThread(Socket s,ObjectInputStream in,ObjectOutputStream out, String username, String application) throws IOException {
		super(s,in,out);
		this.username=username;
		this.application=application;
		//id=username+"_"+application+System.currentTimeMillis();
	}
	
	public void run(){
		try {
			setup();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	@Override
	public void terminateConnection(String reason,boolean localClose){
		if(primaryClient!=null)
			if(!primaryClient.remove(username, reason,localClose))
				closeConnection(reason,localClose);
	}
	
	/**
	 * Communicates with a SecondaryClient to setup a connection with a primary Client
	 * @throws IOException if an I/O error occurs
	 */
	private void setup() throws IOException
	{
		names.add(username);
		//names.add(id);
		while(isConnectedToClient())
		{
			Message m=readConnectionInfo();
			if(m==null)
			{
				closeConnection("Connection lost; Message=null", false);
				break;
			}
			if(m.getMessage().equals(Message.MESSAGE_GET_CONNECTIONS))
			{
				ArrayList<String>availableConnections=new ArrayList<String>();
				for(String key:Server.connections.keySet())
				{
					PrimaryConnectionThread c=Server.connections.get(key);
					
					if(c!=null&&c.application.equals(application)&&c.clients.size()!=c.maxConnections&&
							!c.clients.containsKey(username))
						availableConnections.add(c.username);
					
					//else Server.connections.remove(key);
				}
				log("Available connections for "+username+": "+availableConnections, Logger.LOG_LEVEL_ALL);
				printMessage(new Message(
						availableConnections.toArray(new String[availableConnections.size()]),
						Server.username,Message.TYPE_CONNECTION_INFO));
			}
			else if(m.getHeader().equals(Message.HEADER_MAKE_CONNECTION))//Secondary connects to primary
			{
				PrimaryConnectionThread c=Server.connections.get(m.getMessage()+"");//makes connection by id
				//ask primary to connect to secondary
				c.printMessage(new Message("Connect to "+username+"?",username,Message.TYPE_CONNECTION_INFO,Message.HEADER_CONNECTION_REQUEST));
				log("trying to connect: "+username+" to "+c.username );
				
				Message connectionAnswer=c.readConnectionInfo();
				if(connectionAnswer.getMessage().equals(Message.MESSAGE_CONNECTION_ACCEPTED))
				{
					printMessage(new Message(Message.MESSAGE_CONNECTION_MADE,Server.username,Message.TYPE_CONNECTION_INFO));
					c.clients.put(username,this);
					primaryClient=c;
					c.printMessage(new Message(username,Server.username,Message.TYPE_NOTIFICATION,Message.HEADER_NEW_USER));
					
					log("Connection made. "+username+" to "+c.username+"("+c.username+")" );
					return;
				}
				else printMessage(new Message(Message.MESSAGE_CONNECTION_REJECTED,Server.username));				
			}
		}
	}

}
