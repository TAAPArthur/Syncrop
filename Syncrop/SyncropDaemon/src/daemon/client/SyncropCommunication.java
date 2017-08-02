package daemon.client;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.NoSuchElementException;
import java.util.Scanner;

import daemon.SyncDaemon;

import settings.Settings;
import syncrop.ResourceManager;
import syncrop.Syncrop;


/**
 * 
 * Handles connection between the syncrop script on Windows and the Syncrop daemon
 *
 */
public class SyncropCommunication extends Thread 
{

	public static final int STATUS=0;
	public static final int SHUTDOWN=1;
	public static final int CLEAN=2;
	public static final int GET_ACCOUNT_SIZE=3;
	
	public static final String STATE_OFFLINE="Offline";
	public static final String STATE_INITIALIZING="Initializing";
	public static final String STATE_DISCONNECTED="Disconnected";
	public static final String STATE_CONNECTED="Connected";
	public static final String STATE_SYNCED="Synced";
	public static final String STATE_SYNCING="Syncing";
	public static final String STATE_IDLE="Idle";
	
	
	public ServerSocket serverSocket;
	SyncDaemon daemon;
	Scanner sc=null;
	Socket socket=null;
	PrintWriter out;
	public SyncropCommunication(SyncDaemon daemon)
	{
		super("Syncrop communication thread");
		this.daemon=daemon;
	}
	public void run()
	{
		Syncrop.logger.log("Starting communication thread");
		createSocket();
		while(!SyncropClientDaemon.isShuttingDown())
		{
			try {
				if(serverSocket.isClosed())
					createSocket();
				socket=serverSocket.accept();
				out=new PrintWriter(socket.getOutputStream());
				sc=new Scanner(new BufferedInputStream(socket.getInputStream()));
				
				while(!socket.isClosed()){
					int statusCode=sc.nextInt();
					switch (statusCode)
					{
						case SHUTDOWN:
							socket.close();
							serverSocket.close();
							Syncrop.logger.log("Recieved signial to shutdown");
							System.exit(0);
							break;
						case STATUS:
							if(daemon.isInitializing())
								out.println(STATE_INITIALIZING);
							else if(!daemon.isConnectionAccepted())
								out.println(STATE_DISCONNECTED);
							else if(!daemon.isConnectionAccepted())
								out.println(STATE_DISCONNECTED);
							else if(daemon.getFileTransferManager().haveAllFilesFinishedTranferring())
								out.println(STATE_SYNCED);
							else 
								out.println(STATE_SYNCING);
							out.flush();
							break;
						case CLEAN:
							daemon.printMessage(
									ResourceManager.getAccount().getRestrictionsList()
									, SyncDaemon.HEADER_CLEAN_CLOUD_FILES);
							break;
						case GET_ACCOUNT_SIZE:
							out.println(ResourceManager.getAccount().getRecordedSize());
							break;
						default:
							Syncrop.logger.logWarning("unkown option");
							
					}
				}
				if(socket!=null)
					socket.close();
			}
			catch (NoSuchElementException|IOException e){
				close();
			}
			catch (Exception|Error e)
			{
				SyncropClientDaemon.logger.logFatalError(e, "occured in Syncrop check thread");
				if(!SyncropClientDaemon.isShuttingDown())
					System.exit(0);
			}
			
		}
	}
	public void close(){
		if(sc!=null)
			sc.close();
		if(out!=null){
			out.flush();
			out.close();
		}
		
	}
	private void createSocket(){
		try {
			if(serverSocket!=null&&!serverSocket.isClosed())
				serverSocket.close();
			serverSocket = new ServerSocket(Settings.getSyncropCommunicationPort(),1);
		}catch(BindException e){
			Syncrop.logger.logError(e, ". There is probably another instance of Syncrop running. Exiting");
		}
		catch (IOException e)
		{
			if(!SyncropClientDaemon.isShuttingDown())
				Syncrop.logger.logError(e, "");
		}
		
	}
}