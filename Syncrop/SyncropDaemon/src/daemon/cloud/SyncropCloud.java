package daemon.cloud;

import static file.SyncropItem.INDEX_PATH;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import account.Account;
import daemon.SyncDaemon;
import daemon.client.SyncropClientDaemon;
import file.SyncropFile;
import file.SyncropItem;
import listener.FileWatcher;
import message.Message;
import server.InternalServer;
import server.Server;
import settings.Settings;
import syncrop.FileMetadataManager;
import syncrop.ResourceManager;
import syncrop.SyncropLogger;
import transferManager.FileTransferManager;

/**
 * this will run on the server
 * @author taaparthur
 *
 */
public final class SyncropCloud extends SyncDaemon
{	
	 private static final Set<PosixFilePermission>FILE_PERMISSIONS=new HashSet<>(Arrays.asList(
			PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_READ,
			PosixFilePermission.GROUP_WRITE,PosixFilePermission.GROUP_READ));
	
	private static final Set<PosixFilePermission>DIR_PERMISSIONS=new HashSet<>(
			Arrays.asList(
					PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_READ,
					PosixFilePermission.GROUP_WRITE,PosixFilePermission.GROUP_READ,
					PosixFilePermission.GROUP_EXECUTE,PosixFilePermission.OWNER_EXECUTE));
	
	
	/**
	 * key-clients id</br>
	 * value-names of client including their id
	 */
	private static final HashMap<String, SyncropUser> clients=new HashMap<>();
	private final HashMap<String,HashSet<String>> syncedFiles=new HashMap<String,HashSet<String>>();;
		
	
	/**
	 * Creates and starts an instance of SyncropDaemon that runs as a server and not
	 * a client
	 * @throws IOException 
	 * @see SyncropClientDaemon#SyncropDaemon()
	 */
	public SyncropCloud(String instance,boolean clean) throws IOException{
		super(instance,clean);
	}
	
	public String getUsername(){
		return CLOUD_USERNAME;
	}
	@Override
	protected void checkFiles(boolean clean) throws IOException{
		
		super.checkFiles(clean);
		FileWatcher.checkMetadataForAllFiles(!clean);
	}
	
	@Override
	protected void connectToServer()
	{
		reset();
		fileTransferManager.pause(true);
		initializingConnection=true;
		boolean triedToConnectToServer=false;
		if(!ResourceManager.canReadAndWriteSyncropConfigurationFiles()){
			logger.log("Can't read from config files");
			System.exit(0);
		}
		do{
			if(!triedToConnectToServer)
				logger.log("trying to connected to server:");
			else sleep();
			try {
				if(mainClient!=null){
					logger.log("Closing server socket");
					((Server) mainClient).close();
				}
				mainClient=new InternalServer(Server.UNLIMITED_CONNECTIONS, Settings.isSSLConnection()?Settings.getSSLPort():Settings.getPort(), logger,getUsername(),application,Settings.isSSLConnection());
			} catch (IOException e) {
				if(!triedToConnectToServer){
					logger.log("Could not connect to server");
					sleep();
				}
				sleepLong();
			}
			triedToConnectToServer=true;
		}
		while(!isShuttingDown()&&(mainClient==null||!mainClient.isConnectedToServer()));
		if(mainClient.isConnectedToServer())
			logger.log("Connected to server");
		fileTransferManager.pause(false);
		initializingConnection=false;
	}
	
	@Override
	protected void startThreads()
	{
		super.startThreads();
		mainSocketListenerNotification();
	}
	public void mainSocketListenerNotification()
	{
		new Thread("main Socket Listener notifications")
		{
			public void run()
			{		
				while(!isShuttingDown())
				{
					Message notification=null;
					try 
					{
						while(mainClient.isConnectedToServer())
						{
							try {
								notification=mainClient.readNotification();
							} catch (NullPointerException e) {
								if(!isShuttingDown())
									logger.logError(e, "occured while trying to read notification");
								return;
							}
							if(notification==null) break;
							else if(notification.getMessage().equals(Message.MESSAGE_NO_CLIENTS))
								reset();
							else if(notification.hasHeader())
								switch (notification.getHeader())
								{
									case Message.HEADER_USER_LEFT:
										removeUser((String)notification.getMessage(),"User has left");
										break;
									case Message.HEADER_NEW_USER:
										break;
								}
							else logger.log("Unexpected message: "+notification);
							sleepShort();
						} 
						if(!isShuttingDown())
							SyncropCloud.sleep();
					} 
					catch (Exception e) {	logger.logError(e, "occured when reading notification:"+notification);}
					catch (Error e) {	logger.logError(e, "occured when reading notification:"+notification);System.exit(0);}
					sleepLong();
				}
			}
		}.start();
	}
	/**
	 * Resets the Cloud to before any Clients were connected to it. This method should
	 * be called when the last client has left
	 */
	public void reset()
	{
		fileTransferManager.reset();
		
		clients.clear();
		if(syncedFiles!=null)
			syncedFiles.clear();
		logger.log("Reseting");
	}
	
