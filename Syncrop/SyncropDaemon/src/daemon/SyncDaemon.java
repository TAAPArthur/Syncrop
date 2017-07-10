package daemon;

import static notification.Notification.displayNotification;
import static settings.Settings.getMaxFileSize;
import static settings.Settings.getMaxTransferSize;
import static transferManager.FileTransferManager.HEADER_FILE_SUCCESSFULLY_UPLOADED;
import static transferManager.FileTransferManager.HEADER_REQUEST_SMALL_FILE_DOWNLOAD;
import static transferManager.FileTransferManager.HEADER_REQUEST_SYMBOLIC_LINK_DOWNLOAD;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import account.Account;
import daemon.client.SyncropClientDaemon;
import daemon.client.SyncropCommunication;
import daemon.cloud.SyncropCloud;
import file.SyncropFile;
import file.SyncropItem;
import file.SyncropSymbolicLink;
import listener.FileWatcher;
import listener.actions.RemoveSyncropConflictsAction;
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
	
	UploadLargeFileThread uploadLargeFileThread;
	
	public SyncDaemon(String instance,boolean clean) throws IOException{
		super(instance);
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
		
		try
		{
			displayNotification(APPLICATION_NAME+" started");
			init(clean);
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
		Runtime.getRuntime().addShutdownHook(
				new Thread("Shutdown-thread") {
	        public void run() 
	        {
	        	quit();
	        }
	    });
	}
	
	public void quit(){

    	Syncrop.shutdown();
		
		try {
			logger.log("received kill signal");
			//wakes all threads; they know to end since shuttingDown is true
			fileWatcher.interrupt();
			mainSocketListener.interrupt();
			fileTransferManager.onShutDown();
			uploadLargeFileThread.interrupt();
			if(SyncropClientDaemon.isConnectionActive()){
    			int timeToLive=100+getExpectedFileTransferTime()*fileTransferManager.getOutstandingFiles();
    			
    			logger.log("Allowing "+timeToLive+"s for remaining files("+fileTransferManager.getOutstandingFiles()+") to finish transferring");
    			if(timeToLive>12000){
    				timeToLive=12000;
    				logger.log("time to live truncated to"+12000);
    			}
    			//wait for threads to die;
    			Thread.sleep(timeToLive);
			}
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
			ResourceManager.deleteAllTemporaryFiles();
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
			
			displayNotification(APPLICATION_NAME+" is shutting down");
			//closes the notification; only needed for Windows
			Notification.close();
			logger.log("Shutting down");
			ResourceManager.shutDown();
			
		}
	
	}
	
	/**
	 * initializes the SyncropDaemon; This SyncropDaemon is connected to server and has its listeners
	 * and fileTransferManager started
	 */
	protected void init(boolean clean)
	{
		try 
		{						
			//TODO auto update option gui
			//Updator.checkForUpdate();
			fileWatcher.start();
			checkFiles(clean);
			connectToServer();
			startThreads();
		} catch (Exception e) {
			logger.logFatalError(e, "occured when initializing Syncrop");
		}
	}
	protected void checkFiles (boolean clean)throws IOException{
		logger.logTrace("Checking files");
		
		if(Settings.allowScripts())
			fileWatcher.loadCommandsToRunOnFileModification();
		
		fileWatcher.checkAllFiles(clean?new RemoveSyncropConflictsAction():null);
		
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
	public void setPropperPermissions(SyncropItem item){}
	public void startDownloadOfLargeFile(String id,String path,String owner, long dateModified, int key,boolean modifiedSinceLastUpdate,int filePermissions,boolean exists, long size){
		
		try {
			SyncropItem localFile=ResourceManager.getFile(path, owner);
			if(localFile!=null&&localFile.exists()&&!localFile.isDir()){
				if(!localFile.isInConflictWith(key,size, modifiedSinceLastUpdate)){
					ResourceManager.lockFile(path, owner);
					localFile.setDateModified(dateModified);
					localFile.save();
					logger.log("same version of large file: "+path);
					fileTransferManager.cancelDownload(id, path, true);
					ResourceManager.unlockFile(path, owner);
					return;
				}
			}
			File tempFile=ResourceManager.getTemporaryFile(id,path);
			tempFile.delete();
			tempFile.createNewFile();
			
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
	public void downloadLargeFile(String id,String path,String owner, long dateModified, int key,boolean modifiedSinceLastUpdate,int filePermissions,boolean exists, byte[]bytes,long size)
	{
		SyncropItem localFile=ResourceManager.getFile(path, owner);
		if(fileTransferManager.canDownloadPacket(localFile, id, path, owner, dateModified, key, bytes,size))
			try {
				if(!ResourceManager.getTemporaryFile(id,path).exists()){
					logger.log("Error not receiving begining of file first");
					fileTransferManager.cancelDownload(id,path,true);
					return;
				}
				Files.write(ResourceManager.getTemporaryFile(id,path).toPath(), bytes,StandardOpenOption.APPEND);
				if(ResourceManager.getTemporaryFile(id,path).length()>getMaxFileSize()){
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
	
	public void endDownloadOfLargeFile(String id,String path,String owner,long dateModified,int key,boolean modifiedSinceLastUpdate,int filePermissions,boolean exists,long length){
		if(ResourceManager.getTemporaryFile(id,path).exists())
			downloadFile(id, path, owner, dateModified, key,modifiedSinceLastUpdate,filePermissions, exists, null, length,null, true,true);
		else logger.log("File has already been canceled");
	}
	
	public void downloadFile(String id,String path,String owner,long dateModified,int key,boolean modifiedSinceLastUpdate,int filePermissions,boolean exists,byte[] bytes,long length,String linkTarget,boolean copyFromFile,boolean echo){
		if(exists)
			logger.log("downloading file "+path);
		else 
			logger.log("deleting file "+path);
		ResourceManager.lockFile(path,owner);
		
		SyncropItem localFile=ResourceManager.getFile(path,owner);
		SyncropItem.SyncropPostCompare result=null;
		try {
			result = SyncropItem.compare(localFile, path, owner, dateModified, key, modifiedSinceLastUpdate, filePermissions, exists, length,linkTarget,bytes);
		} catch (IOException e) {
			logger.logError(e);
		}
		
		boolean downloadNotCanceled=true;
		switch(result){
			case SYNC_METADATA:
				sendMetadata(localFile, id);
			case SYNCED:
				if(localFile.hasBeenUpdated()){
					localFile.save();
					updateAllClients(localFile, id);
				}
			case SKIP:
			default:
				fileTransferManager.cancelDownload(id, path, true);
				break;
			case DOWNLOAD_REMOTE_FILE:
				localFile=ResourceManager.getFile(path,owner);
			try {
				saveToDisk(id, localFile, bytes, copyFromFile);
				downloadNotCanceled=true;
			} catch (IOException e) {
				logger.logError(e);
			}
				break;
			case SEND_LOCAL_FILE:
				fileTransferManager.cancelDownload(id, path, true);
				fileTransferManager.addToSendQueue(path,owner, id);
				
				break;
		}
		if(downloadNotCanceled){
			if(localFile.exists()){
				logger.log("file downloaded: "+localFile);
				localFile.setFilePermissions(filePermissions);
				setPropperPermissions(localFile);
			}
				
			logger.logTrace("Setting dateMod of file to "+dateModified);
			localFile.setDateModified(dateModified);
			if(!localFile.isDir()&&exists&&localFile.getDateModified()!=dateModified)
				logger.logWarning("The modification date of file "+localFile.getFile()+" was not set correctly");			
						
			Account account=ResourceManager.getAccount(owner);
			account.setRecordedSize(account.getRecordedSize()-localFile.getLastKnownSize()+localFile.getSize());
		
			//downloading file to cloud; cloud increments key and sends new key back
			if(localFile instanceof SyncropFile){
				if(!isInstanceOfCloud())
					((SyncropFile)localFile).setKey(key);
			}
			localFile.save();
						
			logger.logTrace("Sending confirmation message");
			fileTransferManager.updateDownloadFileTransferStatistics(path);
			mainClient.printMessage(localFile.toSyncData(), HEADER_FILE_SUCCESSFULLY_UPLOADED,id);		
			if(echo&&isInstanceOfCloud())
				((SyncropCloud)(this)).updateAllClients(localFile,id);
		}
		ResourceManager.unlockFile(path, owner);
		
	}
	
	private void saveToDisk(String id,SyncropItem localFile,byte[] bytes,boolean copyFromFile) throws IOException{
		if(localFile instanceof SyncropSymbolicLink){
			localFile.createFile();
			logger.log("Sybmolic link created");
		}
		else{
			if(!localFile.exists())
				localFile.createFile();
			File file=localFile.getFile();
			
			if(copyFromFile){
				Files.copy(ResourceManager.getTemporaryFile(id,localFile.getPath()).toPath(), file.toPath(),StandardCopyOption.REPLACE_EXISTING);	
				ResourceManager.deleteTemporaryFile(id,localFile.getPath());
			}
			else Files.write(file.toPath(), bytes,
					StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE,
					StandardOpenOption.SYNC);
		}
	}
	
	
	public void updateAllClients(SyncropItem file,String targetToExclude){}
	
	
	public void sendMetadata(SyncropItem localFile, String target){
		mainClient.printMessage(
			localFile.toSyncData(null)
			,HEADER_REQUEST_SMALL_FILE_DOWNLOAD,
			target);
	}	
	
	/**
	 * tells the reciepent to download a file
	 * @param owners
	 * @param path
	 */
	public void uploadFile(String path,String owner,String target){
		uploadFile(ResourceManager.getFile(path, owner), target);
	}
	public void uploadFile(SyncropItem file,String target)
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
			mainClient.printMessage(
				file.toSyncData(null)
				,HEADER_REQUEST_SMALL_FILE_DOWNLOAD,
				target);
			return;
		}
		
		try {
			
			if(!file.exists()){
				mainClient.printMessage(
						file.toSyncData(null)
						,HEADER_REQUEST_SMALL_FILE_DOWNLOAD,
						target);
				return;
			}
			else if(file.isSymbolicLink())
				mainClient.printMessage(
						((SyncropSymbolicLink)file).toSyncData(),
						HEADER_REQUEST_SYMBOLIC_LINK_DOWNLOAD,
						target);
			else if(file.getSize()<=getMaxTransferSize())
			{
				mainClient.printMessage(
					file.toSyncData(((SyncropFile) file).readAllBytesFromFile())
					,HEADER_REQUEST_SMALL_FILE_DOWNLOAD,
					target);		
			}
			else if(file.getSize()<=getMaxFileSize())
				(uploadLargeFileThread= new UploadLargeFileThread((SyncropFile) file, target, fileTransferManager)).start();
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
	
	public void addToSendQueue(SyncropItem item){
	
		if(SyncropClientDaemon.isConnectionActive()&&item.isSyncable())
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
	/**
	 * 
	 * NOTE - if client socket is not initialized this will be turn Integer.MAX_VALUE
	 * @return the worst expected time for a file to be transferred based on the current connection
	 */
	public int getExpectedFileTransferTime(){
		return mainClient==null?Integer.MAX_VALUE:Math.max(mainClient.getExpectedRoundTripTime(),50);
	}
	public boolean isSendingLargeFile(){
		return uploadLargeFileThread!=null&&uploadLargeFileThread.isAlive();
	}
	public FileTransferManager getFileTransferManager(){return fileTransferManager;}
	public boolean isConnectionAccepted(){return mainClient!=null&&mainClient.isConnectionAccepted();}
	public void printMessage(Object m,String header){
		printMessage(m, header,null);
	}
	public void printMessage(Object m,String header,String target){
		if(mainClient!=null)
			mainClient.printMessage(m,header,target);
	}

}