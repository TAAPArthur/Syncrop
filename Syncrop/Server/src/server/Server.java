package server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

import logger.Logger;
import message.Message;


/**
 * 
 * @author taaparthur
 *
 */
public abstract class Server 
{
	private ServerSocket serverSocket = null;
	static HashMap<String, PrimaryConnectionThread> connections=new HashMap<String, PrimaryConnectionThread>(1);
	private int port;
	private int maxConnections=-1;
	public static final int UNLIMITED_CONNECTIONS=-1;
	static Logger logger;
	
	static String username="SERVER";
	
	/**
	 * Creates a Server with an unlimited number of connections
	 */
	public Server()
	{
		this(UNLIMITED_CONNECTIONS);
	}
	/**
	 * Creates a Server with an unlimited number of connections
	 */
	public Server(Logger logger)
	{
		this(UNLIMITED_CONNECTIONS,50001,logger);
	}
	/**
	 * 
	 * @param maxConnections -the max number of connection this Server can have at 
	 * one time 
	 */
	public Server(int maxConnections){this(maxConnections,50001,null);}
	
	public Server(int maxConnections, int port,Logger logger)
	{
		Server.logger=logger;
		this.maxConnections=maxConnections;
		this.port=port;
		
		start();
	}
	
	public void close() throws IOException{
		for(String name:connections.keySet())
			connections.get(name).closeAllConnections("Server is shutting down",true);
		log("Shutting down Server Socket");
		if(serverSocket!=null)
			serverSocket.close();
	}
	private void start()
	{
		log("Server on");
		
		listenToPort();
		waitForConnection();
	}
	/**
	 * continually waits for a connection;<br/>
	 * When it connects, it makes an instance of Connection and waits again
	 */
	private void waitForConnection()
	{
		
		new Thread("Wait for connections")
		{
			public void run()
			{
				logger.log("Now accepting connections");
				while(!serverSocket.isClosed()){
					try {
						while(maxConnections==UNLIMITED_CONNECTIONS||maxConnections>connections.size())
							acceptConnection(serverSocket.accept());
						Thread.sleep(1000);
					} 
					catch (IOException e) {log(e);}
					catch (Exception e) {log(e);}
				}
				logger.log("Finished accepting connections");
			}
		}.start();
	}
	void acceptConnection(Socket clientSocket) throws IOException, ClassNotFoundException{
		clientSocket.setSoTimeout(60*2*1000);
		ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));
		out.flush();
		ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(clientSocket.getInputStream()));
		logger.log("client connected");
		Message message=(Message) in.readObject();
	
		if(message==null)
			return;//closeConnection("Message was null while initializing connection; Connection aborted", false);
		
		Object o[]=(Object[])message.getMessage();
		String application=o[0]+"";
		boolean primary=(Boolean)o[1];
		if(primary)
			new PrimaryConnectionThread(clientSocket, in, out, message.getUserID(), application, (int)o[2]).start();
		else new SecondaryConnectionThread(clientSocket,in,out,message.getUserID(),application).start();
	
	}
	

	/**
	 * Initializes serverSocket and lets it listen to port 50001
	 */
	private void listenToPort()
	{
		while(serverSocket==null)
	        try {
	            serverSocket = new ServerSocket(port,100);//accept queue size == 100
	        } catch (IOException e) {
	        	log("Could not listen on port: "+port);
	            System.err.println("Could not listen on port: "+port+"; sleeping 10s");
	            try {
					Thread.sleep(10000);
				} catch (InterruptedException e1) {log(e1);}
	        }
	}
	
	/**
	 * Logs a message to the log file
	 * @param message -the message to log
	 */
	static public void log(String message,int logLevel)
	{
		if(logger!=null)
			logger.log(message,logLevel);
	}
	
	/**
	 * Logs a message to the log file
	 * @param message -the message to log
	 */
	static public void log(String message)
	{
		if(logger!=null)
			logger.log(message);
	}
	/**
	 * logs an exception to the log file
	 * 
	 * @param e - the exception to log
	 */
	static public void log(Throwable e)
	{
		if(logger!=null)
			logger.logError(e);
	}
}