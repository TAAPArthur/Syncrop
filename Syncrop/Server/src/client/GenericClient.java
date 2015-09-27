package client;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;

import logger.Logger;
import message.Message;
import message.Messenger;

/**
 * class used to communicate with Server
 * 
 */
public class GenericClient implements Messenger
{	
	/**
	 * the host name, or null for the loopback address.
	 */
	public static final String DEFAULT_HOST="taaparthur.no-ip.org";
	/**
	 * The port to connect to
	 */
	public static final int DEFAULT_PORT=50001;
	
	/**
	 * The reason the client closed  
	 */
	private String reasonToClose;
	/**
	 * How often the Client should ping the client and how often the Server should ping the client;
	 * If the ping is not recieved within the time interval, the connection will be closed 
	 */
	private volatile long milliSecondsPerPing=120000;
	
	
	private static long waitTime=10;
	
	/**
	 * How many messages have been sent or received<br/>
	 * When count is greater than maxCout, {@link #out} is reset
	 * @see #maxCount
	 */
	volatile int count=0;
	
	/**
	 * The max number of messages sent before {@link #out} is reset
	 * @see #count
	 */
	int maxCount=10;
	
	/**
	 * used to print to server
	 */
	private ObjectOutputStream out = null;
	/**
	 * used to recieve data from server
	 */
	private ObjectInputStream in = null;
	/**
	 * the connection socket
	 */
	private Socket socket=null;
	/**
	 * The name of this Messenger. All Messages sent from this Messenger will have this username 
	 */
	private final String username;
	
	/**
	 * connection status
	 */
	volatile boolean connectionAccepted=false, connectedToServer=false;

	
	/**
	 * contains main messages
	 */
	volatile ArrayList<Message>main=new ArrayList<Message>();
	/**
	 * contains notification messages
	 */
	volatile ArrayList<Message>notifications=new ArrayList<Message>();
	/**
	 * contains messages retaining to connection info
	 */
	volatile ArrayList<Message>connectionInfo=new ArrayList<Message>();
	
	/**
	 * The last recored time of the last sent message
	 */
	volatile long timeLastMessageWasSent;
	/**
	 * The last time a ping was received
	 * @see GenericClient#timeLastMessageWasSent 
	 */
	volatile long timeLastPingRecieved;
	
	private static Logger logger;
	
	private static volatile boolean logPrintedMessages;
	private static volatile boolean loggingMessages=true;
		
	ReadMessageThread readMessageThread=new ReadMessageThread();
	Thread pingThread=new Thread("ping thread"){
		public void run()
		{
			do
			{
				try{
					Thread.sleep(milliSecondsPerPing/2);
					
					if(isConnectionAccepted()&&System.currentTimeMillis()-timeLastPingRecieved>milliSecondsPerPing*2)
						closeConnection("No Ping from Server");
					
					if(System.currentTimeMillis()-timeLastMessageWasSent<milliSecondsPerPing/4)
						continue;
					
					if(isConnectedToServer())
						ping();
					
				} catch (InterruptedException e) {}
			}while(isConnectedToServer());
		}
	};
	
	void ping(){
		printMessage(Message.MESSAGE_PING,Message.TYPE_MESSAGE_TO_SERVER,Message.HEADER_IGNORE);
	}
	
	public GenericClient(String username, String host, int port) throws IOException{
		this.username=username;
		connect(host,port);//TODO javadoc
		pingThread.start();
		readMessageThread.start();
		
	}
	
	
	public String getUsername()
	{
		return username;
	}
	
	
	
	
	
