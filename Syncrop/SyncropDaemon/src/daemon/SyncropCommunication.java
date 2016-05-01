package daemon;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.NoSuchElementException;
import java.util.Scanner;

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

	public static final String SHUTDOWN="SHUTDOWN";
	public static final String STATUS="STATUS";
	public static final String CLEAN="CLEAN";
	public static final String UPDATE="UPDATE";
	
	public static final String SHARE="SHARE";
	public static ServerSocket serverSocket;
	SyncDaemon daemon;
	PrintWriter out;
	public SyncropCommunication(SyncDaemon daemon)
	{
		super("Syncrop communication thread");
		this.daemon=daemon;
	}
	public void updateGUI(){
		if(out!=null)
			out.println(UPDATE);
		out.flush();
	}
	public void run()
	{
		Syncrop.logger.log("Starting communication thread");
		createSocket();
		while(!SyncropClientDaemon.isShuttingDown())
		{
			Scanner sc=null;
			Socket socket=null;
			try {
				if(serverSocket.isClosed())
					createSocket();
				socket=serverSocket.accept();
				out=new PrintWriter(socket.getOutputStream());
				sc=new Scanner(new BufferedInputStream(socket.getInputStream()));
				
				while(!socket.isClosed()){
					String line=sc.nextLine();
					switch (line)
					{
						case SHUTDOWN:
							out.println("DONE");
							socket.close();
							serverSocket.close();
							Syncrop.logger.log("Syncrop GUI is requesting shutdown");
							System.exit(0);
							break;
						case STATUS:
							if(daemon.getFileTransferManager().haveAllFilesFinishedTranferring())
								out.println("Files synced");
							else if(daemon.getFileTransferManager().isSending()||daemon.getFileTransferManager().isReceiving())
								out.println("Syncing");
							else if(!SyncDaemon.isConnectionActive())
								out.println("Connecting");
							else out.println("Unkown");
							out.flush();
							break;
						case CLEAN:
							daemon.printMessage(
									ResourceManager.getAccount().getRestrictionsList()
									, SyncDaemon.HEADER_CLEAN_CLOUD_FILES);
							break;
						case SHARE:
							boolean sharePublic=Boolean.parseBoolean(sc.nextLine());
							String pathToShare=sc.nextLine();
							String usersToShareWith=sharePublic?null:sc.nextLine();
							String header=sharePublic?SyncDaemon.HEADER_REQUEST_SHARE_PUBLIC:SyncDaemon.HEADER_REQUEST_SHARE_PRIVATE;
							Syncrop.logger.log("Sharing file"+pathToShare+" with"+usersToShareWith);
							if(daemon.isConnectionAccepted())
								daemon.printMessage(new String[]{pathToShare,usersToShareWith}, header);
							break;
						default:
							out.println("Unknown");
							out.flush();
					}
				}
				if(socket!=null)
					socket.close();
			}
			catch (NoSuchElementException|IOException e){}
			catch (Exception|Error e)
			{
				SyncropClientDaemon.logger.logFatalError(e, "occured in Syncrop check thread");
				if(!SyncropClientDaemon.isShuttingDown())
					System.exit(0);
			}
			finally{
				if(sc!=null)
					sc.close();
				out.flush();
				out.close();
				
			}
		}
	}
	private void createSocket(){
		try {
			if(serverSocket!=null&&!serverSocket.isClosed())
				serverSocket.close();
			serverSocket = new ServerSocket(Settings.getPort(),1);
		}catch(BindException e){
			Syncrop.logger.logError(e, ". There is probably another instance of Syncrop running."+
					(Settings.allowMultipleInstances()?"":" Exiting"));
			if(!Settings.allowMultipleInstances())
				if(!SyncropClientDaemon.isShuttingDown())
					System.exit(0);
		}
		catch (IOException e)
		{
			if(!SyncropClientDaemon.isShuttingDown())
				Syncrop.logger.logError(e, "");
		}
		
	}
}