	/**
	 * Removes the user from the list of connected clients  
	 * @param username the name of the user to remove
	 */
	public void removeUser(String username,String reason)
	{
		if(clients.containsKey(username)){
			super.removeUser(username, reason);
			clients.remove(username);
			syncedFiles.remove(username);
			logger.log(username+" was removed from cloud",SyncropLogger.LOG_LEVEL_DEBUG);
		}
	}

	
	@Override
	public void updateAllClients(SyncropItem file,String targetToExclude){
		if(!isConnectionActive())
			return;
		logger.logTrace("Updating to all clients "+file.getPath()+" excluding "+targetToExclude+" out of "+clients.size()+" users");
		boolean updatedKey=false;
		for(String key:clients.keySet()){
			if(!key.equals(targetToExclude)&&file.getOwner().equals(clients.get(key).getAccountName()))
				if(!clients.get(key).isPathRestricted(file.getPath())){
					if(!updatedKey){
						((SyncropFile)file).updateKey();
						file.save();
						updatedKey=true;
						
					}
					fileTransferManager.addToSendQueue(file,key);
				}
				else logger.logTrace("File not echoed because restricted");
			else logger.log(key+" is not target receipent");
		}
	}
	
	/**
	 * Authenticates the user who sent m
	 * @param m A message containing a String [] of accountName,email, and token
	 */
	private void authenticate(Message m){
		Object o[]=(Object[]) m.getMessage();
		authenticate(m.getUserID(),(String)o[0],(String)o[1],(String)o[2]);
	}
	
	/**
	 * Queries the server to authenticate the user.
	 * @param username the name of the user to authenticate
	 * @param email the email of the user to authenticate
	 * @param refreshToken the token of the user
	 * @return true if the user has been authenticated
	 */
	public static boolean authenticateUser(String username,String email,String refreshToken){
		if(Settings.getAuthenticationScript()==null)return false;
		int exitCode=127;
		try {
			Process p = Runtime.getRuntime().exec(Settings.getAuthenticationScript()+" "+username+" "+email+" "+refreshToken);
			exitCode = p.waitFor();
		} catch (IOException | InterruptedException e) {
			logger.logError(e);
		}
		return exitCode==0;	
	}
	/**
	 * 
	 * @param id the userID of the client who requsted authentication
	 * @param accountName the name of the account to authenticate
	 * @param email the email of the user
	 * @param refreshToken the token of the user
	 */
	void authenticate(String id, String accountName,String email, String refreshToken)
	{	
		if(authenticateUser(accountName, email, refreshToken)){
			clients.put(id, new SyncropUser(id,accountName));
			mainClient.printMessage(new String[]{id,accountName}, Message.TYPE_MESSAGE_TO_SERVER, Message.HEADER_SET_GROUP);
			mainClient.printMessage(true,HEADER_AUTHENTICATION_RESPONSE, id);
			
			syncedFiles.put(id, new HashSet<String>());
			logger.log("Account "+accountName+" verified for "+id,SyncropLogger.LOG_LEVEL_TRACE);
		}
		else {
			logger.log("Account "+accountName+" unverified for "+id,SyncropLogger.LOG_LEVEL_WARN);
			mainClient.printMessage(false,HEADER_AUTHENTICATION_RESPONSE, id);
		}
	}
	
	
	
