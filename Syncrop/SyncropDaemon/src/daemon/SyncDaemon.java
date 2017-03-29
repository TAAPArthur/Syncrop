package daemon;

import static notification.Notification.displayNotification;
import static transferManager.FileTransferManager.HEADER_FILE_SUCCESSFULLY_UPLOADED;
import static transferManager.FileTransferManager.HEADER_REQUEST_SMALL_FILE_DOWNLOAD;
import static transferManager.FileTransferManager.HEADER_REQUEST_SYMBOLIC_LINK_DOWNLOAD;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import daemon.client.SyncropClientDaemon;
import daemon.client.SyncropCommunication;
import daemon.cloud.SyncropCloud;
import daemon.cloud.SyncropUser;
import file.SyncROPDir;
import file.SyncROPFile;
import file.SyncROPItem;
import file.SyncROPSymbolicLink;
import listener.FileWatcher;
import message.Message;
import message.Messenger;
import notification.Notification;
import notification.NotificationManager;
import settings.Settings;
import syncrop.ResourceManager;
import syncrop.Syncrop;
import transferManager.FileTransferManager;

public abstract class SyncDaemon extends Syncrop{
	
	protected final String CLOUD_USERNAME="SYNCROP_Cloud";	
	/**
	 * When false SyncropDaemon will act as if it is not connected to Cloud until 
	 * the connection has finished initializing 
	 */
	protected static boolean initializingConnection=false;
	
	/**
	 * the maximum package size of a file being transfered. If the file size is less
	 * than this value, {@value #TRANSFER_SIZE}, the entire file will be sent at once.
	 */
	public static final int TRANSFER_SIZE=32*KILOBYTE;

	/**
	 * Checks to see if files have been modified every few seconds; The exact time
	 * can vary based on preferences
	 */
	FileWatcher fileWatcher=new FileWatcher(this);
	/**
	 * Handles how files will be transfered
	 */
	public final FileTransferManager fileTransferManager=new FileTransferManager(this);
	
	/**
	 * Handles connection between Client and Server
	 */
	MainSocketListener mainSocketListener=new MainSocketListener(this);
			
	/**
	 * Communication between SyncropDaemon and Cloud
	 */
	protected static Messenger mainClient;
	
	/**
	 * The name of the application
	 */
	protected static final String application="SYNCROP";
	
	/**
	 * request recipient to handle authentication. This header should be sent with an object
	 * array contain the name, email and token of the Account to be authenticated.
	 */
	public final static String HEADER_AUTHENTICATION="have cloud AUTHENTICATE the user";
	public final static String HEADER_AUTHENTICATION_RESPONSE="the AUTHENTICATED accounts";
	
	public final static String HEADER_USER_SETTINGS_CONFLICT_RESOLUTION="CONFLICT_RESOLUTION";
	public final static String HEADER_USER_SETTINGS_DELETING_FILES_NOT_ON_CLIENT="DELETING_FILES_NOT_ON_CLIENT";
	
	public final static String HEADER_REQUEST_FILE_RENAME="rename file";
	
	public final static String HEADER_SYNC_FILES="sync files to cloud";
	
	/**
	 * Uploads the meta data of all files to Cloud. Cloud compares the metadata 
	 * with its files and decides how to sync the files
	 * @see #syncDirsToCloud(String, String...)
	 */
	public final static String HEADER_SYNC_GET_CLOUD_FILES="sync cloud's files";
	public final static String HEADER_SET_ENABLED_PATHS="set enabled paths";
	
	public final static String HEADER_CLEAN_CLOUD_FILES="clean cloud's files";
	/**
	 * parameter should be a string containing the relative path of the file to share
	 */
	public final static String HEADER_SHARE_FILE="SHARE";
	/**
	 * parameter should be a string containing the relative path of the file to share
	 */
	public final static String HEADER_SHARED_SUCCESS="SHARE KEY";
	public final static String HEADER_REQUEST_ACCESS_TO_SHARED_FILE="REQUEST SHARED FILE";
	public final static String HEADER_REQUEST_ACCESS_TO_SHARED_FILE_RESPONSE="REQUEST SHARED FILE RESPONSE";
	public final static String HEADER_STOP_SHARING_FILE="STOP SHARING FILE";
	
	UploadLargeFileThread uploadLargeFileThread;
	
