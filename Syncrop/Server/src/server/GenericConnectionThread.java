package server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import logger.Logger;
import message.Message;

public abstract class GenericConnectionThread extends Thread 
{

	private static boolean unshared=true;
	private static long waitTime=10;
	/**
	 * the clients; this will be modified to accommodate more users
	 */
	volatile HashMap<String,SecondaryConnectionThread>clients=null;
	/**
	 * the socket that will relay between the primary and the secondary
	 */
	Socket clientSocket;
	
	ReadMessagesThread readMessagesThread=new ReadMessagesThread();
	
	
	
	
	/**
	 * names of clients
	 */
	//volatile private ArrayList<String>namesOfClients=null;
	
	long milliSecondsPerPing;
	/**
	 * write to client
	 */
	ObjectOutputStream out;
	/**
	 * reads input from clients
	 */
	ObjectInputStream in;
	/**
	 * Information passed in
	 */
	//String id;
	String username=null,application;
	HashSet<String> names=new HashSet<String>();
	
	/**
	 * 
	 */
	volatile boolean active=true;
	/**
	 * used for reading
	 */
	volatile ArrayList<Message>input=new ArrayList<Message>(),
			notifications=new ArrayList<Message>(),
			connectionInfo=new ArrayList<Message>();
	/**
	 *the max # of connections allowed. 
	 */
	int maxConnections;
	
	
	volatile boolean connectedToClient=true;
	/**
	 * names thread
	 * @param s-clientSocket
	 * @throws IOException 
	 */
	GenericConnectionThread (Socket s,ObjectInputStream in,ObjectOutputStream out) throws IOException
	{
		super("Connection thread socket");
		clientSocket=s;
		if(s!=null){
			if(out==null){
				this.out = new ObjectOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));
				this.out.flush();
			}
			else this.out=out;
			