	/**
	 * Waits until the specified ArrayList has a size greater than 0<br/>
	 * This method is used to block the thread until a message is received;
	 * @param arrayList the ArrayList to wait for
	 */
	private void wait(ArrayList<Message> arrayList)
	{
		while(arrayList.size()==0&&isConnectedToServer())
			try {
				Thread.sleep(waitTime);
			} catch (InterruptedException e) {}
	}
	/**
	 * Reads the oldest message from the ArrayList containing normal messages or notifications.
	 * This method blocks if the list is empty.
	 * @param list The list to read from
	 * @return the next message in the list
	 * @throws IOException 
	 */
	private Message read(ArrayList<Message>list) throws IOException
	{
		if(list.size()==0)
		{
			wait(list);
			if(!isConnectedToServer())
				throw new IOException("no data left to read");
		}
		Message message=list.remove(0);
		return message;
	}
	
	/**
	 * Reads the oldest notification message.
	 * This method blocks if the list is empty.
	 * @return the next notification message in the list
	 * @throws IOException 
	 */
	public Message readNotification() throws IOException{return read(notifications);}
	
	/**
	 * Reads the oldest regular message.
	 * This method blocks if the list is empty.
	 * @return the next reular message in the list
	 * @throws IOException 
	 */
	public Message readMessage() throws IOException{return read(main);}
	/**
	 * Reads the oldest connection info message.
	 * This method blocks if the list is empty.
	 * @return the next connection info message in the list
	 * @throws IOException 
	 */
	protected Message readConnectionInfo() throws IOException{return read(connectionInfo);}
	
	public void printNotification(Object o){printMessage(o, Message.TYPE_NOTIFICATION);}
	
	public void printMessage(Object o){printMessage(o, Message.TYPE_DEFAULT);}
	public void printMessage(Object o,int type)
	{
		printMessage(new Message(o,username,type));
	}
	public void printMessage(Object o,String header)
	{
		printMessage(new Message(o,username,Message.TYPE_DEFAULT,header));
	}
	public void printMessage(Object o,int type,String header)
	{
		printMessage(new Message(o,username,type,header));
	}
	public void printMessage(Object o,String[]target)
	{
		printMessage(new Message(o,username, Message.TYPE_DEFAULT,null,target));
	}
	public void printMessage(Object o,String header,String... target)
	{
		printMessage(new Message(o,username, Message.TYPE_DEFAULT,header,target));
	}
	public void printMessage(Object o,String header,String[]targetsToInclude,String... targetsToExclude)
	{
		printMessage(new Message(o,username, Message.TYPE_DEFAULT,header,targetsToInclude,targetsToExclude));
	}
	
	public void printMessage(Object o,int type,String header,String... targetsToInclude)
	{
		printMessage(new Message(o,username,type,header,targetsToInclude));
	}
	public void printMessage(Object o,int type,String header,String[] targetsToInclude,String... targetsToExclude)
	{
		printMessage(new Message(o,username,type,header,targetsToInclude,targetsToExclude));
	}
	
	public synchronized void printMessage(Message messageToPrint)
	{
		try 
		{
			//out.writeUnshared(messageToPrint);
			out.writeObject(messageToPrint);
			out.flush();
			if(logPrintedMessages&&!messageToPrint.getMessage().equals(Message.MESSAGE_PING))
				log(messageToPrint.toString());
			
			count++;
			//maxCount=10;
			if(count>maxCount)
			{
				out.reset();
				count=0;
			}
			timeLastMessageWasSent=System.currentTimeMillis();
		}
		catch (SocketException e)
		{
			closeConnection(e.toString());
		}
		catch (Exception|Error e) 
		{
			log(e);
			closeConnection(e.toString());
		}
	
	}
	
	
	/**
	 * @return true if there is regular message ready to be read
	 */
	public boolean isReady(){return main.size()!=0;}
	/**
	 *  @return true if there is regular message ready to be read
	 */
	public boolean isNotificationReady()
	{
		return notifications.size()!=0;
	}
	
	public boolean isConnectionAccepted()
	{
		return connectionAccepted;
	}
	public boolean isConnectedToServer()
	{
		return connectedToServer&&!socket.isClosed();
	}
	/**
	 * Creates a stream socket and connects it to the specified port number on 
	 * the named host and creates an ObjectOutput and InputStreams for the socket
	 * @param host the host name
	 * @param port the port number
	 */
	void connect(String host,int port) throws IOException
	{	
		socket = new Socket(host, port);
		
    	out = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    	out.flush();
    	in = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
    	connectedToServer=true;
	}
	
