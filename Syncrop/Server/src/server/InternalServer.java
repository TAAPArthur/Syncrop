package server;

import java.io.IOException;

import logger.Logger;
import message.Message;
import message.Messenger;

public class InternalServer extends Server implements Messenger {
	final PrimaryConnectionThread primary;
	
	/**
	 * The reason the server closed  
	 */
	private String reasonToClose;
	
	public InternalServer(int maxConnections, int port,Logger logger,String username,String application,long milliSecondsPerPing) throws IOException{
		super(maxConnections, port, logger);
		Server.username=username;
		primary=new PrimaryConnectionThread(null,null,null,username,application, maxConnections, milliSecondsPerPing);
		primary.start();
	}
	
	public Message readMessage() throws IOException{
		return primary.readMessage();
	}
	
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
	public void printMessage(Message m){
		primary.printMessageToClients(m);
	}

	@Override
	public boolean isConnectedToServer() {
		return !primary.isConnectionClosed();
	}

	@Override
	public boolean isConnectionAccepted() {
		return primary.clients.size()>0;
	}

	@Override
	public void closeConnection(String reason, boolean localClose) {
		primary.terminateConnection(reason, localClose);
		reasonToClose=reason;
		try {
			close();
		} catch (IOException e) {
			log(e);
		}
		
	}

	@Override
	public String getReasonToClose() {
		return reasonToClose;
	}

	@Override
	public Message readNotification() {
		return primary.readNotification();
	}	
}