	public SyncDaemon(String instance) throws IOException{
		super(instance);
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
		addShutdownHook();
		try
		{
			displayNotification("Syncrop started");
			init();
		} 
		catch (Exception|Error e) {
			logger.logFatalError(e,"occured when starting Syncrop");
			System.exit(0);
		}
	}


	/**
	 * 
	 */
	protected abstract void connectToServer();
	

	
	/**
	 * creates the shutdown hook that will safely kill Syncrop when asked to shutdown or
	 * when an unexpected error occurred.
	 */
	protected void addShutdownHook()
	{
		logger.logTrace("Creating shutdown hook");
		Runtime.getRuntime().addShutdownHook(
				new Thread("Shutdown-thread") {
	        public void run() 
	        {
	        	shutdown();
	        }
	        public void shutdown()
	    	{
	        	Syncrop.shutdown();
	    		
	    		try {
	    			logger.log("received kill signal");
	    			//wakes all threads; they know to end since shuttingDown is true
	    			fileWatcher.interrupt();
	    			mainSocketListener.interrupt();
	    			fileTransferManager.shutDown();
	    			uploadLargeFileThread.interrupt();
	    			int timeToLive=100+getExptectedFileTransferTime()*fileTransferManager.getOutstandingFiles();
	    			
	    			logger.log("Allowing "+timeToLive+"s for remaining files("+fileTransferManager.getOutstandingFiles()+") to finish transferring");
	    			if(timeToLive>12000){
	    				timeToLive=12000;
	    				logger.log("time to live truncated to"+12000);
	    			}
	    			//wait for threads to die;
	    			Thread.sleep(timeToLive);
    				if(mainSocketListener.isAlive())
    				{
    					//ignoring waiting threads and proceeding with shutdown;
    					logger.logWarning("main socket isAlive"+mainSocketListener.isAlive()+
    							"FileWatcher isAlive:"+fileWatcher.isAlive()+
    							"; ignoring and shutting down; "+
    							fileTransferManager.getOutstandingFiles()+" files left outstanding");
    				}
	    			
	    			if(SyncropCommunication.serverSocket!=null)
	    				SyncropCommunication.serverSocket.close();
	    			ResourceManager.deleteTemporaryFile(null);
    			} 
    			catch (NullPointerException e) 
    			{
    				logger.logWarning("Error when shutting down."+mainSocketListener+
    					" "+fileWatcher+" equals null");
    			}
    			catch (Exception|Error e)
    			{
    				logger.logError(e,"occured while trying to shutdown");
    			}
	    		finally
	    		{
	    			if(mainClient!=null){
	    				mainClient.closeConnection("Shutting down", true);
	    				mainClient=null;//will cause any remaining processes to exit via exception
	    				if(mainSocketListener.isAlive())
	    					mainSocketListener.interrupt();
	    			}
	    			
	    			displayNotification("SYNCROP is shutting down");
	    			//closes the notification; only needed for Windows
	    			Notification.close();
	    			logger.log("Shutting down");
	    			ResourceManager.shutDown();
	    			
	    		}
	    	}
	    });
	}
	
	/**
	 * initializes the SyncropDaemon; This SyncropDaemon is connected to server and has its listeners
	 * and fileTransferManager started
	 */
	protected void init()
	{
		try 
		{			
			if(!isNotMac()||!isNotWindows())
				SyncROPItem.initializeFileUtils();
			
			//TODO auto update option gui
			//Updator.checkForUpdate();
			fileWatcher.start();
			checkFiles();
			connectToServer();
			startThreads();
		} catch (Exception e) {
			logger.logFatalError(e, "occured when initializing Syncrop");
		}
	}
	protected void checkFiles ()throws IOException{
		logger.logTrace("Checking files");
		
		if(Settings.allowScripts())
			fileWatcher.watch(ResourceManager.loadCommands());
		
		fileWatcher.checkAllFiles();
		
		System.gc();
		
	}
	protected void startThreads()
	{
		logger.logTrace("Starting threads");
		
		mainSocketListener.start();
		fileTransferManager.start();
		new NotificationManager(fileTransferManager).start();
	}
		
	
	
