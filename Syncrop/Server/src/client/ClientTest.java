package client;

import java.io.IOException;
import java.util.Scanner;

import logger.GenericLogger;
import message.Messenger;
import server.ExternalServer;
import server.InternalServer;
import server.Server;

public class ClientTest 
{
	static String application="Test";
	public static Messenger getPrimary(boolean internal,String host,int port) throws IOException{
		String username="Testuser1";
		long milliSecondsPerPing=7000;
		if(internal){
			return new InternalServer(Server.UNLIMITED_CONNECTIONS, port, new GenericLogger(System.out),
					username,application, milliSecondsPerPing);
		}
		else {
			new ExternalServer(Server.UNLIMITED_CONNECTIONS,port,new GenericLogger(System.out));

			try {Thread.sleep(1000);} catch (InterruptedException e2) {e2.printStackTrace();}
			System.out.println("Server started");
			//creates the Primary Client
			return new PrimaryClient(username,host,port,application,PrimaryClient.AUTOMATIC_YES,3, milliSecondsPerPing);

		}
	}
	public static void main(String args[]) throws IOException
	{
		final int port=50022;
		String host="localhost";
		GenericClient.setLogger(new GenericLogger(System.out));

		//creates the Primary Client
		final Messenger primary=getPrimary(true, host, port);
		try {Thread.sleep(100);} catch (InterruptedException e2) {e2.printStackTrace();}
		//creates 2 Secondary Clients
		final SecondaryClient secondary=new SecondaryClient("Testuser2",host,port,application);
		try {Thread.sleep(100);} catch (InterruptedException e2) {e2.printStackTrace();}
		final SecondaryClient third=new SecondaryClient("Testuser3",host,port,application);
		//waits to make sure initialization is finished
		try {Thread.sleep(100);} catch (InterruptedException e2) {e2.printStackTrace();}
		if(!primary.isConnectedToServer()||!secondary.isConnectedToServer()||!third.isConnectedToServer())
		{
			System.out.println("Not all Clients could connect to Server");
			System.out.println(primary.isConnectedToServer());
			System.out.println(secondary.isConnectedToServer());
			System.out.println(third.isConnectedToServer());
			System.exit(0);
		}

		System.out.println("Starting");
		try {
			secondary.autoConnect();
			third.autoConnect();
		} catch (IndexOutOfBoundsException|IOException e1) {
			e1.printStackTrace();
		} 
		System.out.println("Starting");
		
		new Thread("Second")
		{
			public void run()
			{
				while(secondary.connectionAccepted&&secondary.connectedToServer)
					try {
						System.out.println("2"+secondary.readMessage());
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
			}
		}.start();
		new Thread("Third")
		{
			public void run()
			{
				while(third.connectionAccepted&&third.connectedToServer)
					try {
						System.out.println("3"+third.readMessage());
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
			}
		}.start();
		System.out.println("while");
		Scanner sc=new Scanner(System.in);
		Loop: while(true)
		{
			String line=sc.nextLine();
			
			primary.printMessage(line);
			try {Thread.sleep(10);} catch (InterruptedException e) {}
			switch(line){
				case "break":
					primary.closeConnection("",true);
					break Loop;
				case "break2":
					secondary.closeConnection("",true);
					break;
				case "break3":
					third.closeConnection("",true);
					break;
				case "stopPing":
					((GenericClient) primary).setMilliSecondsPerPing(100000000);
					break;
				case "stopPing2":
					secondary.setMilliSecondsPerPing(100000000);
					break;
				case "stopPing3":
					third.setMilliSecondsPerPing(100000000);
					break;
			}
		}
		System.out.println("exiting");
		sc.close();
	}
}
