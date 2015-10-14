package daemon;


import static file.SyncROPItem.DATE_MODIFIED;
import static file.SyncROPItem.EXISTS;
import static file.SyncROPItem.KEY;
import static file.SyncROPItem.PATH;
import static file.SyncROPItem.SYMBOLIC_LINK_TARGET;
import static transferManager.FileTransferManager.HEADER_DELETE_MANY_FILES;

import java.io.File;
import java.io.IOException;
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
import authentication.Authenticator;
import file.Directory;
import file.SyncROPDir;
import file.SyncROPFile;
import file.SyncROPItem;
import file.SyncROPSymbolicLink;
import listener.FileWatcher;
import message.Message;
import server.InternalServer;
import server.Server;
import settings.Settings;
import syncrop.ResourceManager;
import syncrop.Syncrop;
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
	
	private boolean updateAllClients=true;
	
	/**
	 * key-clients id</br>
	 * value-names of client including their id
	 */
	private static final HashMap<String, SyncropUser> clients=new HashMap<>();
	final HashMap<String,HashSet<String>> syncedFiles=new HashMap<String,HashSet<String>>();;
		
	final static long milliSecondsPerPing=120000;
	
	public static void main (String args[]) throws IOException
	{
		String instance="";
		if(args.length>0)
			for(String s:args)
				if(s.startsWith("-i"))
					instance=s.substring(2).trim();
				else if(s.startsWith("-v")){
					System.out.println(Syncrop.getVersionID());
					System.exit(0);
				}
				
		new SyncropCloud(instance);
	}
	
	/**
	 * Creates and starts an instance of SyncropDaemon that runs as a server and not
	 * a client
	 * @throws IOException 
	 * @see SyncropClientDaemon#SyncropDaemon()
	 */
	public SyncropCloud(String instance) throws IOException{super(instance);}
	
	public String getUsername(){
		return CLOUD_USERNAME;
	}
	@Override
	protected void checkFiles(){
		FileWatcher.checkAllMetadataFiles();
		super.checkFiles();
	}
	
	@Override
	protected void connectToServer()
	{
		reset();
		fileTransferManager.pause(true);
		initializingConnection=true;
		boolean triedToConnectToServer=false;
		if(!ResourceManager.canReadAndWriteSyncropConfigurationFiles())
		{
			logger.log("Can't read from config files");
			System.exit(0);
		}
		do
		{
			if(!triedToConnectToServer)
				logger.log("trying to connected to server:");
			else sleep();
			try {
				if(mainClient!=null){
					logger.log("Closing server socket");
					((Server) mainClient).close();
				}
				mainClient=new InternalServer(Server.UNLIMITED_CONNECTIONS, Settings.getPort(), logger,getUsername(),application, milliSecondsPerPing);
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
										removeUserInfo((String)notification.getMessage());
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
	public void removeUserInfo(String username)
	{
		clients.remove(username);
		syncedFiles.remove(username);
		
		if(fileTransferManager.isUserSendingTo(username))
			fileTransferManager.resetSendInfo();
		if(fileTransferManager.isUserReceivingFrom(username))
			fileTransferManager.resetReceiveInfo();
		logger.log(username+" was removed from cloud",SyncropLogger.LOG_LEVEL_DEBUG);
	}
	@Override
	public String deleteManyFiles(String userId,Object[][] files){
		setUpdateAllClients(false);
		String target=super.deleteManyFiles(userId, files);
		setUpdateAllClients(true);
		mainClient.printMessage(files, HEADER_DELETE_MANY_FILES,new String[]{target},userId);
		return target;
	}
	public void setUpdateAllClients(boolean b){updateAllClients=b;}
	
	public void updateAllClients(SyncROPItem file,String targetToExclude)
	 {		
		if(isConnectionActive()&&updateAllClients)
			for(String key:clients.keySet())
				if(!key.equals(targetToExclude)&&file.getOwner().equals(clients.get(key).getAccountName()))
					fileTransferManager.addToSendQueue(file,key);
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
	 * 
	 * @param id the userID of the client who requsted authentication
	 * @param accountName the name of the account to authenticate
	 * @param email the email of the user
	 * @param refreshToken the token of the user
	 */
	void authenticate(String id, String accountName,String email, String refreshToken)
	{	
		if(Authenticator.authenticateUser(accountName, email, refreshToken)){
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
	
	
	protected void syncFilesWithClient(Message message)
	{
		logger.log("Syncing remaining files with client");
		final String []restrictions=((String[][])message.getMessage())[0];
		String []parentPaths=((String[][])message.getMessage())[1];
		
		SyncropUser user=clients.get(message.getUserID());
		user.addRestrictions(restrictions);
		logger.log("client restrinction"+user.getRestrictions().toString());
		
		String accountName=user.getAccountName();
		File parent=new File(ResourceManager.getMetadataDirectory(),accountName);
		
		for(File metaDataFile:parent.listFiles())
			if(!metaDataFile.equals(ResourceManager.getMetadataVersionFile()))
				syncFilesToClient(message.getUserID(),user,metaDataFile,"", parentPaths);
		
		this.syncedFiles.get(message.getUserID()).clear();
		logger.log("files from Cloud have been synced with "+message.getUserID()+"("+accountName+")");
	}
	
	void syncFilesToClient(final String id,final SyncropUser user,File metaDataFile,String relativePath,final String []parentPaths){
		logger.log("Checking "+relativePath,SyncropLogger.LOG_LEVEL_ALL);
		if(user.isPathRestricted(relativePath))
			return;
		if(metaDataFile.isDirectory()){
			for(File file:metaDataFile.listFiles())
				syncFilesToClient(id,user,file,
					relativePath+(relativePath.isEmpty()?"":File.separatorChar)+file.getName(),parentPaths);
				
		}
		if(!metaDataFile.isDirectory()||metaDataFile.list().length==0) {
			SyncROPItem file=ResourceManager.readFile(metaDataFile);
			if(file==null||!file.exists()||syncedFiles.get(id).contains(file.getPath())||
					!file.isEnabled())return;
			if(file.exists()&&file.isDir()&&!file.isEmpty())return;
			for(String parentDir:parentPaths)
				if(Directory.isPathContainedInDirectory(file.getPath(), parentDir))
				{
					fileTransferManager.addToSendQueue(file, id);
					return;
				}
		}
	}
	
	protected void syncFiles(Message message)
	{
		Object files[][]=(Object[][])message.getMessage();
		String accountName=clients.get(message.getUserID()).getAccountName();
		
		
		HashSet<String>syncedFiles=checkClientsfiles(files, accountName, message.getUserID());
		if(syncedFiles==null)return;
		
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
				final String path=(String)files[i][PATH];
				if(path==null)break;
				SyncROPItem localFile=ResourceManager.getFile(path,owner);
				if(!SyncROPFile.isValidFileName(path)||
						(localFile!=null&&!localFile.isEnabled()))
				{
					logger.logWarning("Checking client files; File is not synced because it is not enabled"+localFile); 
					continue;
				}
				logger.log("Comparing "+path,SyncropLogger.LOG_LEVEL_ALL);
				
				
				Long localDateMod=
						localFile==null?
							-1
							:localFile.getDateModified();
				Long clientDateMod=(Long)files[i][DATE_MODIFIED];
				long clientKey=(Long)files[i][KEY];
				boolean clientFileExists=(Boolean)(files[i][EXISTS]);
				String linkTarget=(String)(files[i][SYMBOLIC_LINK_TARGET]);
				boolean clientDir=clientKey==-1;
				
				
				if(syncedFiles.get(id).contains(path))
					logger.log("path has already been added to synced paths; path="+path);
				else listOfClientFiles.add(path);
				
				ResourceManager.lockFile(path, owner);
				
				if(localFile!=null &&localFile.isSymbolicLink()){
					logger.log(localFile.toString());
					logger.log(Arrays.asList(files[i]).toString());
				}
				//if client file is existing symbolic link
				//and local file is non symbolic dir
				//then do nothing
				if(new File(ResourceManager.getAbsolutePath(path,owner)).isDirectory()&&
						linkTarget!=null&&clientFileExists){}
				//same thing but for client
				else if(localFile!=null&&localFile.isSymbolicLink()&&
						((SyncROPSymbolicLink)localFile).isTargetDir()&&
						clientDir&&clientFileExists){}
				else if(clientDir){
					//keys do not need to be updated because dirs don't have keys
					if(localFile==null)
						if(clientFileExists)//if client file exists and local file doesn't, create dir
						{
							localFile=new SyncROPDir(path, owner,clientDateMod);
							localFile.save();
							localFile.createFile(clientDateMod);
							updateAllClients(localFile,id);
						}
						else {
							logger.logWarning("rare case: localfile==null but file exists; file is being deleted");
							File file=new File(ResourceManager.getAbsolutePath(path, owner));
							if(file.exists())
								file.delete();
						}
					else if(localFile instanceof SyncROPDir){
						if((!clientFileExists&&!localFile.exists())||
								(clientFileExists&&localFile.exists())){
							if(clientDateMod>localDateMod){
								localFile.setDateModified(clientDateMod);
								localFile.save();
								updateAllClients(localFile,id);
							}	
						}
						else if(clientFileExists)// local files has to not exists at this point
							if(clientDateMod>=localFile.getDateModified()){
								localFile.createFile(clientDateMod);
								localFile.save();
								updateAllClients(localFile,id);
							}
							//delete client file;
							else fileTransferManager.addToSendQueue(localFile, id);
						else if(!clientFileExists){//local files has to exists
							if(clientDateMod>localFile.getDateModified()&&((SyncROPDir)localFile).isEmpty()){
								localFile.delete(clientDateMod);
								localFile.save();
								updateAllClients(localFile,id);
							}
							else
								fileTransferManager.addToSendQueue(localFile, id);
						}
					}
					else {
						logger.log("File/Directory conflict; "
								+ "file is being made into a syncrop conflict path="+path);
						((SyncROPFile) localFile).makeConflict();
						localFile=new SyncROPDir(path, owner,clientDateMod);
						localFile.createFile(clientDateMod);
						localFile.save();
					}
				}
				else {// for files
					if(localDateMod>clientDateMod){//if cloud file is newer than client file
						if(localFile.exists()||clientFileExists||localFile.getKey()!=clientKey)
							fileTransferManager.addToSendQueue(localFile,id);//send file to client
					}
					else if(localDateMod<clientDateMod){//if client file is newer than cloud file
						if(clientFileExists||clientKey!=localFile.getKey())
							filesToAddToDownload.add(path);
						else localFile.delete(clientDateMod);
					}
					else if(localFile.exists()^clientFileExists){
						logger.log("rare case; client and local date mod is the same but only one exists: "+
								clientDateMod+"="+localDateMod);
						if(localFile.exists()){
							logger.logTrace("local file exists and client file does not");
							fileTransferManager.addToSendQueue(localFile,id);//send file to client
						}
						else {
							logger.logTrace("client file exists and local file does not");
							filesToAddToDownload.add(path);
						}
					}
				}
				ResourceManager.unlockFile(path, owner);
			} catch (Exception e) {
				logger.logError(e, "Error syncing file "+i+" :"+files[i][0]);
				removeUser(id, "Error syncing file "+i+" :"+files[i][0]);
			}
		}
		logger.log("requesting to download "+filesToAddToDownload.size()+" files");
		if(filesToAddToDownload.size()!=0)
			mainClient.printMessage(filesToAddToDownload.toArray(new String[filesToAddToDownload.size()]),
				FileTransferManager.HEADER_ADD_MANY_TO_SEND_QUEUE,id);
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
					syncFilesWithClient(message);
					break;
				
				case HEADER_AUTHENTICATION:
						authenticate(message);
					break;
				case HEADER_CLEAN_CLOUD_FILES:
					clean(message);
					break;
						
				default: return false;
			}
		return true;
	
	}
	@Override
	protected void setPropperPermissions(SyncROPItem item){
		try {
			long dateMod= item.getDateModified();
			if(!item.exists())
				return;	
			if(item.isSymbolicLink())return;
			//TODO check dir permission
			if(item.isDir())
				if(Files.getPosixFilePermissions(item.getFile().toPath(),LinkOption.NOFOLLOW_LINKS).equals(DIR_PERMISSIONS))
					return;	
				else  Files.setPosixFilePermissions(item.getFile().toPath(), DIR_PERMISSIONS);
			else
				if(Files.getPosixFilePermissions(item.getFile().toPath(),LinkOption.NOFOLLOW_LINKS).equals(FILE_PERMISSIONS))
					return;
				else Files.setPosixFilePermissions(item.getFile().toPath(), FILE_PERMISSIONS);
			if(dateMod!=item.getFile().lastModified()){
				logger.logWarning("setting permission changed the date mod: "+
						logger.getDateTimeFormat().format(item.getFile().lastModified()));
				item.setDateModified(dateMod);
			}
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