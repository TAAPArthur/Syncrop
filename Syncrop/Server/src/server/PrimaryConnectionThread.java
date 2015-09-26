package server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ConcurrentModificationException;
import java.util.HashMap;

import logger.Logger;
import message.Message;

public class PrimaryConnectionThread extends GenericConnectionThread{
	
	CheckThreadsThread checkThreadsThread=null;
		
	public PrimaryConnectionThread(Socket s,ObjectInputStream in,ObjectOutputStream out,String username,String application,int maxConnections,long milliSecondsPerPing) throws IOException {
		super(s,in,out);
		if(username==null||username.isEmpty())
			username=System.currentTimeMillis()+"";
		this.application=application;
		this.username=username;
		//id=username+"_"+application+System.currentTimeMillis();
		this.maxConnections=maxConnections;
		this.milliSecondsPerPing=milliSecondsPerPing;
		this.clientSocket=s;
		
		Server.connections.put(username, this);
		if(maxConnections<=0)
			clients=new HashMap<>();
		else
			clients=new HashMap<>(maxConnections);
		
		log("new primary. ID="+username+" milliSecondsPerPing="+milliSecondsPerPing);
	}
	@Override
	public synchronized void printMessage(Message m){
		if(m.hasHeader()&&m.getHeader().equals(Message.HEADER_CONNECTION_REQUEST)){
			connectionInfo.add(new Message(
					Message.MESSAGE_CONNECTION_ACCEPTED,Server.username,Message.TYPE_CONNECTION_INFO));
		}
		else if(m.getType()==Message.TYPE_NOTIFICATION)
			notifications.add(m);
		else input.add(m);
	}
	@Override
	public void run()
	{
		(checkThreadsThread=new CheckThreadsThread()).start();
		while (!isConnectionClosed())//relays info
		{
			try 
			{
				if(clients.size()==0)
				{
					if(input.size()!=0){
						log("Clearing input because there are no clients", Logger.LOG_LEVEL_WARN);
						input.clear();
					}
					Thread.sleep(1000);
					continue;
				}
				if(clientSocket!=null)
					if(isReady())//prints messages to clients
					{
						printNextMessageToClients();
					}				
				Message m=readClientMessages();
				if(m!=null)
					if(clientSocket==null)
						input.add(m);
					else printMessage(m);
				Thread.sleep(20);
			}
			catch (OutOfMemoryError e) {Server.log(e);terminateConnection("out of memory",true);}
			catch (Error|Exception e) {log(e);terminateConnection(e.toString(), true);}
			
		} 
	}
	protected void printMessageToClients(Message message){

		if(!message.hasSpecficTarget())
			for(String key:clients.keySet())
				clients.get(key).printMessage(message);
		else //only writes to targeted messages 
		{
			Message undirectedMessage=new Message(message);
			for(String key:clients.keySet())
				if(message.isTarget(clients.get(key).names))
				{
					clients.get(key).printMessage(undirectedMessage);
				}
		}
	}
	protected void printNextMessageToClients() throws IOException{
		//read
		Message message=readMessage();
		printMessageToClients(message);
	}
	
	public Message readClientMessages() throws IOException{
		for(String key:clients.keySet())
		{
			SecondaryConnectionThread client=clients.get(key);
			if(client==null)continue;
		    if(client.isReady()){
		    	Message m= client.readMessage();
		    	if(!m.getUsername().equals(key))
		    		terminateConnection("User:"+key+" is sending messages with "
		    				+ "different id-"+m.getUsername(), true);
		    	else {
		    		log("Primary read message:"+m, Logger.LOG_LEVEL_ALL);
		    		return m;
		    	}
		    }
		}
		
		return null;
	}
	
	boolean isConnectionClosed(){
		if(clientSocket==null)
			return !connectedToClient;
		else return !connectedToClient||clientSocket.isClosed();
	}
	
	@Override
	public void terminateConnection(String reason,boolean localClose){
		closeAllConnections(reason,localClose);
	}
	
	protected void closeAllConnections (String reason,boolean localClose)
	{
		if(!connectedToClient)return;
		
		for(String key:clients.keySet())
			clients.get(key).closeConnection("Error with primary: "+reason,true);
		
		closeConnection(reason,localClose);
		checkThreadsThread.interrupt();
		if(clientSocket!=null)
			input.clear();
		checkThreadsThread.interrupt();
		clients.clear();
		Server.connections.remove(username);
	}
	
	
	/**
	 * checks for and removes inactive threads every 20s
	 */
	
	class CheckThreadsThread extends Thread
	{
		public CheckThreadsThread() 
		{
			super("CheckThreads");
		}
		@Override
		public void run()
		{
			final Message ping=new Message(
					Message.MESSAGE_PING, username, Message.TYPE_MESSAGE_TO_CLIENT,Message.HEADER_IGNORE);
			while(isConnectedToClient()){
				try{
					for(String key:clients.keySet())
					{
						SecondaryConnectionThread c=clients.get(key);
						
						if(c.isActive())
						{
							clients.get(key).printMessage(ping);
							clients.get(key).active=false;
						}
						else 
							remove(key,"no ping",true);	
					}
					if(clientSocket!=null){
						if(isActive())
						{
							printMessage(ping);
							active=false;
						}
						else 
						{
							closeAllConnections("no ping from primary",true);
							return;
						}
					}
					Thread.sleep(milliSecondsPerPing);
				} 
				catch (InterruptedException e){log("Check threads threads has been interrupted");}
				catch (ConcurrentModificationException e) {log(e.toString());}
				catch (Exception|Error e) {Server.log(e);terminateConnection(e.toString(),false);}
			}
		}
	}
}