	/**
	 * Closes the connection due to an external factor
	 * @param reason the reason for the close
	 * @see #closeConnection(String, boolean)
	 */
	private void closeConnection(String reason)
	{
		closeConnection(reason, false);
	}
	public void closeConnection(String reason,boolean localClose) 
	{
		if(!connectedToServer)return;
		if(localClose)
			printMessage(reason, Message.TYPE_MESSAGE_TO_SERVER,Message.HEADER_CLOSE_CONNECTION);
		
		reasonToClose=reason;
				
		main.clear();
		notifications.clear();
		
		connectedToServer=false;
		connectionAccepted=false;
		
		pingThread.interrupt();
		if(socket.isClosed())return;
		try 
		{
			closeSocket();
		}
		catch (IOException e) {
			log(e);
		}  
	 }
	private void closeSocket() throws IOException{
		socket.shutdownOutput();
		socket.shutdownInput();
		socket.close();
	}
	public String getReasonToClose() {
		return reasonToClose;
	}
	
	/**
	 * Sets the PrintWriter to all errors will be written to
	 * @param o the PrintWriter to log errors to
	 */
	public static void setLogger(Logger logger)
	{
		GenericClient.logger=logger;
	}
	public void log(String message)
	{
		if(logger!=null&&loggingMessages)
			logger.log(message);
		
	}
	public void log(Throwable e)
	{
		if(logger!=null&&loggingMessages){
			logger.logError(e);
		}
		else e.printStackTrace();
	}
	
	
	public static boolean isLoggingPrintedMessages() {
		return logPrintedMessages;
	}
	public static void setLoggingPrintedMessages(boolean logPrintedMessages) {
		GenericClient.logPrintedMessages = logPrintedMessages;
	}

	public static boolean isLoggingMessages() {
		return loggingMessages;
	}
	public static void setLoggingMessages(boolean loggingMessages) {
		GenericClient.loggingMessages = loggingMessages;
	}

	
	void setMilliSecondsPerPing(long milliSecondsPerPing){
		this.milliSecondsPerPing=milliSecondsPerPing;
		pingThread.interrupt();
	}
	/**
	 * opens a new thread and contiunally reads input and saves it into main or notification
	 */
	
	class ReadMessageThread extends Thread
	{

		public ReadMessageThread() 
		{
			super("Read Message Thread");
		}
		public void run()
		{
			while (connectedToServer)	
			{
				Message message = null;
				try 
				{	
					//message=(Message)in.readUnshared();
					message=(Message)in.readObject();	
					if(message.isMessageToClient())
					{
						if(message.getHeader().equals(Message.HEADER_CLOSE_CONNECTION))
							closeConnection("Closed by server:"+message.getMessage());
						else if(message.getMessage().equals(Message.MESSAGE_PING));
						else if(message.getHeader().equals(Message.HEADER_SET_MILLISECONDS_TILL_PING)){
							setMilliSecondsPerPing((long)message.getMessage());
						}
						else log(message.toString());
					}
					else if(message.isNotification())
					{
						if(message.getMessage().equals(Message.MESSAGE_NO_CLIENTS))
							connectionAccepted=false;
						notifications.add(message);
					}
					else if(message.isDefault())main.add(message);
					else connectionInfo.add(message);
					timeLastPingRecieved=System.currentTimeMillis();
				}
				catch (EOFException e)
				{	
					closeConnection(e.toString()+"; no data left to read");
				}
				catch (SocketException e)
				{
					closeConnection(e.toString()+"; Cloud has closed connection");
				}
				catch (IOException|ClassNotFoundException|ClassCastException e)
				{
					
					log(e);closeConnection(e.toString());
				}
				catch (Exception|Error e)
				{
					if(message!=null)
						log(message+"");
					log(e);
					closeConnection(e.toString());
				}
			}
		}
	}
}