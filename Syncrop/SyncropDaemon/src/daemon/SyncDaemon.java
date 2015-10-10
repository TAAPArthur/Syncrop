package daemon;

import static file.SyncROPItem.DATE_MODIFIED;
import static file.SyncROPItem.KEY;
import static file.SyncROPItem.OWNER;
import static file.SyncROPItem.PATH;
import static notification.Notification.displayNotification;
import static transferManager.FileTransferManager.HEADER_DELETE_MANY_FILES;
import static transferManager.FileTransferManager.HEADER_FILE_SUCCESSFULLY_UPLOADED;
import static transferManager.FileTransferManager.HEADER_FILE_UPLOAD_NEXT_PACKET;
import static transferManager.FileTransferManager.HEADER_REQUEST_SMALL_FILE_DOWNLOAD;
import static transferManager.FileTransferManager.HEADER_REQUEST_SYMBOLIC_LINK_DOWNLOAD;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

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
import syncrop.SyncropLogger;
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
	public static final int TRANSFER_SIZE=500*KILOBYTE;

	
	
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
	 * Used to upload large file 
	 */
	UploadLargeFileThread uploadLargeFileThread=new UploadLargeFileThread(fileTransferManager);
		
	/**
	 * Communication between SyncropDaemon and Cloud
	 */
	public static Messenger mainClient;
	
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
	
	public final static String HEADER_REQUEST_FILE_RENAME="rename file";
	
	public final static String HEADER_SYNC_FILES="sync files to cloud";
	
	/**
	 * Uploads the meta data of all files to Cloud. Cloud compares the metadata 
	 * with its files and decides how to sync the files
	 * @see #syncDirsToCloud(String, String...)
	 */
	public final static String HEADER_SYNC_GET_CLOUD_FILES="sync cloud's files";
	
	public final static String HEADER_CLEAN_CLOUD_FILES="clean cloud's files";
	
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
	    			//closes connection with server
		    		if(mainClient!=null)
		    			mainClient.closeConnection("shutting down",true);
		    		
		    		
	    			//wakes all threads; they know to end since shuttingDown is true
	    			fileWatcher.interrupt();
	    			mainSocketListener.interrupt();
	    			if(SyncropCommunication.serverSocket!=null)
	    				SyncropCommunication.serverSocket.close();
	    			long startTime=System.currentTimeMillis();
	    			//wait for threads to die;
	    			while(mainSocketListener.isAlive()||fileWatcher.isAlive())
	    			{
	    				sleepShort();
	    				if((System.currentTimeMillis()-startTime)/1000>7)
	    				{
	    					//ignoring waiting threads and proceeding with shutdown;
	    					logger.logWarning("main socket isAlive"+mainSocketListener.isAlive()+
	    							"FileWatcher isAlive:"+fileWatcher.isAlive()+
	    							"; ignoring and shutting down");
	    					break;
	    				}
	    			}
	    			ResourceManager.getTemporaryFile().delete();
    			} 
    			catch (NullPointerException e) 
    			{
    				logger.logWarning("Error when shutting down."+mainSocketListener+
    					" "+fileWatcher+" equals null");
    			}
    			catch (Exception|Error e)
    			{logger.logError(e,"occured while trying to shutdown");}
	    		finally
	    		{
	    			mainClient=null;//will cause any remaining processes to exit via exception
	    			//closes the notification; only needed for Windows
	    			displayNotification("SYNCROP is shutting down");
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
			checkFiles();
			connectToServer();
			startThreads();
		} catch (Exception e) {
			logger.logFatalError(e, "occured when initializing Syncrop");
		}
	}
	protected void checkFiles(){
		logger.logTrace("Checking files");
		
		if(Settings.allowScripts())
			fileWatcher.watch(ResourceManager.loadCommands());
		fileWatcher.start();
		fileWatcher.checkAllFiles();
		FileWatcher.checkAllMetadataFiles();
		
	}
	protected void startThreads()
	{
		logger.logTrace("Starting threads");
		uploadLargeFileThread.start();
		mainSocketListener.start();
		fileTransferManager.start();
		new NotificationManager(fileTransferManager).start();
	}
		
	
	
	/**
	 * Closes the connection of the client or specified user if run from Cloud 
	 * @param username the user to remove; can be null if not run from Cloud
	 * @param reason the reason the user should be removed
	 */
	public static void removeUser(String username,String reason)
	{
		if(isInstanceOfCloud())
			mainClient.printMessage(new String[]{username,reason},
					Message.TYPE_MESSAGE_TO_SERVER,Message.HEADER_REMOVE_USER);
		else mainClient.closeConnection(reason,true);
	}
	

	/**
	 * Sets teh Permisions on the specifed file
	 * @param item the file to set permissions for
	 */
	protected void setPropperPermissions(SyncROPItem item){return;}
		
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
	public void downloadLargeFile(String id,String path,String owner, long dateModified, long key,boolean exists, byte[]bytes,long size,boolean end)
	{
		SyncROPItem localFile=ResourceManager.getFile(path, owner);
		if(!fileTransferManager.canDownloadPacket(localFile, id, path, owner, dateModified, key, bytes,size)){
			fileTransferManager.cancelDownload(path,true, true);
			return;
		}
		fileTransferManager.updateTimeReceiving();
		try {
			if(!ResourceManager.getTemporaryFile().exists())
				ResourceManager.getTemporaryFile().createNewFile();
			Files.write(ResourceManager.getTemporaryFile().toPath(), bytes,StandardOpenOption.APPEND);
			if(ResourceManager.getTemporaryFile().length()>MAX_FILE_SIZE)
			{
				logger.log("Download failed because tempFile is too large; deleting");
				fileTransferManager.cancelDownload(path,true, true);
				removeUser(id,"Hacking attempt");
				return;
			}
			//sleepShort();
			if(logger.isLogging(SyncropLogger.LOG_LEVEL_TRACE))
				logger.log("Adding "+bytes.length+" bytes to temp file",SyncropLogger.LOG_LEVEL_TRACE);
			if(end)
				downloadFile(id,path,owner,dateModified,key,exists,bytes,size,true);
			else
				mainClient.printMessage(path,HEADER_FILE_UPLOAD_NEXT_PACKET,id);
			
				return;
			} catch (IOException e) {
				logger.logError(e, "download of large file failed.");
			}	
		
		fileTransferManager.cancelDownload(path,true, true);
	}
	public void downloadFile(String id,String path,String owner,long dateModified,long key,boolean exists,byte[] bytes,long length,boolean copyFromFile){
		downloadFile(id, path, owner, dateModified, key, exists, bytes, length,null, copyFromFile);
	}
	public void downloadFile(String id,String path,String owner,long dateModified,long key,boolean exists,byte[] bytes,long length,String linkTarget,boolean copyFromFile){
		SyncROPItem localFile=ResourceManager.getFile(path, owner);
		logger.log("downloading file "+path);
		if(!fileTransferManager.canDownloadPacket(localFile, id, path, owner, dateModified, key, bytes,length)){
			logger.logDebug("Cannot download file "+path);
			fileTransferManager.cancelDownload(path,true, true);
			return;
		}
		
		//if local file does not exists or exists and is dir and client file is a symbolic link
		if((localFile==null||localFile!=null&&localFile.isDir())&&linkTarget!=null){
			
			mainClient.printMessage(path, HEADER_FILE_SUCCESSFULLY_UPLOADED);
			fileTransferManager.receivedFile();
			return;
		}
		ResourceManager.lockFile(path,owner);
		//if client file is directory and local file is directory
		if(key==-1&&(localFile==null||localFile.getKey()==-1)){
			logger.logTrace("downloading dir info");
			if(localFile==null)
				localFile=new SyncROPDir(path,owner,dateModified);
		
			if(exists){
				if(!localFile.exists())
					localFile.createFile(dateModified);
				else localFile.setDateModified(dateModified);
			}
			else 
				localFile.delete(dateModified);
		}
		else
			try 
			{
				
				//Difference in modification date is not considered because files
				//are initially synced when connection is made
				//so any any subsequent transfer have to be newer
				//also handeles the case of trying to download a dir when local file is not a dir
				if(localFile!=null&&
						((SyncROPFile)localFile).shouldMakeConflict(dateModified,key, length, 
								linkTarget))//if conflict should be made 
				{
					logger.log("Conflict occured while trying to download file;"
							+ " local file is being renamed");
					logger.log(localFile.toString());
					
					
					((SyncROPFile) localFile).makeConflict();
					localFile.save();
					/*
					if(isInstanceOfCloud())
						((Cloud)(this)).updateAllClients(localFile,null);
					else fileTransferManager.addToSendQueue(localFile, id);
					*/
					localFile=null;
				}
				
				if(localFile==null)
					localFile=(linkTarget==null)?
							new SyncROPFile(path,owner,dateModified, key,length,false):
								new SyncROPSymbolicLink(path,owner,dateModified, key,linkTarget,length,false);
				
				if(!exists){
					localFile.delete(dateModified);
					Syncrop.sleepVeryShort();
				}
				else if(linkTarget!=null){
					localFile.createFile();
				}
				else{
					if(!localFile.exists())
						localFile.createFile();
					File file=localFile.getFile();
					
					if(copyFromFile)
					{
						if(ResourceManager.getAccount(owner).willBeFull(length))
						{
							fileTransferManager.cancelDownload(path,true, true);
								removeUser(id, "Unexpected: No space left in account");
							throw new IllegalArgumentException("No space left in account");
						}
						if(length==ResourceManager.getTemporaryFile().length())
							Files.copy(ResourceManager.getTemporaryFile().toPath(), file.toPath(),StandardCopyOption.REPLACE_EXISTING);
						else {
							logger.log("Error downloading large file:"+path+" the length of bytes " +
								"does not equal bytes obtained. "+length+" != "+ResourceManager.getTemporaryFile().length());
							return;
						}
						ResourceManager.deleteTemporaryFile();
					}
					else Files.write(file.toPath(), bytes,
							StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE,
							StandardOpenOption.SYNC);
				}
			}
			catch (FileSystemException e){
				logger.logFatalError(e, "occured while to trying to download file; Download has failed. path="+path);
				fileTransferManager.cancelDownload(path,true, true);
				return;
			}
			 catch (IOException e) {
				logger.logError(e,
						"occured while to trying to download file; Download has failed. path="+path);
				fileTransferManager.cancelDownload(path,true, true);
				return;
			}
			logger.log("file downloaded: "+localFile);
			if(localFile.exists())
				setPropperPermissions(localFile);
			localFile.setDateModified(dateModified);
			logger.logTrace("Setting dateMod of file to "+dateModified);
		
			if(localFile instanceof SyncROPFile)
				((SyncROPFile) localFile).updateKey();
			
			localFile.save();
			
			
			if(key!=-1&&exists&&localFile.getDateModified()!=dateModified)
				logger.logWarning("The modification date of file "+localFile.getFile()+" was not set correctly");
			
			mainClient.printMessage(path, HEADER_FILE_SUCCESSFULLY_UPLOADED);
			if(isInstanceOfCloud())
				((SyncropCloud)(this)).updateAllClients(localFile,id);
			logger.logTrace("Sending confirmation message");
			fileTransferManager.receivedFile();
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
	
	public String deleteManyFiles(String userId,Object[][] files){
		String owner=null;
		for(Object syncData[]:files){
			String path=(String)syncData[PATH];
			owner=(String)syncData[OWNER];
			long dateModified=(long)syncData[DATE_MODIFIED];
			long key=(long)syncData[KEY];
			downloadFile(userId, path,owner, dateModified, key, false, null, -1, false);
		}
		return owner;
	}
	/**
	 * tells the reciepent to download a file
	 * @param owners
	 * @param path
	 */
	public void uploadFile(String path,String owner,String target)
	{
		if(!isConnectionActive())
		{
			logger.log("file path="+path+" cannot be sent because connection is not active");
			if(logger.isDebugging())
				logger.log("mainclient="+mainClient+" connenected="+
			(mainClient==null?null:mainClient.isConnectionAccepted())+
			" init connection="+initializingConnection);
			return;
		}
		
		SyncROPItem file=ResourceManager.getFile(path, owner);
		
		logger.log("uploading "+file);
		
		if(file==null)
		{
			logger.log("upload failed; file not found; file:"+file+" owner"+owner);
			
			//upload failed
			fileTransferManager.cancelUpload(path,true, true);
			return;
		}
		else if(!file.isEnabled()){
			logger.log("upload failed; file is not enabled; file:"+file+" owner"+owner);
			//upload failed
			fileTransferManager.cancelUpload(path,true, true);
			return;
		}
		/*
		else if(!file.exists()){
			//fileTransferManager.cancelUpload(path,true, true);
			mainClient.printMessage(file.getPath(),HEADER_ADD_TO_SEND_QUEUE,target);
			return;
		}*/
		//a dir is trying to be uploaded which means there is a file on the client with the same name that is not synced
		else if(file.isDir()){
			if(!((SyncROPDir) file).isEmpty()){
				logger.logTrace("upload failed; file is a non empty dir; file:"+file+" owner"+owner);
				file.remove();
				//upload failed
				fileTransferManager.cancelUpload(path,true, true);
			}
			else 
				mainClient.printMessage(
						file.formatFileIntoSyncData(null)
						,HEADER_REQUEST_SMALL_FILE_DOWNLOAD,
						target);
			return;
		}
		
		try {
			fileTransferManager.storeFileSentData(file);
			
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
			else if(file.getSize()<MAX_FILE_SIZE)
				uploadLargeFileThread.startUploadingOfFile((SyncROPFile) file, path, target);
			else
			{
				logger.log("Files is too big to upload; path="+path);
				fileTransferManager.cancelUpload(path,true, true);
			}
		}
		catch (Exception e)
		{
			fileTransferManager.cancelUpload(path,true, true);
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
		else if(message.getHeader().contains("download"))
			fileTransferManager.downloadRequest(message);
		else if(message.getHeader().equals(HEADER_DELETE_MANY_FILES))
			fileTransferManager.deleteManyRequest(message);
		/*else if(message.getHeader().equals(HEADER_REQUEST_FILE_RENAME)){
			tryToRenameFile();
		}*/		
		else return false;
		return true;
	}
	public FileTransferManager getFileTransferManager(){return fileTransferManager;}
	public boolean isUploadingLargeFile(){return uploadLargeFileThread.isUploadingLargeFile();}
	
	public void interruptUploadingLargeFile(){uploadLargeFileThread.interrupt();}
}