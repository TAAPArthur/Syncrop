package client;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import message.Message;
import message.Messenger;
import message.TimeoutCalculator;

/**
 * class used to communicate with Server
 * 
 */
public class GenericClient implements Messenger{
	TimeoutCalculator timeoutCalculator=new TimeoutCalculator();
	private static boolean unshared=true;
	String application;
	/**
	 * the host name, or null for the loopback address.
	 */
	public static final String DEFAULT_HOST="https://taaparthur.no-ip.org";
	/**
	 * The port to connect to
	 */
	public static final int DEFAULT_PORT=50001;
	
	/**
	 * The reason the client closed  
	 */
	private String reasonToClose;
	
	/**
	 * the time internal in miliseconds that read methods will check to see if there is 
	 * a message to forward
	 */
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
	 * The name of this Messenger. All Messages sent from this Messenger will have this 
	 * userID 
	 */
	private final String USER_ID;
	
	
	/**
	 * connection status
	 */
	volatile boolean connectionAccepted=false, connectedToServer=false;

	
	/**
	 * contains main messages
	 */
	volatile LinkedList<Message>main=new LinkedList<Message>();
	/**
	 * contains notification messages
	 */
	volatile List<Message>notifications=new LinkedList<Message>();
	/**
	 * contains messages retaining to connection info
	 */
	volatile List<Message>connectionInfo=new LinkedList<Message>();
	
	/**
	 * The last recored time of the last sent message
	 */
	volatile long timeLastMessageWasSent;
	
		
	ReadMessageThread readMessageThread=new ReadMessageThread();
	/**
	 * Sleeps half of {@link #milliSecondsPerPing} and checks to see if a message has been
	 * sent to Cloud. If not, a ping is sent.
	 */
	Thread pingThread=new Thread("Ping thread"){
		public void run(){
			do{
				try{
					Thread.sleep(timeoutCalculator.getPingDelay());
					if(isConnectedToServer())
						printMessage(System.currentTimeMillis(),Message.TYPE_MESSAGE_TO_SERVER,Message.HEADER_PING);
					
				} catch (InterruptedException e) {}
			}while(isConnectedToServer());
		}
	};
	
	public int getTimeout(){return timeoutCalculator.getTimeout();}
	public int getExpectedRoundTripTime(){return timeoutCalculator.getExpectedMaxRoundTripTime();}
	
	String host; 
	int port;
	boolean ssl;
	/**
	 * 
	 * @param username the unique name of the user making the connection
	 * @param host the hostname to connect to
	 * @param port the port to connect to
	 * @throws IOException
	 */
	protected GenericClient(String userID, String host, int port,String application,boolean ssl) throws IOException{
		this.USER_ID=userID;
		this.host=host;
		this.port=port;
		this.ssl=ssl;
		this.application=application;
		
	}
	
	
	protected void startThreads(){
		pingThread.start();
		readMessageThread.start();
	}
	
	
	
