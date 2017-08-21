package daemon.client;

import static notification.Notification.displayNotification;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.HashSet;

import javax.net.ssl.SSLHandshakeException;

import account.Account;
import client.SecondaryClient;
import daemon.SyncDaemon;
import daemon.cloud.SyncropCloud;
import file.Directory;
import file.SyncropItem;
import logger.Logger;
import message.Message;
import settings.Settings;
import syncrop.FileMetadataManager;
import syncrop.ResourceManager;
import syncrop.Syncrop;
import syncrop.SyncropCloseException;

/**
 * 
 * An instance of Syncrop used for general purpose client communication.
 * This class provides methods to connect to Cloud.
 *
 */
public class SyncropClientDaemon extends SyncDaemon{
	
	/**
	 * If SyncropClientDaemon is in the process of authenticating to Cloud
	 */
	static volatile boolean authenticating;
	
	
	public static void main (String args[]) throws IOException
	{
		//handles parameters
		String instance="";
		
		if(args.length>0)
			for(String s:args)
				if(s.startsWith("-i"))
					instance=s.substring(2).trim();
				
		SyncropClientDaemon  d=new SyncropClientDaemon(instance,false);
		d.startConnection();
	}
	
	

	/**
	 * Creates and starts an instance of SyncropDaemon that runs as a client and not
	 * a server
	 * @throws IOException 
	 * @see SyncropCloud#Cloud()
	 */
	public SyncropClientDaemon(String instance,boolean clean)
	{
		super(instance,clean);
	}
	