	/**
	 * Closes the connection of the client or specified user if run from Cloud 
	 * @param username the user to remove; can be null if not run from Cloud
	 * @param reason the reason the user should be removed
	 */
	public void removeUser(String username,String reason)
	{
		if(isInstanceOfCloud()){
			mainClient.printMessage(new String[]{username,reason},
					Message.TYPE_MESSAGE_TO_SERVER,Message.HEADER_REMOVE_USER);
		}
		else mainClient.closeConnection(reason,true);
	}
	

	/**
	 * Sets teh Permisions on the specifed file
	 * @param item the file to set permissions for
	 */
	public void setPropperPermissions(SyncROPItem item,String filePermissions){
		try {
			Files.setPosixFilePermissions(item.getFile().toPath(),
					SyncROPItem.getPosixFilePermissions(filePermissions));
		}catch (SecurityException | IOException e) {
				logger.logError(e, " occured while trying to change the write/execute permissions of "+item.getAbsPath());
			}
	}
	public void startDownloadOfLargeFile(String id,String path,String owner, long dateModified, long key,boolean modifiedSinceLastUpdate,String filePermissions,boolean exists, byte[]bytes,long size){
		
		try {
			SyncROPItem localFile=ResourceManager.getFile(path, owner);
			if(localFile!=null&&localFile.exists()){
				byte[]b=FileTransferManager.getFileHash(localFile.getFile());
				if(b==bytes){//same file
					fileTransferManager.cancelDownload(id, path, true);
					return;
				}
			}
			File tempFile=ResourceManager.getTemporaryFile(id);
			tempFile.delete();
			tempFile.createNewFile();
			System.out.println(tempFile+""+tempFile.exists());
			
		} catch (IOException e) {
			logger.logError(e, "download of large file failed.");
		}
	}
	/**
	 * 
	 * @param sender 
	 * @param path
	 * @param owners the verified owners 
	 * @param dateModified the modification date of the file
	 * @param key the key of the file
	 * @param bytes the bytes to download
	 * @param size the size of the file
	 * @param end if these are the last bytes to download
	 */
	public void downloadLargeFile(String id,String path,String owner, long dateModified, long key,boolean modifiedSinceLastUpdate,String filePermissions,boolean exists, byte[]bytes,long size)
	{
		SyncROPItem localFile=ResourceManager.getFile(path, owner);
		if(fileTransferManager.canDownloadPacket(localFile, id, path, owner, dateModified, key, bytes,size))
			
			try {
				if(!ResourceManager.getTemporaryFile(id).exists()){
					logger.log("Error not receiving begining of file first");
					fileTransferManager.cancelDownload(id,path,true);
					return;
				}
				Files.write(ResourceManager.getTemporaryFile(id).toPath(), bytes,StandardOpenOption.APPEND);
				if(ResourceManager.getTemporaryFile(id).length()>MAX_FILE_SIZE){
					logger.log("Download failed because tempFile is too large; deleting");
					fileTransferManager.cancelDownload(id,path,true);
					removeUser(id,"Hacking attempt");
					return;
				}
				//sleepShort();
				logger.logAll("Adding "+bytes.length+" bytes to temp file");
			
				return;
			} catch (IOException e) {
				logger.logError(e, "download of large file failed.");
			}			
		fileTransferManager.cancelDownload(id,path,true);
	}
	public void downloadFile(String id,String path,String owner,long dateModified,long key,boolean modifiedSinceLastUpdate,String filePermissions,boolean exists,byte[] bytes,long length,boolean copyFromFile){
		downloadFile(id, path, owner, dateModified, key,modifiedSinceLastUpdate,filePermissions, exists, bytes, length,null, copyFromFile,true);
	}
	public void downloadFile(String id,String path,String owner,long dateModified,long key,boolean modifiedSinceLastUpdate,String filePermissions,boolean exists,byte[] bytes,long length,boolean copyFromFile,boolean echo){
		downloadFile(id, path, owner, dateModified, key,modifiedSinceLastUpdate,filePermissions, exists, bytes, length,null, copyFromFile,echo);
	}
	public void downloadFile(String id,String path,String owner,long dateModified,long key,boolean modifiedSinceLastUpdate,String filePermissions,boolean exists,byte[] bytes,long length,String linkTarget,boolean copyFromFile,boolean echo){
		SyncROPItem localFile=ResourceManager.getFile(path, owner);
		if(exists)
			logger.log("downloading file "+path);
		if(!fileTransferManager.canDownloadPacket(localFile, id, path, owner, dateModified, key, bytes,length)){
			logger.logDebug("Cannot download file "+path);
			fileTransferManager.cancelDownload(id,path,true);
			return;
		}
		
		//if local file does not exists or exists and is dir and client file is a symbolic link
		if((localFile==null&&new File(ResourceManager.getAbsolutePath(path, owner)).isDirectory()
				||localFile!=null&&localFile.isDir())&&linkTarget!=null){
			
			mainClient.printMessage(new Object[]{path,key}, HEADER_FILE_SUCCESSFULLY_UPLOADED);
			fileTransferManager.updateDownloadFileTransferStatistics(path);
			return;
		}
		ResourceManager.lockFile(path,owner);
		
		//if client file is directory and local file is directory
		if(key==-1&&(localFile==null||localFile instanceof SyncROPDir)){
			logger.logTrace("downloading dir info");
			if(localFile==null)
				localFile=new SyncROPDir(path,owner,dateModified);
		
			if(exists){
				if(!localFile.exists())
					localFile.createFile(dateModified);
				else localFile.setDateModified(dateModified);
			}
			else if(localFile.isEmpty()) 
				localFile.delete(dateModified);
			else fileTransferManager.addToSendQueue(localFile, id);
		}
		else
			DOWNLOAD_FILE:try 
			{
				
				//Difference in modification date is not considered because files
				//are initially synced when connection is made
				//so any any subsequent transfer have to be newer
				//also handeles the case of trying to download a dir when local file is not a dir
				//if(localFile instanceof SyncROPDir && exists)
				//TODO FIX conflicts
				if(localFile!=null)
					if(localFile instanceof SyncROPDir){
						fileTransferManager.cancelDownload(id,path,true);
						fileTransferManager.addToSendQueue(localFile,id);
						break DOWNLOAD_FILE;
					}
					else if(key==-1){//client file is directory
						((SyncROPFile) localFile).makeConflict();
						addToSendQueue(localFile);
					}
					else if (((SyncROPFile)localFile).shouldMakeConflict(dateModified,key,modifiedSinceLastUpdate, length, 
								linkTarget))//if conflict should be made
					if(allowConflicts(id)&&localFile.exists()){
						boolean areFilesTheSame=false;
						if(copyFromFile){
							
							byte[]b=FileTransferManager.getHash(bytes);
						
							if(b!=null&&b.equals(FileTransferManager.getFileHash(ResourceManager.getTemporaryFile(id))))
								areFilesTheSame=true;
						}
						else {
							if(bytes.equals(FileTransferManager.getFileHash(ResourceManager.getTemporaryFile(id))))
								areFilesTheSame=true;
						}
						
						if(areFilesTheSame){
							logger.log(localFile.getPath()+"  is out of sync but both versions are the same; updating modification date to match sent file");
							//sync the modification date of the files
							localFile.setDateModified(dateModified);
							localFile.save();
							//TODO move to own method
							break DOWNLOAD_FILE;
						}
						logger.log("Conflict occured while trying to download file;"
								+ " local file is being renamed");
						logger.log(localFile.toString());
						((SyncROPFile) localFile).makeConflict();
						addToSendQueue(localFile);
						localFile=null;
					}
					
				if(localFile==null)
					localFile=(linkTarget==null)?
							new SyncROPFile(path,owner,dateModified, key,false,length,""):
								new SyncROPSymbolicLink(path,owner,dateModified, key,false,linkTarget,length,"");
	
				if(!exists){
					localFile.delete(dateModified);			
					Syncrop.sleepVeryShort();
				}
				else if(linkTarget!=null){
					localFile.createFile();
					logger.log("Sybmolic link created");
				}
				else{
					if(!localFile.exists())
						localFile.createFile();
					File file=localFile.getFile();
					
					if(copyFromFile)
					{
						if(ResourceManager.getAccount(owner).willBeFull(length)){
							fileTransferManager.cancelDownload(id,path,true);
								removeUser(id, "Unexpected: No space left in account");
							throw new IllegalArgumentException("No space left in account");
						}
						if(length==ResourceManager.getTemporaryFile(id).length())
							Files.copy(ResourceManager.getTemporaryFile(id).toPath(), file.toPath(),StandardCopyOption.REPLACE_EXISTING);
						else {
							logger.log("Error downloading large file:"+path+" the length of bytes " +
								"does not equal bytes obtained. "+length+" != "+ResourceManager.getTemporaryFile(id).length());
							return;
						}
						ResourceManager.deleteTemporaryFile(id);
					}
					else Files.write(file.toPath(), bytes,
							StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE,
							StandardOpenOption.SYNC);
				}
			}
			catch (SecurityException|IOException e){
				logger.logFatalError(e, "occured while to trying to download file; Download has failed. path="+path);
				ResourceManager.unlockFile(path,owner);
				fileTransferManager.cancelDownload(id,path,true);
				return;
			}
			
			
			if(localFile.exists()){
				logger.log("file downloaded: "+localFile);
				setPropperPermissions(localFile,filePermissions);
			}
				
			
			localFile.setDateModified(dateModified);
			
			logger.logTrace("Setting dateMod of file to "+dateModified);
		
			//downloading file to cloud; cloud increments key and sends new key back
			
			
			if(localFile instanceof SyncROPFile){
				//long newKey=isInstanceOfCloud()?Math.max(localFile.getKey(), key)+1:key;
				if(isInstanceOfCloud())
					((SyncROPFile)localFile).updateKey();
				else ((SyncROPFile)localFile).setKey(key);
			}
			
			localFile.save();
			
			
			if(key!=-1&&exists&&localFile.getDateModified()!=dateModified)
				logger.logWarning("The modification date of file "+localFile.getFile()+" was not set correctly");
				
			logger.logTrace("Sending confirmation message");
			fileTransferManager.updateDownloadFileTransferStatistics(path);
			mainClient.printMessage(localFile.formatFileIntoSyncData(), HEADER_FILE_SUCCESSFULLY_UPLOADED,id);
					
			if(echo&&isInstanceOfCloud())
				((SyncropCloud)(this)).updateAllClients(localFile,id);
			
			ResourceManager.unlockFile(path,owner);
			//sleepShort();
		//}
		/*else 
		{
			AccountManager.log("file failed to be downloaded "+path+
				"\nlocal file is older: local file-"+(localFile==null?"":localFile.getDateModified())+" cloud file-"+modificationDate);
			fileTransferManager.cancelDownload(true, true);
		}*/
	}
	