	/**
	 * Waits until the specified ArrayList has a size greater than 0<br/>
	 * This method is used to block the thread until a message is received;
	 * @param arrayList the ArrayList to wait for
	 */
	private void wait(List<Message> list)
	{
		while(list.isEmpty()&&isConnectedToServer())
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
	private Message read(List<Message>list) throws IOException
	{
		if(list.isEmpty())
		{
			wait(list);
			if(!isConnectedToServer())
				throw new IOException("No data can be read because connection closed");
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
	
	/**
	 * Prints a message with {@link Message#TYPE_NOTIFICATION}
	 * @param o the object to be sent 
	 */
	public void printNotification(Object o){printMessage(o, Message.TYPE_NOTIFICATION);}
	
	
	public void printMessage(Object o){printMessage(o, Message.TYPE_DEFAULT);}
	public void printMessage(Object o,int type)
	{
		printMessage(new Message(o,USER_ID,type));
	}
	public void printMessage(Object o,String header)
	{
		printMessage(new Message(o,USER_ID,Message.TYPE_DEFAULT,header));
	}
	public void printMessage(Object o,int type,String header)
	{
		printMessage(new Message(o,USER_ID,type,header));
	}
	public void printMessage(Object o,String[]target)
	{
		printMessage(new Message(o,USER_ID, Message.TYPE_DEFAULT,null,target));
	}
	public void printMessage(Object o,String header,String... target)
	{
		printMessage(new Message(o,USER_ID, Message.TYPE_DEFAULT,header,target));
	}
	public void printMessage(Object o,String header,String[]targetsToInclude,String... targetsToExclude)
	{
		printMessage(new Message(o,USER_ID, Message.TYPE_DEFAULT,header,targetsToInclude,targetsToExclude));
	}
	
	public void printMessage(Object o,int type,String header,String... targetsToInclude)
	{
		printMessage(new Message(o,USER_ID,type,header,targetsToInclude));
	}
	public void printMessage(Object o,int type,String header,String[] targetsToInclude,String... targetsToExclude)
	{
		printMessage(new Message(o,USER_ID,type,header,targetsToInclude,targetsToExclude));
	}
	
	/**
	 * Prints a message to the server
	 */
	public synchronized void printMessage(Message messageToPrint){
		try {
			if(unshared)out.writeUnshared(messageToPrint);
			else out.writeObject(messageToPrint);
			out.flush();
			count++;
			if(count>maxCount){
				out.reset();
				count=0;
			}
			timeLastMessageWasSent=System.currentTimeMillis();
		}
		catch (SocketException e){
			closeConnection(e.toString());
		}
		catch (Exception|Error e){
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
	protected void connectToServer() throws IOException
	{	
		System.out.println("connecting");
		socket = ssl?
				SSLSocketFactory.getDefault().createSocket(host, port):
				new Socket(host, port);
				System.out.println("handshake");
		if(ssl)
			((SSLSocket) socket).startHandshake();

		System.out.println("connected");
		
		socket.setSoTimeout(60*4*1000);
		
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
	
	/**
	 * Closes the connection due to an external factor
	 * @param reason the reason for the close
	 * @param localClose if the signial to close the connection was sent from the server or
	 * internally
	 * 
	 */
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
		System.out.println("Closing connection");
		if(socket.isClosed())return;
		try 
		{
			closeSocket();
		}
		catch (IOException e) {}  
	 }
	/**
	 * Closes the socket used by this Client. Once the socket has been closed,
	 * it cannot be reopened; an new socket needs to be created.
	 * @throws IOException
	 */
	private void closeSocket() throws IOException{
		in.close();
		out.close();
		socket.close();
	}
	
	/**
	 * @return The reason the socket closed or null if no reason was specified
	 */
	public String getReasonToClose() {
		return reasonToClose;
	}
	
	/**
	 * opens a new thread and continually reads input and saves it into main or notification
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
					
					message=(Message)(unshared?in.readUnshared():in.readObject());	
					if(message.isMessageToClient()){
						if(message.getHeader().equals(Message.HEADER_CLOSE_CONNECTION))
							closeConnection("Closed by server:"+message.getMessage());
						else if(message.getHeader().equals(Message.HEADER_PING))	
							printMessage(message.getMessage(),Message.TYPE_MESSAGE_TO_SERVER,Message.HEADER_PONG);
						else if(message.getHeader().equals(Message.HEADER_PONG))
							timeoutCalculator.calculateTimeout((int)(System.currentTimeMillis()-(long)message.getMessage()));
					}
					else if(message.isNotification())
					{
						if(message.getMessage().equals(Message.MESSAGE_NO_CLIENTS))
							connectionAccepted=false;
						notifications.add(message);
					}
					else if(message.isMessageConnectionInfo())
						connectionInfo.add(message);
					else {
						main.add(message);
						if(Math.random()>.5)
							Thread.sleep(waitTime);
					}
				}
				catch (EOFException e)
				{	
					closeConnection(e.toString());
				}
				catch (SocketException e)
				{
					closeConnection(e.toString()+"; Cloud has closed connection");
				}
				catch (IOException|ClassNotFoundException|ClassCastException e)
				{
					closeConnection(e.toString());
				}
				catch (Exception|Error e)
				{
					closeConnection(e.toString());
				}
			}
		}
	}
}