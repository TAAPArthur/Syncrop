package server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ConcurrentModificationException;
import java.util.HashMap;

import logger.Logger;
import message.Message;
import message.TimeoutCalculator;

public class PrimaryConnectionThread extends GenericConnectionThread{
	
	CheckThreadsThread checkThreadsThread=null;
	private boolean internal;
	public PrimaryConnectionThread(Socket s,ObjectInputStream in,ObjectOutputStream out,String username,String application,int maxConnections) throws IOException {
		this(s, in, out, username, application, maxConnections, false);
	}
	public PrimaryConnectionThread(Socket s,ObjectInputStream in,ObjectOutputStream out,String username,String application,int maxConnections,boolean internal) throws IOException {
		super(s,in,out);
		if(username==null||username.isEmpty())
			username=System.currentTimeMillis()+"";
		this.application=application;
		this.username=username;
		//id=username+"_"+application+System.currentTimeMillis();
		this.maxConnections=maxConnections;
		this.internal=internal;
		this.clientSocket=s;
		
		Server.connections.put(username, this);
		if(maxConnections<=0)
			clients=new HashMap<>();
		else
			clients=new HashMap<>(maxConnections);
		
		log("new primary. ID="+username);
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
		(checkThreadsThread=new CheckThreadsThread(!internal)).start();
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
			//System.out.println(clients.keySet());
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
		    	if(!key.equals(m.getUserID()))
		    		client.closeConnection("invalid id", true);
		    	else
			    	if(!m.getUserID().equals(key))
			    		terminateConnection("User:"+key+" is sending messages with "
			    				+ "different id-"+m.getUserID(), true);
			    	else {
			    		log("Primary read message:"+m, Logger.LOG_LEVEL_TRACE);
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
		boolean checkPrimary;
		public CheckThreadsThread(boolean checkPrimary) 
		{
			super("CheckThreads");
			this.checkPrimary=checkPrimary;
		}
		@Override
		public void run()
		{
			while(isConnectedToClient()){
				try{
					long currentTime=System.currentTimeMillis();
					
					for(SecondaryConnectionThread client:clients.values()){
						if(currentTime-client.timeoutCalculator.getTimeOfLastUpdate()<=TimeoutCalculator.MAX_PING_DELAY+client.getExpectedRoundTripTime()){
							log("pinging",Logger.LOG_LEVEL_TRACE);
							//client.printMessage(new Message(System.currentTimeMillis(), Server.username, Message.TYPE_MESSAGE_TO_CLIENT,Message.HEADER_PING));
						}
						else client.closeConnection("Timeout ("+client.getTimeout()+"ms <"+(currentTime-client.timeoutCalculator.getTimeOfLastUpdate())+")", true);
					}
					if(checkPrimary&&clientSocket!=null)
						if(currentTime-timeoutCalculator.getTimeOfLastUpdate()<=getTimeout())
							;//printMessage(new Message(System.currentTimeMillis(), Server.username, Message.TYPE_MESSAGE_TO_CLIENT,Message.HEADER_PING));
						else terminateConnection("Timeout (primary) ("+getTimeout()+"ms)", true);
					Thread.sleep(5*60*1000);//terminating an idle connection is not high priority
				} 
				catch (InterruptedException e){log("Check threads threads has been interrupted");}
				catch (ConcurrentModificationException e) {log(e.toString());}
				catch (Exception|Error e) {Server.log(e);terminateConnection(e.toString(),false);}
			}
		}
	}
}