	public boolean allowConflicts(String id){
		return Settings.isConflictsAllowed();
	}
	public boolean isLocalFileNewerVersion(String id,SyncROPItem localFile,long dateMod,long key,boolean modifiedSinceLastUpdate,String targetOfLink){
		
		if(localFile==null)return false;
		//TODO file specific settigns
		if(Settings.getConflictResolution()==Settings.LOCAL_FILE_ALWAYS_WINS)return true;
		if(Settings.getConflictResolution()==Settings.LOCAL_FILE_ALWAYS_LOSES)return false;
		if(isInstanceOfCloud()){
			SyncropUser user=SyncropCloud.getSyncropUser(id);
			if(user.getConflictResolution()==Settings.LOCAL_FILE_ALWAYS_WINS)return false;
			if(user.getConflictResolution()==Settings.LOCAL_FILE_ALWAYS_LOSES)return true;
		}		
		if(!localFile.isEmpty()&&key!=-1)//localFile is non-empty dir and client file is file 
			return true;
		return localFile.isNewerThan(dateMod);
	}
	
	public boolean isLocalFileOlderVersion(String id,SyncROPItem localFile,long dateMod,long key,boolean modifiedSinceLastUpdate,String targetOfLink){
		
		if(localFile==null)return true;
		//TODO file specific settigns
		if(Settings.getConflictResolution()==Settings.LOCAL_FILE_ALWAYS_WINS)return false;
		if(Settings.getConflictResolution()==Settings.LOCAL_FILE_ALWAYS_LOSES)return true;
		
		if(isInstanceOfCloud()){
			SyncropUser user=SyncropCloud.getSyncropUser(id);
			if(user.getConflictResolution()==Settings.LOCAL_FILE_ALWAYS_WINS)return true;
			if(user.getConflictResolution()==Settings.LOCAL_FILE_ALWAYS_LOSES)return false;
		}
		
		
		if(!localFile.isEmpty()&&key!=-1)//localFile is non-empty dir and client file is file
			return false;
		return localFile.isOlderThan(dateMod);
	}
		
	
	/**
	 * tells the reciepent to download a file
	 * @param owners
	 * @param path
	 */
	public void uploadFile(String path,String owner,String target){
		uploadFile(ResourceManager.getFile(path, owner), target);
	}
	public void uploadFile(SyncROPItem file,String target)
	{
		logger.log("uploading "+file);
		String path=file.getPath();
		if(!isConnectionActive())
		{
			logger.log("file path="+path+" cannot be sent because connection is not active");
			if(logger.isDebugging())
				logger.log("mainclient="+mainClient+" connenected="+
			(mainClient==null?null:mainClient.isConnectionAccepted())+
			" init connection="+initializingConnection);
			return;
		}
		if(!file.isEnabled()){
			logger.log("upload failed; file is not enabled; file:"+file);
			return;
		}
		
		//a dir is trying to be uploaded which means there is a file on the client with the same name that is not synced
		else if(file.isDir()){
			if(!((SyncROPDir) file).isEmpty()){
				logger.logTrace("upload failed; file is a non empty dir; file:"+file);
				file.deleteMetadata();
			}
			else 
				mainClient.printMessage(
					file.formatFileIntoSyncData(null)
					,HEADER_REQUEST_SMALL_FILE_DOWNLOAD,
					target);
			return;
		}
		
		try {
			
			if(!file.exists()){
				mainClient.printMessage(
						file.formatFileIntoSyncData(null)
						,HEADER_REQUEST_SMALL_FILE_DOWNLOAD,
						target);
				return;
			}
			else if(file.isSymbolicLink())
				mainClient.printMessage(
						((SyncROPSymbolicLink)file).formatFileIntoSyncData(),
						HEADER_REQUEST_SYMBOLIC_LINK_DOWNLOAD,
						target);
			else if(file.getSize()<=TRANSFER_SIZE)
			{
				mainClient.printMessage(
					file.formatFileIntoSyncData(((SyncROPFile) file).readAllBytesFromFile())
					,HEADER_REQUEST_SMALL_FILE_DOWNLOAD,
					target);		
			}
			else if(file.getSize()<=MAX_FILE_SIZE)
				(uploadLargeFileThread= new UploadLargeFileThread((SyncROPFile) file, target, fileTransferManager)).start();
			else
				logger.log("Files is too big to upload; path="+path);
		}
		catch (Exception e)
		{
			logger.logFatalError(e,"occured while trying to upload file="+file);
		}
	}
		
	
//		
	/**
	 * 
	 * @return true if connected to Cloud and has finished initializing the connection
	 */
	public static boolean isConnectionActive()
	{
		return mainClient!=null&&mainClient.isConnectionAccepted()&&!initializingConnection;
	}
	