	protected void setEnabledDirectories(final Message message){
		final SyncropUser user=clients.get(message.getUserID());
		final String []enabledPaths=((String[])message.getMessage());
		user.addEnabledDirs(enabledPaths);
	}
	protected void syncFilesWithClient(final Message message) throws IOException
	{
		logger.log("Syncing remaining files with client");
		final String []restrictions=((String[])message.getMessage());
		
		final SyncropUser user=clients.get(message.getUserID());
		user.addRestrictions(restrictions);
		logger.logTrace("client restrinction"+user.getRestrictions().toString());
		final String accountName=user.getAccountName();
		
		boolean removable=false;
		//change standards; metadata in home"
		while(removable=!removable){//regular vs removable
			Iterable<SyncropItem>items=FileMetadataManager.iterateThroughAllFileMetadata(user.getAccountName());
			for (SyncropItem item:items)
				if(item.isEnabled())
					syncFilesToClient(message.getUserID(),user,item);
		}
		this.syncedFiles.get(message.getUserID()).clear();
		logger.log("files from Cloud have been synced with "+message.getUserID()+"("+accountName+")");
		
	}
		
	void syncFilesToClient(final String id,SyncropUser user,SyncropItem file){
		
		if(!file.exists()||syncedFiles.get(id).contains(file.getPath())){
			logger.logTrace("File would have already been synced"+file.getPath());
			return;
		}
		else if(!file.isEnabled()){
			logger.logTrace(file.getPath()+" is not enabled");
		}
		else if(!file.isSyncable()){
			logger.logTrace(file.getPath()+" is not syncable");
		}
		else {
			if(file.isSymbolicLink()){
				try {
					
					File target=Files.readSymbolicLink(file.getFile().toPath()).toFile();
					if(target.list()!=null&&target.length()>=0){
						logger.logTrace("Sybmolic link of nonempty dir; not syncing");
						return;
					}
				} catch (IOException e) {}
			}
			if(user.isPathEnabled(file.getPath())){
					logger.logTrace("Local client does not have "+file);
					if(user.deletingFilesNotOnClient){
						logger.logTrace("Deleting  "+file+" because it is not on client");
						file.delete(user.getLongInTime());
					}
					else fileTransferManager.addToSendQueue(file, id);
					return;
				}
		}
		
	}
	
	protected void syncFiles(Message message)
	{
		Object files[][]=(Object[][])message.getMessage();
		String accountName=clients.get(message.getUserID()).getAccountName();
		
		
		HashSet<String>syncedFiles=checkClientsfiles(files, accountName, message.getUserID());
		if(syncedFiles==null){
			logger.log("No files synced");
			return;
		}
		
		this.syncedFiles.get(message.getUserID()).addAll(syncedFiles);	
		logger.log("total Synced file size="+this.syncedFiles.get(message.getUserID()).size(),SyncropLogger.LOG_LEVEL_ALL);
	}
	
	private HashSet<String> checkClientsfiles(Object files[][],String owner,String id)
	{
		ArrayList<String>filesToAddToDownload=new ArrayList<String>();
		
		HashSet<String> listOfClientFiles=new HashSet<String>(1);
		
		if(files==null){
			logger.logWarning("synced files were null");
			return null;
		}
		for(int i=0;i<files.length;i++)
		{
			try {
				final String path=(String)files[i][INDEX_PATH];
				if(path==null)break;
				ResourceManager.lockFile(path, owner);
				if(syncedFiles.get(id).contains(path)){
					logger.log("path has already been added to synced paths; path="+path);
					continue;
				}
				SyncropItem localFile=ResourceManager.getFile(path,owner);
				SyncropItem.SyncropPostCompare result= SyncropItem.compare(localFile, files[i]);
				switch(result){
					case SKIP:
						continue;
					case SYNC_METADATA:
						sendMetadata(localFile, id);
					case SYNCED:
						localFile.save();
						updateAllClients(localFile, id);
						break;
					case DOWNLOAD_REMOTE_FILE:
						filesToAddToDownload.add(path);
						break;
					case SEND_LOCAL_FILE:
						fileTransferManager.addToSendQueue(localFile, id);
						break;
				}
				ResourceManager.unlockFile(path, owner);
				listOfClientFiles.add(path);
			} catch (Exception e) {
				logger.logError(e, "Error syncing file "+i+" :"+files[i][0]);
				removeUser(id, "Error syncing file "+i+" :"+files[i][0]);
			}
		}
		
		if(filesToAddToDownload.size()>0){
			logger.log("requesting to download "+filesToAddToDownload.size()+" files");
			mainClient.printMessage(filesToAddToDownload.toArray(new String[filesToAddToDownload.size()]),
				FileTransferManager.HEADER_ADD_MANY_TO_SEND_QUEUE,id);
		}
	
		return listOfClientFiles;
	}

