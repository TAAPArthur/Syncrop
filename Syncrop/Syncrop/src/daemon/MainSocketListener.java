package daemon;

import java.io.IOException;

import message.Message;

class MainSocketListener extends Thread	{
	/**
	 * 
	 */
	private final SyncDaemon syncDaemon;
	private Message message;
	private volatile boolean paused;
	public MainSocketListener(SyncDaemon syncDaemon)
	{
		super("main socket listener");
		this.syncDaemon = syncDaemon;
	}
	
	void setPaused(boolean b)
	{
		paused=b;
	}
	boolean isPaused(){return paused;}
	public void run()
	{
		while(!SyncDaemon.isShuttingDown())
		{
			while(SyncDaemon.mainClient.isConnectedToServer())
			{
				if(!SyncDaemon.isInstanceOfCloud()&&!SyncDaemon.mainClient.isConnectionAccepted())
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
					SyncDaemon.removeUser(message.getUsername(), e.toString());
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
			SyncDaemon.sleepLong();
			this.syncDaemon.connectToServer();
		}	
	}
}