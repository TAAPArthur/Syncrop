package daemon;

import java.io.IOException;

import message.Message;
import syncrop.Syncrop;

/**
 * 
 * This thread continually reads from {@link SyncDaemon#mainClient} and passes output to
 * {@link SyncDaemon#handleResponse(Message)}
 * This thread should be paused
 *  while the connection to server is not active of while authenticating
 *
 */
public class MainSocketListener extends Thread	{
	
	private final SyncDaemon syncDaemon;
	private volatile boolean paused;
	public MainSocketListener(SyncDaemon syncDaemon){
		super("main socket listener");
		this.syncDaemon = syncDaemon;
	}
	
	void setPaused(boolean b){
		paused=b;
	}
	boolean isPaused(){return paused;}
	public void run(){
		Message message=null;
		while(!SyncDaemon.isShuttingDown())
		{
			while(SyncDaemon.mainClient.isConnectedToServer())
			{
				if(!SyncDaemon.isInstanceOfCloud()&&!SyncDaemon.mainClient.isConnectionAccepted())
					break;
				if(Syncrop.isShuttingDown()&&syncDaemon.fileTransferManager.haveAllFilesFinishedTranferring())
					break;
				try 
				{
					//if(mainClient.ready())
					message=SyncDaemon.mainClient.readMessage();
					
					SyncDaemon.logger.logTrace("read message "+message.getHeader());
					SyncDaemon.sleepVeryShort();
					if(!this.syncDaemon.handleResponse(message)) 
						SyncDaemon.logger.log("Header not found:"+message.getHeader());
					while(isPaused()&&!SyncDaemon.isShuttingDown())
						SyncDaemon.sleep();
				} 
				catch (NullPointerException e){
					if(SyncDaemon.mainClient.isConnectedToServer())
						SyncDaemon.logger.logFatalError(e," message is null but still connected to Server?");
					SyncDaemon.mainClient.closeConnection(e.toString(), true);
				}
				catch (IOException e){
					if(SyncDaemon.mainClient.isConnectedToServer())
						SyncDaemon.mainClient.closeConnection(e.toString(), true);
					//else the connection has already been closed
				}
				catch (Exception e) {
					SyncDaemon.logger.logFatalError(e," caused by message:"+message);
					syncDaemon.removeUser(message.getUserID(), e.toString());
				}
				catch (OutOfMemoryError e)
				{
					SyncDaemon.logger.logError(e,"Error in main listener caused by message:"+message);
					SyncDaemon.mainClient.closeConnection(e.toString(),true);		
				}
				catch (Error e) {
					SyncDaemon.logger.logFatalError(e,"Error in main listener caused by message:"+message);
					System.exit(0);
				}		
			}
			if(SyncDaemon.isShuttingDown())break;
			this.syncDaemon.connectToServer();
		}	
	}
}