	/**
	 * Removes all files that are restricted for the user specified by the userID of the message.
	 * @param message a message containing the userID of the SyncropUser to whoose files should be cleaned.
	 * 
	 */
	private void clean(Message message){
		final SyncropUser user=clients.get(message.getUserID());
		
		SimpleFileVisitor<Path>visitor=new SimpleFileVisitor<Path>() {
			Account account=ResourceManager.getAccount(user.getAccountName());
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
				if(user.isPathRestricted(dir.toString()))
					dir.toFile().delete();
				return FileVisitResult.CONTINUE;
			}
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs){
				if(account.isPathContainedInDirectories(dir.toString()))
					return FileVisitResult.CONTINUE;
				else return FileVisitResult.SKIP_SUBTREE;
			}
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if(user.isPathRestricted(file.toString()))
					file.toFile().delete();
				return FileVisitResult.CONTINUE;
			}
			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
				return FileVisitResult.SKIP_SUBTREE;
			}
		};
		try {
			Files.walkFileTree(new File(ResourceManager.getHome(user.getAccountName(), true)).toPath(), visitor);
			Files.walkFileTree(new File(ResourceManager.getHome(user.getAccountName(), false)).toPath(), visitor);
		} catch (IOException e) {
			logger.logError(e);
		}
		
	}
	
	void stopSharingFile(Message message){
		String []s=(String[]) message.getMessage();
		//String path=s[0];
		boolean sharedByHost=Boolean.parseBoolean(s[1]);
		//SyncropUser user=clients.get(message.getUserID());
		//SyncROPItem item=ResourceManager.getFile(path, user.getAccountName());
		//SharedFile file=ResourceManager.getSharedFile(path);
		if(sharedByHost){
			//TODO remove key
			//fix all shared paths
		}
		else ;
		//delete literal link; copy (no link) shared dir to spot;
	}
	
	
	@Override
	public boolean handleResponse(Message message)
	{
		if(super.handleResponse(message))return true;
		else
			switch(message.getHeader())
			{
				case HEADER_SYNC_FILES:
					syncFiles(message);
					break;
				
				case HEADER_SYNC_GET_CLOUD_FILES:
					try {
						syncFilesWithClient(message);
					} catch (IOException e) {
						logger.logError(e);
					}
					break;
				case HEADER_SET_ENABLED_PATHS:
					setEnabledDirectories(message);
					break;
				case HEADER_AUTHENTICATION:
					authenticate(message);
					break;
				case HEADER_CLEAN_CLOUD_FILES:
					clean(message);
					break;
				case HEADER_USER_SETTINGS_CONFLICT_RESOLUTION:
					getSyncropUser(message.getUserID()).setConflictResolution((int)message.getMessage());
					break;
				case HEADER_USER_SETTINGS_DELETING_FILES_NOT_ON_CLIENT:
					getSyncropUser(message.getUserID()).setDeletingFilesNotOnClient((boolean)message.getMessage());
					break;
						
				default: return false;
			}
		return true;
	
	}
	@Override
	public void setPropperPermissions(SyncropItem item){
		try {
			if(item==null){
				logger.logWarning("item is null when trying to set permissions");
				return;
			}
			if(!item.exists())
				return;	
			
			Set<PosixFilePermission>currentPermissions=Files.getPosixFilePermissions(item.getFile().toPath(),
					LinkOption.NOFOLLOW_LINKS);
			if(currentPermissions.addAll(item.isDir()?DIR_PERMISSIONS:FILE_PERMISSIONS))
				Files.setPosixFilePermissions(item.getFile().toPath(), currentPermissions);
		}
		catch(FileSystemException e){
			logger.logWarning("Do not have write permissions to "+item.getAbsPath());
			logger.logWarning("item"+item.toString());
			logger.logWarning("isEnabled:"+item.isEnabled());
			
		}
		catch (SecurityException | IOException e) {
			logger.logError(e, " occured while trying to change the write/execute permissions of "+item.getAbsPath());
		}
	}
	
	@Override
	public boolean verifyUser(String id,String accountName){
		boolean result =clients.get(id).getAccountName().equals(accountName);
		if(!result)
			logger.logDebug(id+" does not have access to "+accountName);
		return result;
	}	
	public static SyncropUser getSyncropUser(String id){
		return clients.get(id);
	}
	public static boolean hasUser(String id){
		return clients.containsKey(id);
	}
}