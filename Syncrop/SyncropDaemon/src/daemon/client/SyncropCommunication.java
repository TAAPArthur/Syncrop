package daemon.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;

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
	
	Socket socket=null;
	DataOutputStream out;
	DataInputStream in=null;
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
				
				new Thread() {
					public void run(){
						communicate(socket);;
					}
				};
			}
			catch (IOException e){
				close();
			}
		}
	}
	public void communicate(Socket socket){
		try {
			out=new DataOutputStream(socket.getOutputStream());
			in=new DataInputStream(socket.getInputStream());
			out.flush();
			while(!Syncrop.isShuttingDown() && !socket.isClosed()){
				System.out.println("waiting");
				int statusCode=in.readInt();
				System.out.println("received code "+statusCode);
				Syncrop.logger.log(statusCode+"");
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
							out.writeUTF(STATE_INITIALIZING);
						else if(!daemon.isConnectionAccepted())
							out.writeUTF(STATE_DISCONNECTED);
						else if(daemon.isConnectionAccepted())
							out.writeUTF(STATE_CONNECTED);
						else if(daemon.getFileTransferManager().haveAllFilesFinishedTranferring())
							out.writeUTF(STATE_SYNCED);
						else 
							out.writeUTF(STATE_SYNCING);
						out.flush();
						Syncrop.logger.log("echoing status");
						break;
					case CLEAN:
						daemon.printMessage(
								ResourceManager.getAccount().getRestrictionsList()
								, SyncDaemon.HEADER_CLEAN_CLOUD_FILES);
						break;
					case GET_ACCOUNT_SIZE:
						out.writeLong(ResourceManager.getAccount().getRecordedSize());
						break;
					default:
						Syncrop.logger.logWarning("unkown option");
						
				}
			}
		}catch(IOException e) {}
		finally {try {socket.close();} catch (IOException e) {}}
	}
	public void close(){
		try {
			serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}	
	}
	private void createSocket(){
		try {
			if(serverSocket!=null&&!serverSocket.isClosed())
				serverSocket.close();
			serverSocket = new ServerSocket(Settings.getSyncropCommunicationPort(),4);
		}catch(BindException e){
			Syncrop.logger.logError(e, ". There is probably another instance of Syncrop running. Exiting");
			System.exit(1);
		}
		catch (IOException e)
		{
			if(!SyncropClientDaemon.isShuttingDown())
				Syncrop.logger.logError(e, "");
		}
		
	}
}