	public void addToSendQueue(SyncROPItem item){
	
		if(SyncropClientDaemon.isInstanceOfCloud())
			((SyncropCloud)(this)).updateAllClients(item, null);
		else fileTransferManager.addToSendQueue(item, CLOUD_USERNAME);
	}
		
	public static void closeConnection(String reason,boolean localClose){
		if(mainClient!=null)
			mainClient.closeConnection(reason, localClose);
	}
		
	public abstract boolean verifyUser(String id,String accountName);
	
	protected boolean handleResponse(Message message){
		if(message.getHeader().contains("queue"))
			fileTransferManager.addToQueueRequest(message);
		
		else if(message.getHeader().contains("upload"))
			fileTransferManager.uploadRequest(message);
		else if(!Syncrop.isShuttingDown()) 
			if(message.getHeader().contains("download"))
				fileTransferManager.downloadRequest(message);
		/*else if(message.getHeader().equals(HEADER_REQUEST_FILE_RENAME)){
			tryToRenameFile();
		}*/		
		else return false;
		return true;
	}
	public int getExptectedFileTransferTime(){
		return Math.max(mainClient.getTimeout(),50);
	}
	public boolean isSendingLargeFile(){
		return uploadLargeFileThread!=null&&uploadLargeFileThread.isAlive();
	}
	public FileTransferManager getFileTransferManager(){return fileTransferManager;}
	public boolean isConnectionAccepted(){return mainClient==null||mainClient.isConnectionAccepted();}
	public void printMessage(Object m,String header){
		printMessage(m, header,null);
	}
	public void printMessage(Object m,String header,String target){
		if(mainClient!=null)
			mainClient.printMessage(m,header,target);
	}
}