			this.in = in==null?
					new ObjectInputStream(new BufferedInputStream(clientSocket.getInputStream())):
					in;
			readMessagesThread.start();
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
	}
	
	
	@Override
	public String toString()
	{
		return username+" "+application;
	}
	
	/**
	 * 
	 * @param message the message to be logged
	 */
	public void log(String message,int logLevel)
	{
		Server.log(message,logLevel);
	}
	/**
	 * 
	 * @param message the message to be logged
	 */
	public void log(String message)
	{
		Server.log(message);
	}
	/**
	 * 
	 * @param e the exception whose stack trace will be logged
	 */
	public void log(Throwable e)
	{
		Server.log(e);
	}
	/**
	 * does everything
	 */
	
	/**
	 * 
	 * @return If the connection between the client and this thread is active
	 */
	boolean isActive()
	{
		return active;
	}
	/**
	 * Removes the client specified by key its primary Client
	 * @param key the username of the client
	 * @param reason the reason to remove the client
	 * @param localClose if the decision to close came locally from the Server of from a Client
	 * @return true if thee client was successfully removed
	 */
	
	protected boolean remove(String key,String reason,boolean localClose)
	{
		try {
			if(!connectedToClient||!clients.containsKey(key))return false;
			
			clients.get(key).closeConnection(reason,localClose);
			
			printMessage(new Message(clients.get(key).username,Server.username,Message.TYPE_NOTIFICATION,Message.HEADER_USER_LEFT));
			
			if(clients.remove(key)!=null)
			{
				if(clients.size()==0)
					printMessage(new Message(Message.MESSAGE_NO_CLIENTS,Server.username,Message.TYPE_NOTIFICATION));
				return true;
			}
			else log(key+" could not be removed", Logger.LOG_LEVEL_WARN);
		} catch (NullPointerException e) {
			log(e+" occured while trying to remove user "+key);
			log(e);
		}
		return false;
	}
	
	/**
	 * Terminates the connection. This method should be called if it is not known 
	 * whether the connection is a primary or not
	 * @param reason the reason to terminate
	 * @param localClose if the discussion to terminate was caused locally by the Server or by the Clien
	 */
	public abstract void terminateConnection(String reason,boolean localClose);
	
	
	
	
	/**
	 * Safely closes the connection between this thread and the client
	 * @param reason the reason to close the connection
	 * @param localClose if the decision to close will decided locally by the Server or by the Client
	 * @throws IOException
	 */
	protected void closeConnection(String reason,boolean localClose)
	{
		if(connectedToClient)
			try 
			{
				connectedToClient=false;
				if(localClose)
					printMessage(new Message(reason,"Server",Message.TYPE_MESSAGE_TO_CLIENT,Message.HEADER_CLOSE_CONNECTION));
				
				if (this instanceof PrimaryConnectionThread)				
					log(username+"(primary) closed connection: reason "+reason);
				else log(username+" is closing connection: reason "+reason);
				
				if(clientSocket!=null){
					clientSocket.shutdownOutput();
					clientSocket.shutdownInput();
					clientSocket.close();
				}				
			} catch (IOException e) {
				log(e);
			}
	}
	
	/**
	 * 
	 * @return If the connection to the Client is still open
	 */
	boolean isConnectedToClient(){return connectedToClient&&(clientSocket==null||!clientSocket.isClosed());}
	
	volatile int count=0;
	synchronized void printMessage(Message o)
	{
		if(isConnectedToClient())
			try {
				if(unshared)out.writeUnshared(o);
				else out.writeObject(o);
				
				out.flush();
				
				if(count++>10){
					out.reset();
					count=0;
				}
			} 
			catch (IOException e) 
			{
				log("Error cannot print because of broken pipe\n; Connection is probaly closed on the other end");
				if(isConnectedToClient())
					terminateConnection("Client closed connection (broken pipe)",false);
			}
	}
	
	Message readMessage() throws IOException
	{
		while(input.size()==0)
			try {
				if(!isConnectedToClient())
					throw new IOException("no data left to read");
				//outputResults("empty");
				Thread.sleep(waitTime);
			} catch (InterruptedException e) {
			}
		return input.remove(0);
	}
	Message readNotification()
	{
		while(notifications.size()==0)
			try {
				//outputResults("empty");
				Thread.sleep(waitTime);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
		return notifications.remove(0);
	}
	Message readConnectionInfo()
	{
		while(connectionInfo.size()==0)
			try {
				Thread.sleep(waitTime);
			} catch (InterruptedException e) {
				log(e.toString());
			}
		
		return connectionInfo.remove(0);
	}
	/**
	 * 
	 * @return true if there is a normal messages ready to be read
	 */
	boolean isReady(){return input.size()!=0;}

	
	
	class ReadMessagesThread extends Thread
	{

		public ReadMessagesThread()
		{
			super("READ");
		}
		@Override
		public void run()
		{
			while (isConnectedToClient())	
			{
				try 
				{
					
					Message m =(Message)(unshared?in.readUnshared():in.readObject());
					log("Read message:"+m.toString(), Logger.LOG_LEVEL_ALL);
					active=true;
					readMessage(m);
					count++;
					m=null;
				}
				catch(StreamCorruptedException e){
					if(username!=null);
						terminateConnection(e.toString(), false);
				}
				catch (EOFException e)
				{
					terminateConnection("No data left to read",false);
					break;
				}
				catch(ClassNotFoundException|ClassCastException e)
				{
					log(e);
					terminateConnection(e.toString(),true);
					break;
				}
				catch (IOException e)
				{
					terminateConnection(e.toString(),false);
					break;
				}
				
				catch (Exception|Error e)
				{
					Server.log(e);
					terminateConnection(e.toString(),true);
				}
				
			}
			if(isConnectedToClient())
				terminateConnection("error in reading",false);
		}
		void readMessage(Message message)
		{
			boolean primary=GenericConnectionThread.this instanceof PrimaryConnectionThread;
			if(message.isMessageToServer())
			{
				if(message.getHeader().equals(Message.HEADER_IGNORE));
				else if(message.getHeader().equals(Message.HEADER_CLOSE_CONNECTION))
					terminateConnection("User request:"+message.getMessage(),false);
				else if(message.getHeader().equals(Message.HEADER_REMOVE_USER)&&primary)
				{
					String[]param=(String[]) message.getMessage();
					remove(param[0], param[1],true);
				}
				else if(primary&&message.getHeader().equals(Message.HEADER_SET_GROUP))
				{
					try {
						String names[]=(String[]) message.getMessage();
						String key=(String) names[0];
						
						clients.get(key).names.clear();
						clients.get(key).names.addAll(Arrays.asList(names));
						
						log(key+" is a member of:"+names);
					} catch (ArrayIndexOutOfBoundsException e) {
						log(username+": "+e.toString());
					}
				}
			}
			else if(message.isMessageConnectionInfo())
			{
				connectionInfo.add(message);
			}
			else if(!primary||clients.size()>0)
			{
				if(message.isNotification())
					notifications.add(message);
				else input.add(message);
			}
		}
	}
}