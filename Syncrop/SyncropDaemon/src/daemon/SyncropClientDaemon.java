package daemon;

import static notification.Notification.displayNotification;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;

import account.Account;
import client.SecondaryClient;
import file.Directory;
import file.SyncROPItem;
import message.Message;
import settings.Settings;
import syncrop.ResourceManager;
import syncrop.Syncrop;

/**
 * 
 * An instance of Syncrop used for general purpose client communication.
 * This class provides methods to connect to Cloud.
 *
 */
public class SyncropClientDaemon extends SyncDaemon{

	/**
	 * If SyncropClientDaemon is in the process of autheniticating to Cloud
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
				else if(s.startsWith("-v")){
					System.out.println(Syncrop.getVersionID());
					System.exit(0);
				}
		new SyncropClientDaemon(instance);
	}
	/**
	 * kills the application
	 */
	public static void stop(){
		logger.log("Method stop called");
		System.exit(0);
	}

	/**
	 * Creates and starts an instance of SyncropDaemon that runs as a client and not
	 * a server
	 * @throws IOException 
	 * @see SyncropCloud#Cloud()
	 */
	public SyncropClientDaemon(String instance) throws IOException
	{
		super(instance);
	}
	/**
	 * {@inheritDoc}
	 * <br/>
	 * This method has been override to create a {@link SyncropCommunication}SyncropCommunication instance to 
	 * communicate with a potential GUI.
	 */
	@Override
	public void init(){
		new SyncropCommunication(this).start();
		if(!isShuttingDown())
			super.init();
	}
	/**
	 * Attempts a connection to Cloud and waits until one is made, authenticated and
	 *  files are synced
	 */
	protected void connectToServer(){	
		initializingConnection=true;
		int count=0;
		boolean tryingToAutoReconnectedToCloud=mainClient!=null;
		
		int lastConnectionFailedMessage=-1;
		boolean surpressConnectionMessage=false;
		logger.logTrace("Trying to connect to Server");
		fileTransferManager.reset();
		fileTransferManager.pause(true);
		
		if(mainClient!=null&&!surpressConnectionMessage)
			logger.log("connection closed for reason "+mainClient.getReasonToClose());
		
		//TODO
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
				if(count++>15)
					sleepLong();//when connection has failed repeatedly, sleep extra time;
		
				//Initializes the main client and has it connect to Server
				//TODO make id username
				try {
					mainClient=new SecondaryClient(ResourceManager.getID(),
							Settings.getHost(),Settings.getPort(), application);
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
				catch (IOException e){
					if(lastConnectionFailedMessage!=-4)
						logger.log(e.toString()+" occured while trying to connect to Server");
					lastConnectionFailedMessage=-4;
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
				if(authenticate())
					syncFilesToCloud();//sync();
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
	/**
	 * This method handles authentication.<br/>
	 * 
	 * <br/><br/>
	 * <h1>Note: The cloud processes the authentication and gives permissions accordingly</h1>
	 */
	public boolean authenticate() throws IOException
	{
		requestAuthentication();
		Message m=null;
			do
				m=mainClient.readMessage();
			while(m!=null&&!m.getHeader().equals(HEADER_AUTHENTICATION_RESPONSE));
		
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

			a.setAuthenticated(false);
	
			String message="This account failed to be authenticated: "+a.getName()+
					" Please update the password for these accounts";
			displayNotification(message);
			logger.log(message);
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
	 * 
	 * @see #syncFilesToCloud(String, String...)
	 */
	public void syncFilesToCloud()
	{
		logger.logTrace("Syncing Files");
		HashSet<String>set=new HashSet<String>();
		Account a=ResourceManager.getAccount();
		
		for(Directory dir:a.getDirectories())
			set.add(isNotWindows()?dir.getDir():SyncROPItem.toLinuxPath(dir.getDir()));
		for(String s:a.getRemovableDirectoriesThatExists())
			set.add(isNotWindows()?s:SyncROPItem.toLinuxPath(s));
		
		syncFilesToCloud(set.toArray(new String[set.size()]));
		logger.logTrace("finished Syncing files");
	}

	/**
	 * Sends the metadata of of all the enabled files and dir to Cloud. Cloud compares the data 
	 * with its data and orders file deletion,conflicts, upload, and download accordingly  
	 * This method calls {@link #syncDirsToCloud(String, String...)} to Sync directories
	 * after the regular files are synced
	 * @param parentDir only syncs files in the parent dir
	 * @param pathsToSync  only have the cloud sync files that are in this domain 
	 */
	public void syncFilesToCloud(String... pathsToSync){
		File parent=ResourceManager.getMetadataDirectory();
		
		ArrayList<Object[]> message=new ArrayList<Object[]>();
				
		logger.log("Syncing files");
		
		File[] files=parent.listFiles();
		
		for(File file:files)
			if(!file.equals(ResourceManager.getMetadataVersionFile()))
				syncFilesToCloud(file, message);
		//message=Arrays.copyOf(message, count);
		if(message.size()!=0)
			mainClient.printMessage(message.toArray(new Object[message.size()][5]), HEADER_SYNC_FILES);
		//Tells Cloud to send any files that this client does not have
		sleepShort();
		mainClient.printMessage(
				new String[][]{ResourceManager.getAccount().getRestrictionsList(),pathsToSync}
				, HEADER_SYNC_GET_CLOUD_FILES);
	}
	/**
	 * Recursively goes through directories of the directory corresponding to metaDataFile uploads 
	 * their metadata to cloud. To increase performance, messages are not sent immediately but instead are sent in a group of {@value Syncrop#KILOBYTE}.
	 * <br/>
	 * While recursing through files, the method checks to see if the file has been updated or deleted and updates the metadata.
	 * Note this method does not check to see if new files have been added.
	 * @param metaDataFile the metadata directory to recursively check
	 * @param message the messages in queue to be sent; When messages are sent, this list is cleared
	 */
	private void syncFilesToCloud(File metaDataFile,final ArrayList<Object[]> message){
		final int maxTransferSize=64;
		if(metaDataFile.isDirectory()){
			sleepVeryShort();
			HashSet<String> metaDataDirs=new HashSet<String>();
			for(File file:metaDataFile.listFiles())
				if(file.isDirectory()){
					syncFilesToCloud(file, message);
					metaDataDirs.add(file.getName()+ResourceManager.METADATA_ENDING);
				}
			for(File file:metaDataFile.listFiles())
				if(!file.isDirectory()&&!metaDataDirs.contains(file.getName())){
					syncFilesToCloud(file, message);
				}
			for(String metaDataDir: metaDataDirs)
				syncFilesToCloud(new File(metaDataFile,metaDataDir), message);
		}
		else {
			SyncROPItem file=ResourceManager.readFile(metaDataFile);
			if(file==null)return;
			if(!file.isEnabled())return;
			if(file.exists()&&file.isDir()&&!file.isEmpty())return;
			if(!file.exists()&&!file.isDeletionRecorded()){
				logger.logTrace("File was deleted while server was off path="+file);
				file.setDateModified(Syncrop.getStartTime());
				file.tryToCreateParentSyncropDir();
			}
			if(file.hasBeenUpdated())
				file.save();
			message.add(file.formatFileIntoSyncData());
		}
			
		if(message.size()==maxTransferSize){
			mainClient.printMessage(message.toArray(new Object[message.size()][5]), HEADER_SYNC_FILES);
			message.clear();
			Syncrop.sleep();
		}
		
	}
	
	@Override
	/**
	 * {@inheritDoc}
	 */
	public boolean handleResponse(Message message){
		if(super.handleResponse(message))return true;
		else if(message.getHeader().equals(HEADER_AUTHENTICATION_RESPONSE)){
			recieveAuthentication(message);
			return true;
		}
		else return false;
	}
	@Override
	/**
	 * 	{@inheritDoc}
	 */
	public boolean verifyUser(String id,String accountName){
		return true;
	}
	
}