	/**
	 * Attempts a connection to Cloud and waits until one is made, authenticated and
	 *  files are synced
	 */
	@Override
	protected void connectToServer(){	
		if(isShuttingDown())
			throw new SyncropCloseException();
		initializingConnection=true;
		int count=0;
		boolean tryingToAutoReconnectedToCloud=mainClient!=null;
		
		int lastConnectionFailedMessage=-1;
		boolean surpressConnectionMessage=false;
		logger.logTrace("Trying to connect to Server");
		
		fileTransferManager.pause(true);
		
		if(mainClient!=null) {
			if(getFileTransferManager().getTransferedFiles()>0) {
				notificationManager.notifyUser();
			}
			if(!surpressConnectionMessage){
				logger.log("connection closed for reason "+mainClient.getReasonToClose());
				logger.log("dynamic timeout: "+mainClient.getTimeout());
				Syncrop.sleepLong();
			}
		}
		fileTransferManager.reset();
		//if(!ResourceManager.canReadAndWriteSyncropConfigurationFiles())
			//waitForConfigFiles();
		do {
			do
			{
				if(isShuttingDown())return;
				if(lastConnectionFailedMessage!=-1&&!surpressConnectionMessage){
					displayNotification("Disconnected From Cloud");
					surpressConnectionMessage=true;
				}
				
				count++;
				if(count++>15) {
					sleepLong();//when connection has failed repeatedly, sleep extra time;
					count=0;
				}
		
				//Initializes the main client and has it connect to Server
				//TODO make id username
				try {
					if(Settings.isSSLConnection())
						mainClient=new SecondaryClient(ResourceManager.getID(),
								Settings.getHost(),Settings.getSSLPort(), application,true);
					else 
						mainClient=new SecondaryClient(ResourceManager.getID(),
							Settings.getHost(),Settings.getPort(), application,false);
					count=0;
				}
				catch (UnknownHostException e) {
					if(lastConnectionFailedMessage!=-2)
						logger.log(e.toString()+" occured while trying to connect to Server");
					lastConnectionFailedMessage=-2;
				}
				catch (ConnectException e){
					if(lastConnectionFailedMessage!=-3)
						logger.log(e.toString()+"; The Server is not running");
					lastConnectionFailedMessage=-3;
				}
				catch (SSLHandshakeException e){
					
					if(lastConnectionFailedMessage!=-4){
						logger.logError(e);
						e.printStackTrace();
					}
					lastConnectionFailedMessage=-4;
				}
				catch (IOException e){
					if(lastConnectionFailedMessage!=-5)
						logger.log(e.toString()+" occured while trying to connect to Server");
					lastConnectionFailedMessage=-5;
				}
				finally {
					if(mainClient==null||!mainClient.isConnectedToServer()){
						sleepLong();
						continue;
					}
				}
				
				//mainclient connects to Cloud
				
				if(mainClient.isConnectedToServer())
					do
						try 
						{
							if(lastConnectionFailedMessage==-1&&!surpressConnectionMessage)
								logger.log("trying to connect to Cloud");
							((SecondaryClient)mainClient).autoConnect();
							
							if(!surpressConnectionMessage){
								if(tryingToAutoReconnectedToCloud)
									displayNotification("Reconnected to Cloud");
								else displayNotification("Connected to Cloud");
								logger.log("Connection made");
							}
						} 
						catch (NullPointerException e)
						{
							if(lastConnectionFailedMessage!=1)
								if(mainClient.isConnectedToServer())
									logger.log("Lost connection with server");
								else logger.log("No connections are available");
							lastConnectionFailedMessage=1;
						}
						catch (IndexOutOfBoundsException e)
						{
							if(lastConnectionFailedMessage!=2)
							logger.log("Cannot connect to cloud because " +
								"it refused the connection or it is not running ");
							lastConnectionFailedMessage=2;
						}
						catch (IOException e) {
							if(lastConnectionFailedMessage!=3)
								logger.log(e.toString()+ " occured while trying to connection to Cloud");
							lastConnectionFailedMessage=3;
						}
						finally{
							if(!mainClient.isConnectionAccepted()){
								if(tryingToAutoReconnectedToCloud){
									displayNotification("Lost connection with server");
								}
								sleepLong();
							}
							
							tryingToAutoReconnectedToCloud=false;	
						}
					while(mainClient.isConnectedToServer()&&!mainClient.isConnectionAccepted()&&!isShuttingDown());
				else sleepLong();
			}
			while (mainClient==null||!mainClient.isConnectedToServer()||!mainClient.isConnectionAccepted());
			
			if(isShuttingDown())return;
			
			try {
				if(authenticate()){
					sendSettings();
					syncAllFilesToCloud(Settings.isForceSync());//sync();
					
				}
				fileTransferManager.pause(false);
				initializingConnection=false;
				break;
			} 
			catch (IOException e){logger.log(e.toString());}
			catch (Exception e) {
				logger.logFatalError(e, "occured after connecting to Cloud");
				System.exit(0);
			}
			sleep();
			surpressConnectionMessage=true;
		}while(mainClient==null||!mainClient.isConnectionAccepted());
	}
	private void sendSettings(){
		mainClient.printMessage(Settings.getConflictResolution(), HEADER_USER_SETTINGS_CONFLICT_RESOLUTION);
		if(Settings.isDeletingFilesNotOnClient())
			mainClient.printMessage(null, HEADER_USER_SETTINGS_DELETING_FILES_NOT_ON_CLIENT);
	}
	/**
	 * This method handles authentication.<br/>
	 * 
	 * <br/><br/>
	 * <h1>Note: The cloud processes the authentication and gives permissions accordingly</h1>
	 */
	public boolean authenticate() throws IOException
	{
		logger.log("Authenicating");
		requestAuthentication();
		Message m=null;
		do{
			m=mainClient.readMessage();
			if(m!=null&&!m.getHeader().equals(HEADER_AUTHENTICATION_RESPONSE))
				logger.log("got message"+m+"  when expecting HEADER_AUTHENTICATION_RESPONSE");
		}while(m!=null&&!m.getHeader().equals(HEADER_AUTHENTICATION_RESPONSE));
		
		return recieveAuthentication(m);
	}
	/**
	 * Sends a message to Cloud requesting authentication for {@link ResourceManager#getAccount()}
	 */
	private void requestAuthentication(){
		logger.logTrace("Requesting Authentication");
		authenticating=true;
		Account a=ResourceManager.getAccount();
		Object []o={a.getName(),a.getEmail(),a.getToken()};
		
		mainClient.printMessage(o,HEADER_AUTHENTICATION);
	}

