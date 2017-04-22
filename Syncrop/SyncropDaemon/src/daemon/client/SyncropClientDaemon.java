package daemon.client;

import static notification.Notification.displayNotification;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;

import com.sun.jna.platform.FileUtils;

import account.Account;
import client.SecondaryClient;
import daemon.SyncDaemon;
import daemon.cloud.SyncropCloud;
import file.Directory;
import file.SyncROPItem;
import message.Message;
import notification.Notification;
import settings.Settings;
import syncrop.FileMetadataManager;
import syncrop.LostConnectionWithCloudException;
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
	SyncropCommunication communication;
	
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
		if(!isNotMac()||!isNotWindows())
			initializeFileUtils();
		communication=new SyncropCommunication(this);
		communication.start();
		if(!isShuttingDown())
			super.init();
	}
	/**
	 * Attempts a connection to Cloud and waits until one is made, authenticated and
	 *  files are synced
	 */
	protected void connectToServer(){	
		if(isShuttingDown())
			throw new SyncropCloseException();
		initializingConnection=true;
		int count=0;
		boolean tryingToAutoReconnectedToCloud=mainClient!=null;
		
		int lastConnectionFailedMessage=-1;
		boolean surpressConnectionMessage=false;
		
		logger.logTrace("Trying to connect to Server");
		fileTransferManager.reset();
		fileTransferManager.pause(true);
		
		if(mainClient!=null&&!surpressConnectionMessage){
			logger.log("connection closed for reason "+mainClient.getReasonToClose());
			logger.log("dynamic timeout: "+mainClient.getTimeout());
			Syncrop.sleepLong();
		}
		
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
				if(authenticate()){
					sendSettings();
					syncAllFilesToCloud();//sync();
					
				}
				fileTransferManager.pause(false);
				initializingConnection=false;
				break;
			} 
			catch (IOException|LostConnectionWithCloudException e){logger.log(e.toString());}
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
				displayNotification(message);
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
	public void syncAllFilesToCloud() throws IOException,LostConnectionWithCloudException
	{
		
		HashSet<String>set=new HashSet<String>();
		Account a=ResourceManager.getAccount();
		
		for(Directory dir:a.getDirectories())
			set.add(isNotWindows()?dir.getDir():SyncROPItem.toLinuxPath(dir.getDir()));
		for(String s:a.getRemovableDirectoriesThatExists())
			set.add(isNotWindows()?s:SyncROPItem.toLinuxPath(s));
		logger.log("Starting sync started: "+set);
		syncFilesToCloud(set.toArray(new String[set.size()]));
		
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
	public void syncFilesToCloud(String... pathsToSync) throws IOException,LostConnectionWithCloudException{
		
		final ArrayList<Object[]> message=new ArrayList<Object[]>();
		mainClient.printMessage(pathsToSync, HEADER_SET_ENABLED_PATHS);
		Iterable<SyncROPItem>items=FileMetadataManager.iterateThroughAllFileMetadata(null);
		for (SyncROPItem item:items)
			syncFilesToCloud(item,message);
		

		if(message.size()!=0)
			mainClient.printMessage(message.toArray(new Object[message.size()][5]), HEADER_SYNC_FILES);
		
		//Tells Cloud to send any files that this client does not have
		sleepShort();
		mainClient.printMessage(
			ResourceManager.getAccount().getRestrictionsList(), HEADER_SYNC_GET_CLOUD_FILES);
		
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
	private void syncFilesToCloud(SyncROPItem file,final ArrayList<Object[]> message){

		if(Syncrop.isShuttingDown())
			throw new SyncropCloseException();
		else if(!isConnectionAccepted())throw new LostConnectionWithCloudException();
		final int maxTransferSize=100;
		
		
		if(file==null||!file.isEnabled()||!file.isSyncable())return;
		
		if(!file.exists()&&!file.isDeletionRecorded()){
			logger.logTrace("File was deleted while server was off path="+file);
			file.setDateModified(Syncrop.getStartTime());	
		}
		
		if(file.hasBeenUpdated())
			file.save();
		message.add(file.formatFileIntoSyncData());
	
			
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
		else if(message.getHeader().equals(HEADER_SHARED_SUCCESS)){
			Notification.displayNotification("File shared "+(String) message.getMessage());
			communication.updateGUI();
		}
		else return false;
		return true;
	}
	@Override
	/**
	 * 	{@inheritDoc}
	 */
	public boolean verifyUser(String id,String accountName){
		return true;
	}
	private static FileUtils fileUtils=null;

	/**
	 * Used to initialize {@link #fileUtils} which is used to send files to trash. Only call if os is Mac or Windows
	 */
	public static void initializeFileUtils(){
		fileUtils = FileUtils.getInstance();
	}
	/**
	 * Sends a file to trash bin
	 * TODO Windows and Mac support
	 * @param f the file to send to trash
	 */
	public static void sendToTrash(File file)
	{
		if(!file.exists())return;
		if(logger.isDebugging())
			logger.log("Sending file to trash path="+file);
		
		try {
			if(isNotWindows()&&isNotMac())
				sendToLinuxTrash(file);
			else fileUtils.moveToTrash( new File[] {file});
			/*else if(AccountManager.notWindows)
				sendToWindowsTrash(f);
			else sendToMacTrash(f);*/
		}
		
		catch (IOException e) {
			logger.logError(e, "occured while trying to send file to trash path="+file);
		}
		
	}
	
	private static void sendToLinuxTrash(File file) throws IOException
	{
		String baseName=file.getName(),name=baseName;
		File trashInfoFile=new File(System.getProperty("user.home")+"/.local/share/Trash/info",name+".trashinfo");
		for(int i=2;trashInfoFile.exists();i++)
		{
			name=baseName+="."+i;
			trashInfoFile=new File(System.getProperty("user.home")+"/.local/share/Trash/info",name+".trashinfo");
		}			
		File trashFile=new File(System.getProperty("user.home")+"/.local/share/Trash/files",name);
		Files.move(file.toPath(), trashFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

		logger.log(file+" was sent to trash");
		//DeletionDate=2014-03-01T23:38:18
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		trashInfoFile.createNewFile();
		PrintWriter out=new PrintWriter(trashInfoFile);
		out.println("[Trash Info]");
		out.println("Path="+file.getAbsolutePath());
		out.println("DeletionDate="+dateFormat.format(System.currentTimeMillis()).replace(" ", "T"));
		out.close();
	
	}
	
}