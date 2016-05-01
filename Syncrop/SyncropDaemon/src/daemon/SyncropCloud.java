package daemon;


import static file.SyncROPItem.INDEX_DATE_MODIFIED;
import static file.SyncROPItem.INDEX_EXISTS;
import static file.SyncROPItem.INDEX_KEY;
import static file.SyncROPItem.INDEX_MODIFIED_SINCE_LAST_KEY_UPDATE;
import static file.SyncROPItem.INDEX_PATH;
import static file.SyncROPItem.INDEX_SYMBOLIC_LINK_TARGET;
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

import listener.FileWatcher;
import message.Message;
import server.InternalServer;
import server.Server;
import settings.Settings;
import sharing.SharedFile;
import syncrop.ResourceManager;
import syncrop.Syncrop;
import syncrop.SyncropLogger;
import transferManager.FileTransferManager;
import account.Account;
import authentication.Authenticator;
import file.Directory;
import file.SyncROPDir;
import file.SyncROPFile;
import file.SyncROPItem;
import file.SyncROPSymbolicLink;

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
		
		String owner=super.deleteManyFiles(userId, files);

		logger.log("Echoing message to delete many files");
		mainClient.printMessage(files, HEADER_DELETE_MANY_FILES,new String[]{owner},userId);
		return owner;
	}
	
	public void updateAllClients(Message message,String owner,String targetToExclude){
		logger.logTrace("Updating to all clients excluding "+targetToExclude);
		Message newMessage=new Message(message,CLOUD_USERNAME);
		newMessage.addTargetsToExclude(targetToExclude);
		
		if(isConnectionActive())
			for(String key:clients.keySet()){
				if(!key.equals(targetToExclude)&&owner.equals(clients.get(key).getAccountName()))
					mainClient.printMessage(newMessage);
			}
	}
	public void updateAllClients(SyncROPItem file,String targetToExclude){		
		logger.logTrace("Updating to all clients excluding "+targetToExclude);
		if(isConnectionActive())
			for(String key:clients.keySet()){
				logger.log(key+" "+targetToExclude+" "+file.getOwner()+" "+clients.get(key).getAccountName());
				if(!key.equals(targetToExclude)&&file.getOwner().equals(clients.get(key).getAccountName()))
					if(!clients.get(key).isPathRestricted(file.getPath()))
						fileTransferManager.addToSendQueue(file,key);
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
		logger.logTrace("client restrinction"+user.getRestrictions().toString());
		logger.logTrace("client paths"+parentPaths==null?null:Arrays.asList(parentPaths).toString());
		String accountName=user.getAccountName();
		File parent=new File(ResourceManager.getMetadataDirectory(),accountName);
		
		//change standards; metadata in home"
		for(File metaDataFile:parent.listFiles())//regular vs removable
			if(metaDataFile.isDirectory())
					syncFilesToClient(message.getUserID(),user,metaDataFile,(metaDataFile.getName().equals("removable")?File.separator:""), parentPaths);
		
		this.syncedFiles.get(message.getUserID()).clear();
		logger.log("files from Cloud have been synced with "+message.getUserID()+"("+accountName+")");
	}
	
	void syncFilesToClient(final String id,final SyncropUser user,File metaDataFile,String relativePath,final String []parentPaths){
		
		if(metaDataFile.isDirectory()){
			if(user.isPathRestricted(relativePath)){
				logger.log("Path resticted for user "+user+" "+relativePath);
				return;
			}
			for(File file:metaDataFile.listFiles()){
				String newRelativePath=relativePath+(relativePath.isEmpty()||relativePath.endsWith(File.separator)?"":File.separatorChar)+file.getName();
				for(String parentDir:parentPaths)
					if(Directory.isPathContainedInDirectory(newRelativePath, parentDir)){
						syncFilesToClient(id,user,file,newRelativePath,parentPaths);
						break;
					}				
			}
		}
		else {
			SyncROPItem file=ResourceManager.readFile(metaDataFile);
			if(file==null||!file.exists()||syncedFiles.get(id).contains(file.getPath())||
					!file.isEnabled())return;
			if(file.exists()&&file.isDir()&&!file.isEmpty())return;
			if(user.isPathRestricted(file.getPath())){
				logger.log("Path resticted for user "+user+" "+relativePath);
				return;
			}
			if(file.isSymbolicLink()){
				try {
					File target=Files.readSymbolicLink(file.getFile().toPath()).toFile();
					if(target.list()!=null&&target.length()>=0)
						return;
				} catch (IOException e) {}
			}

			for(String parentDir:parentPaths)
				if(Directory.isPathContainedInDirectory(file.getPath(), parentDir))
				{
					logger.log("Local client does not have "+file);
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
		ArrayList<Object[]>keysToUpdate=new ArrayList<Object[]>();
		
		if(files==null){
			logger.logWarning("synced files were null");
			return null;
		}
		for(int i=0;i<files.length;i++)
		{
			try {
				final String path=(String)files[i][INDEX_PATH];
				if(path==null)break;
				if(syncedFiles.get(id).contains(path)){
					logger.log("path has already been added to synced paths; path="+path);
					continue;
				}
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
				Long clientDateMod=(Long)files[i][INDEX_DATE_MODIFIED];
				long clientKey=(Long)files[i][INDEX_KEY];
				boolean clientFileExists=(Boolean)(files[i][INDEX_EXISTS]);
				String linkTarget=(String)(files[i][INDEX_SYMBOLIC_LINK_TARGET]);
				boolean clientUpdatedSinceLastUpdate=(boolean)files[i][INDEX_MODIFIED_SINCE_LAST_KEY_UPDATE];
				
				boolean clientDir=clientKey==-1;
				
				
				
				
				
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
				//if both files exists and are links and point to same file
				else if(localFile!=null&&localFile.exists()&&localFile.isSymbolicLink()&&
						localFile.getTargetPath().equals(linkTarget)&&clientFileExists){}
				else if(clientDir){
					//keys do not need to be updated because dirs don't have keys
					if(localFile==null)
						if(clientFileExists)//if client file exists and local file doesn't, create dir
						{
							localFile=new SyncROPDir(path, owner,clientDateMod);
							if(localFile.isEmpty()){
								localFile.save();
								localFile.createFile(clientDateMod);
								updateAllClients(localFile,id);
							}
							else continue;//only directory needs to be sent to client
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
					if(localFile==null){
						if(clientFileExists)
							filesToAddToDownload.add(path);
						//else ; //ignore
					}
					else if(localFile.isNewerThan(clientDateMod)){//if cloud file is newer than client file
						if(localFile.exists()||clientFileExists||!localFile.isDiffrentVersionsOfSameFile(clientKey,clientUpdatedSinceLastUpdate))
							fileTransferManager.addToSendQueue(localFile,id);//send file to client
					}
					else if(localFile.isOlderThan(clientDateMod)){//if client file is newer than cloud file
						if(clientFileExists||!localFile.isDiffrentVersionsOfSameFile(clientKey,clientUpdatedSinceLastUpdate))
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
					else if(clientKey!=localFile.getKey()){//update key
						keysToUpdate.add(new Object[]{path,localFile.getKey()});
						
					}
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
		if(keysToUpdate.size()>0){
			Object [][]o=new Object[keysToUpdate.size()][];
			for(int i=0;i<keysToUpdate.size();i++)
				o[i]=keysToUpdate.get(i);
			mainClient.printMessage(o,
					FileTransferManager.HEADER_UPDATE_KEYS,id);
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
		String path=(String) message.getMessage();
		SyncropUser user=clients.get(message.getUserID());
		SharedFile file=ResourceManager.getSharedFileInfo(path);
		if(file!=null&&file.getOwner().equals(user.getAccountName())){
			try {
				Files.delete(file.getPath());
			} catch (IOException e) {
				logger.logError(e,"");
			}
		}
	}
	void createSharedFile(Message message){
		boolean sharedPublicly=message.getHeader().equals(HEADER_REQUEST_SHARE_PUBLIC);
		String []s=(String[]) message.getMessage();
		String localPath=s[0];
		SyncropUser user=clients.get(message.getUserID());
		String accountName=user.accountName;
		try {
			if(message.getHeader().equals(HEADER_REQUEST_SHARE_PUBLIC)){
				String absPath=ResourceManager.getAbsolutePath(localPath,accountName);
				File absFile=new File(absPath);
				String token=Integer.toString(absPath.hashCode(), 36)+Long.toString(System.currentTimeMillis()%(1000*3600*24*365), 36);
				SharedFile file=new SharedFile(sharedPublicly, localPath,accountName, token);
				Files.createSymbolicLink(absFile.toPath(), file.getPath());
				ResourceManager.addSharedFiles(file);
				ResourceManager.saveSharedFiles();
				mainClient.printMessage(file.toString(), HEADER_SHARED_FILE);
			}
		} catch (IOException e) {
			logger.logError(e,"");
		}
		
	}
	
	@Override
	public boolean handleResponse(Message message)
	{
		if(super.handleResponse(message))return true;
		else
			switch(message.getHeader())
			{
				case HEADER_REQUEST_SHARE_PUBLIC:
				case HEADER_REQUEST_SHARE_PRIVATE:
					createSharedFile(message);
					break;
				case HEADER_STOP_SHARING_FILE:
					stopSharingFile(message);
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
	protected void setPropperPermissions(SyncROPItem item,String filePermissions){
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