	/**
	 * Receives the authentication message and notifies the user if the account failed to
	 * be authenticated. 
	 * @param m the message containing the authentication response
	 * @return true if and only if the account has been authenticated.
	 */
	private boolean recieveAuthentication(Message m){
		boolean verifed= (boolean) m.getMessage();
		if(!verifed){
			Account a=ResourceManager.getAccount();
			if(a.isAuthenticated()){
				a.setAuthenticated(false);
				String message="This account failed to be authenticated: "+a.getName()+
						" Please update the password for these accounts";
				displayNotification(Logger.LOG_LEVEL_WARN,message);
				logger.log(message);
			}
		}
		authenticating=false;
		logger.logTrace("Finished Authentication");
		return verifed;
	}
	

	/**
	 * Sends the metadata of of all the files and dir to Cloud. Cloud compares the data 
	 * with its data and orders file deletion,conflicts, upload, and download accordingly  
	 * This method calls syncFilesToCloud("",set.toArray(new String[set.size()]) where
	 * set is the an ArrayList<String> of the regular and removable directores of all the 
	 * enabled accounts
	 * @throws IOException 
	 * 
	 * @see #syncFilesToCloud(String, String...)
	 */
	public void syncAllFilesToCloud(boolean force) throws IOException
	{
		HashSet<String>set=new HashSet<String>();
		Account a=ResourceManager.getAccount();
		
		for(Directory dir:a.getDirectories())
			set.add(isNotWindows()?dir.getDir():SyncropItem.toLinuxPath(dir.getDir()));
		for(String s:a.getRemovableDirectoriesThatExists())
			set.add(isNotWindows()?s:SyncropItem.toLinuxPath(s));
		logger.log("Starting sync: "+set);
		syncFilesToCloud(force,set.toArray(new String[set.size()]));
		
	}

	/**
	 * Sends the metadata of of all the enabled files and dir to Cloud. Cloud compares the data 
	 * with its data and orders file deletion,conflicts, upload, and download accordingly  
	 * This method calls {@link #syncDirsToCloud(String, String...)} to Sync directories
	 * after the regular files are synced
	 * @param parentDir only syncs files in the parent dir
	 * @param pathsToSync  only have the cloud sync files that are in this domain 
	 * @throws IOException 
	 */
	public void syncFilesToCloud(boolean force,String... pathsToSync) throws IOException{
		
		mainClient.printMessage(pathsToSync, HEADER_SET_ENABLED_PATHS);
		Iterable<SyncropItem>items=FileMetadataManager.iterateThroughAllFileMetadata(null);
		final int maxTransferSize=1024;
		Object[][] message=new Object[maxTransferSize][];
		int count=0,totalCount=0;
		for (SyncropItem item:items) {
			if(item==null||!item.isEnabled()||!item.syncOnFileModification())continue;
			if(item.hasBeenUpdated())
				item.save();
			message[count++]=item.toSyncData();
			
			if(count==maxTransferSize){
				mainClient.printMessage(message, force?HEADER_FORCE_SYNC_FILES:HEADER_SYNC_FILES);
				count=0;
				message=new Object[maxTransferSize][];
			}
			totalCount++;
		}
		if(count!=0)
			mainClient.printMessage(message, force?HEADER_FORCE_SYNC_FILES:HEADER_SYNC_FILES);
		logger.log("Syncing "+totalCount+" files");
		//Tells Cloud to send any files that this client does not have
		sleepShort();
		mainClient.printMessage(
			ResourceManager.getAccount().getRestrictionsList(), HEADER_SYNC_GET_CLOUD_FILES);	
	}

	
	@Override
	/**
	 * 	{@inheritDoc}
	 */
	public boolean verifyUser(String id,String accountName){
		return true;
	}

	
}