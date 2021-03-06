package transferManager;


import static file.SyncropItem.INDEX_BYTES;
import static file.SyncropItem.INDEX_DATE_MODIFIED;
import static file.SyncropItem.INDEX_EXISTS;
import static file.SyncropItem.INDEX_FILE_PERMISSIONS;
import static file.SyncropItem.INDEX_KEY;
import static file.SyncropItem.INDEX_MODIFIED_SINCE_LAST_KEY_UPDATE;
import static file.SyncropItem.INDEX_OWNER;
import static file.SyncropItem.INDEX_PATH;
import static file.SyncropItem.INDEX_SIZE;
import static file.SyncropItem.INDEX_SYMBOLIC_LINK_TARGET;
import static syncrop.ResourceManager.getFile;
import static syncrop.Syncrop.isNotWindows;
import static syncrop.Syncrop.logger;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedHashSet;

import daemon.SyncDaemon;
import daemon.client.SyncropClientDaemon;
import daemon.cloud.SyncropCloud;
import file.SyncropFile;
import file.SyncropItem;
import message.Message;
import settings.Settings;
import syncrop.ResourceManager;
import syncrop.Syncrop;
import transferManager.queue.QueueMember;
import transferManager.queue.SendQueue;
/**
 * This class has a queue for sent and receive files. File transfer requests will be
 * sent in the order in which they appear. Only file can be transfered at a time and 
 * the receive and send queues alternate: send, then receive, then send...
 * @author taaparthur
 *
 */
public class FileTransferManager extends Thread{
	

	/**
	 * Used to add a file to the recipient send queue; Should only be used 
	 * in Cloud 
	 */
	public final static String HEADER_ADD_TO_SEND_QUEUE="add to send queue";
	/**
	 * Used to add many files to the recipient send queue; Should only be used 
	 * in Cloud 
	 */
	public final static String HEADER_ADD_MANY_TO_SEND_QUEUE="add many to send queue";
	
	
	/**
	 * This String is used as a header for a {@link Message}
	 * to indicate that that a file was uploaded successfully. 
	 * Upon receiving this message, {@link FileTransferManager#sentFile()} should be called.<br/>
	 * This message that should be sent should contain a Sting with the path of file 
	 * that was successfully uploaded 
	 * <br/>
	 * <b>Note that if the recipient is not expecting a file with the designated path, the 
	 * notification will fail.</b> To upload properly, use one of {@link FileTransferManager}'s 
	 * addToSendQueue methods like
	 *  {@link FileTransferManager#addToSendQueue(String, String, String...)}
	 * @see Message
	 */
	public final static String HEADER_FILE_SUCCESSFULLY_UPLOADED="file uploaded success";
	
	
	
	/**
	 * This String is used as a header for a {@link Message}
	 * to tell the recipient to cancel the upload and re-add the current file to the send queue.
	 * This message indicates that there was a problem uploading the file 
	 * that was not directly related to the file itself.
	 * <br/>
	 * This message that should be sent should contain a Sting with the path of file. 
	 * <br/>
	 * <b>Note that if the recipient is not expecting a file with the designated path, the 
	 * notification will fail.</b> To upload properly, use one of {@link FileTransferManager}'s 
	 * addToSendQueue methods like
	 *  {@link FileTransferManager#addToSendQueue(String, String, String...)}
	 * @see Message
	 */
	public final static String HEADER_CANCEL_UPLOAD="cancel upload";
	/**
	 *   
	 * 
	 */
	
	/**
	 * tells recipient to cancel a download
	 */
	public final static String HEADER_CANCEL_DOWNLOAD=" cancel download";
	
	
	
	/**
	 * Requests recipient to download a small file. A small fire is a file whose size is 
	 * less than or equal to the transfer size, {@value #transferSize}. <br/>
	 * The message that should accompany this header is defined by 
	 * {@link SyncropItem#toSyncData(byte[])}
	 */
	public final static String HEADER_REQUEST_SMALL_FILE_DOWNLOAD="request small file download";
	/**
	 * Requests recipient to download a small file. A small fire is a file whose size is 
	 * greater than the transfer size, {@value #transferSize}. <br/>
	 * The message that should accompany this header is defined by 
	 * {@link SyncropItem#toSyncData(byte[])}
	 */
	public final static String HEADER_REQUEST_LARGE_FILE_DOWNLOAD="request large file download";
	
	public final static String HEADER_REQUEST_LARGE_FILE_DOWNLOAD_START="request to start large file download";
	
	/**
	 * tells the recipient that the a large download file has completed and that the 
	 * temp file should be copied to the real file<br/>
	 * The message that should accompany this header is defined by 
	 * {@link #formatFileIntoSyncData(SyncropFile, byte[])}
	 */
	public final static String HEADER_REQUEST_END_LARGE_FILE_DOWNLOAD="end large file download";
	
	
	/**
	 * This String is used as a header for a Message requesting
	 * the recipient to upload a specified file<br/>
	 * This message that should be sent should be a Sting[] containing 
	 * <li>The linux path of the file
	 * <li>The owner of the file</li>
	 * <b>Note that if the recipient is not expecting a file with the designated path, the 
	 * upload will fail.</b> To upload properly, use one of {@link FileTransferManager}'s 
	 * addToSendQueue methods like
	 *  {@link FileTransferManager#addToSendQueue(String, String, String...)}
	 * @see Message
	 */
	public final static String HEADER_REQUEST_FILE_UPLOAD="request file upload";

	
	final LinkedHashSet<String>downloadedFiles=new LinkedHashSet<>();
	final LinkedHashSet<String>uploadedFiles=new LinkedHashSet<>();
	volatile boolean keepRecord=true;
	/**
	 * The name of the first file uploaded/downloaded since the last save
	 */
	private String nameOfDownloadFile,nameOfUploadedFile;
	
	private volatile long timeOfLastCompletedFileTransfer;
	/**
	 * a record of files to send with a 2D array of targets to include(0) and targets
	 * to excluded
	 * 
	 *  gareenteed to have a non null value that has a size that is greater than 0
	 */
	private volatile SendQueue sendQueue=new SendQueue();
	
	private volatile int outStandingFiles=0;
	private long timeLastFileWasSent;
	private HashMap<String, String>pathsOfLargeFilesBeingSent=null;
	private String pathOfLargeFilesBeingSent=null;
	
	
	/**
	 * if a file is being received. No two files will be received simultaneously 
	 */
	//private volatile boolean receiving=false;
	//private volatile String userSendingTo,userReceivingFrom;
	//private volatile String fileSending, fileReceiveing;
	//private volatile long fileSendingDate;
	//private String fileSendingOwner;
	
	
	private volatile boolean paused=false;
	
	private final SyncDaemon daemon;
	
	/**
	 * Returns a reference to an instance of SyncropDaemon or SyncropCloud
	 * @return a refrence to the running daemon
	 */
	public SyncDaemon getDaemon(){return daemon;} 
		
	public FileTransferManager(SyncDaemon daemon)
	{
		super("file transfer manager");
		this.daemon=daemon;
		if(Syncrop.isInstanceOfCloud())
			pathsOfLargeFilesBeingSent=new HashMap<>();
		
	}
	/**
	 * Will be called when Daemon is shutting down;
	 * Cleans up transfer manager
	 */
	public void quit(){
		unsetLargeFileTransferInfo(null);
		sendQueue.clear();
		this.interrupt();
	}
	
	/**
	 * Resets this FTM to default settings. The queues are cleared and it is 
	 * not sending nor receiving. The temporary file is also deleted if it exists
	 */
	public void reset()
	{
		logger.log("File transfer manager reseting");
		ResourceManager.deleteAllTemporaryFiles();
		if(Syncrop.isInstanceOfCloud())
			pathsOfLargeFilesBeingSent.clear();
		else pathOfLargeFilesBeingSent=null;
		
		sendQueue.clear();
		
		timeOfLastCompletedFileTransfer=0;
		outStandingFiles=0;
		resetTransferRecord();
	}
	/**
	 * Resets the count of files being upload/downloaded respectively.
	 *  These values are used for notifications
	 */
	public void resetTransferRecord(){
		logger.log("Reseting transfer record");
		uploadedFiles.clear();
		downloadedFiles.clear();
		timeOfLastCompletedFileTransfer=0;
		nameOfUploadedFile=nameOfDownloadFile=null;
	}
	
	
	/**
	 * receive 
	 * @return true if both the sendQueue and the receiveQueue are empty
	 */
	public boolean isEmpty()
	{
		return sendQueue.isEmpty();
	}
	public void setLargeFileTransferInfo(String id,String path){
		if(Syncrop.isInstanceOfCloud())
			pathsOfLargeFilesBeingSent.put(id,path);
		else pathOfLargeFilesBeingSent=path;
	}
	private void unsetLargeFileTransferInfo(String id) {
		if(Syncrop.isInstanceOfCloud())
			if(id==null)
				pathsOfLargeFilesBeingSent.clear();
			else pathsOfLargeFilesBeingSent.remove(id);
		else pathOfLargeFilesBeingSent=null;		
	}

	public void addToSendQueue(String[] paths,String owner,String target)
	{
		for(String path:paths)
			addToSendQueue(path, owner, target);
	}
	public void addToSendQueue(String path,String owner,String target)
	{
		if(!isNotWindows())
			path=SyncropFile.toWindowsPath(path);
		SyncropItem file=getFile(path, owner);
		if(file==null)
			logger.log(path +"cannot be added to send queue because there is no corrosponding file");
		else addToSendQueue(file,target);
	}
	/**
	 * adds a file to the send que
	 * @param file -the file to send
	 * @param targets -who to send it to
	 */
	public void addToSendQueue(SyncropItem file,String target)
	{
		if(Syncrop.isShuttingDown()){
			logger.log("Cannot add"+file.getPath()+" to queue because shutting down");
			return;
		}
		if(file==null){
			throw new NullPointerException("Cannont add a null file to send queue");
		}
		else if(!file.isEnabled()){
			logger.log(file.getPath()+" cannot be added to send queue because it is not enabled");
			return;
		}
		if(file.getSize()>Settings.getMaxFileSize())
		{
			logger.logTrace("File is too big to sync path="+file.getPath()+" size="+
				file.getSize()/SyncropClientDaemon.MEGABYTE+"MBs");
			return; 
		}
		
		else if(target==null)
		{
			logger.log("file not added to send queue " +
					"because it had no target path="+file.getPath());
			throw new NullPointerException("target==null");
			//return;
		}
		else {
			if(Syncrop.isInstanceOfCloud())
				if(SyncropCloud.getSyncropUser(target).isPathRestricted(file.getPath())){
					logger.log("file "+file+" is not enabled on cloud for user: "+target);
					return;
				}
			logger.logTrace("Adding "+file+" to send queue");
			sendQueue.add(file,target);
		}
	}
	
	
	/**
	 * Pauses or unpauses this file transfer manager; When it is paused, no files are sent
	 * or downloaded; Timeout are also paused
	 * @param b the state of this file transfer manager;
	 */
	public void pause(boolean b)
	{
		paused=b;
		if(logger.isDebugging())
			logger.log("FTM paused: "+paused);
	}
	public boolean isPaused(){return paused;}
	
	
	
	private void sendFile(QueueMember member){
		
		SyncropItem file=ResourceManager.getFile(member.getPath(),member.getOwner());
		if(file == null) {
			logger.logTrace("file no longer exists "+member.getPath());
			return;
		}
		if(file.hasBeenUpdated()){
			logger.logTrace("will not send file because file has recently been updated"+file.getPath());
			return;
		}
		String userSendingTo=member.getTarget();
		
		daemon.uploadFile(file, userSendingTo);
		timeLastFileWasSent=System.currentTimeMillis();
		outStandingFiles++;
		logger.log("Sending: "+file.getPath()+" "+userSendingTo);
	
	}
	
	
	
	public void updateDownloadFileTransferStatistics(String path)
	{
		timeOfLastCompletedFileTransfer=System.currentTimeMillis();
		downloadedFiles.add(path);
		nameOfDownloadFile=path;
	}
	public void onSuccessfulFileUpload(Message message){
		Object []o=(Object[]) message.getMessage();
		SyncropItem fileSent=ResourceManager.getFile((String)o[INDEX_PATH],(String) o[INDEX_OWNER]);
		if(!SyncDaemon.isInstanceOfCloud()&& fileSent instanceof SyncropFile){
			
			logger.log(fileSent.getPath()+" Changing key from "+fileSent.getKey()+" to "+o[INDEX_KEY]);
			logger.logTrace("key "+fileSent.getKey()+" is being changed to "+o[INDEX_KEY]+" path="+fileSent.getPath());
			((SyncropFile)fileSent).setKey((int) o[INDEX_KEY]);
			if((long)o[INDEX_DATE_MODIFIED]!=fileSent.getDateModified())
				fileSent.setModifiedSinceLastKeyUpdate(true);
			fileSent.save();
			
			//if(fileSent.getDateModified()!=dateMod)addToSendQueue(fileSent, userSendingTo);
		}
		if(fileSent==null);//metadata does not exists
		else if(!Syncrop.isInstanceOfCloud()&&!fileSent.exists());
		else if(fileSent.getPath().equals(getPathOfLargeFileBeingSent(message.getUserID())))
			unsetLargeFileTransferInfo(message.getUserID());
		logger.log("fileSent "+o[INDEX_PATH]);
		updateUploadFileTransferStatistics((String)o[INDEX_PATH]);
	}
	private void updateUploadFileTransferStatistics(String path)
	{
		timeOfLastCompletedFileTransfer=System.currentTimeMillis();
		uploadedFiles.add(path);
		nameOfUploadedFile=path;
		logger.log(uploadedFiles.size()+". File upload success:"+path);
		
		outStandingFiles--;
	}
	
	

	public void cancelUpload(String id,String path,boolean localCommand)
	{
		
		if(localCommand)//tells recipient to stop downloading the file that this client was uploading
			cancel(path,HEADER_CANCEL_DOWNLOAD,id);
		else {
			outStandingFiles--;
			logger.log("Remote cancel upload;");
		}
	
		logger.log("Upload failed: "+path);
		if(path.equals(getPathOfLargeFileBeingSent(id)))
			unsetLargeFileTransferInfo(id);
	}
	

	public String getPathOfLargeFileBeingSent(String id){
		return Syncrop.isInstanceOfCloud()?pathsOfLargeFilesBeingSent.get(id):pathOfLargeFilesBeingSent;
	}
	
	public boolean isLargeFileUploadOngoing(String id,String path){
		return path.equals(getPathOfLargeFileBeingSent(id));
	}
	public void cancelDownload(String id,String path,boolean localCommand)
	{	
		if(localCommand)
			//tells recipient to stop uploading the file that this client was downloading
			cancel(path,HEADER_CANCEL_UPLOAD,id);
		
		if(!localCommand)logger.log("Remote cancel download: "+path);

		if(path.equals(getPathOfLargeFileBeingSent(id)))
			ResourceManager.deleteTemporaryFile(id,path);
	}
		
	private void cancel(String path,String header,String target)
	{		
		logger.log("Telling "+target+
				(header.contains("failed")?" that ":" to ")+
				header+" file:"+path);
		daemon.printMessage(path,header,target);
	}
	
	@Override
	public void run()
	{
		logger.log("File Transfer Manager started");
		while(!SyncropClientDaemon.isShuttingDown())
		{
			try
			{
				if(!paused&&outStandingFiles<=12&&daemon.isConnectionAccepted()&&!isEmpty()
					&&System.currentTimeMillis()-timeLastFileWasSent>daemon.getExpectedFileTransferTime()/2)	
				{
					QueueMember m=sendQueue.peek();
					if(m==null)continue;
					if(m.getTimeInQueue()>Math.max(1000,daemon.getExpectedFileTransferTime()))
						if(m.isLargeFile()&&daemon.isSendingLargeFile()){
							Syncrop.sleep();
						}
						else {
							sendFile(sendQueue.poll());
							Syncrop.sleepShort();
							if(daemon.getExpectedFileTransferTime()>10000)
								logger.logTrace("File transfer time is high: "+daemon.getExpectedFileTransferTime());
							
						}
					else Syncrop.sleep();
				}
				else Syncrop.sleep();
			}
			catch (Throwable e) {
				logger.logFatalError(e, "");
				System.exit(0);
				break;
			}
		}
	}
	public String toString()
	{
		return "sendQueue size="+sendQueue.size();				
	}
	
	public long getTimeFromLastCompletedFileTransfer() {
		return System.currentTimeMillis()-timeOfLastCompletedFileTransfer;
	}
	public boolean haveAllFilesFinishedTranferring(){
		return isEmpty()&&getOutstandingFiles()==0;
	}
	public int getOutstandingFiles(){return outStandingFiles;}
	public int getTransferedFiles(){return getDownloadCount()+getUploadCount();}
	
	public boolean canDownloadPacket(SyncropItem localFile, String id,String path,String owner, long dateModified, int key, byte[]bytes,long size){
		return isFileEnabled(localFile,path,owner)&&!isFileSizeToLarge(bytes,path)
				&&isRoomLeftInAccountAfterTransfer(id,path,owner, size)
				&&!convertFileToDir(localFile, id,key)
				&&!shouldConflictBeMadeForFileBeingSent(localFile, key, dateModified, id)
				&&isValidOwner(localFile, owner);
	}
	private boolean isValidOwner(SyncropItem localFile,String owner){
		if(owner==null){
			logger.logDebug("owner cannot be null");
			return false;
		}
		else if(localFile==null)return true;
		else {
			boolean same= localFile.getOwner().equals(owner);
			if(!same)
				logger.logDebug("local file owner does not equal client file owner: "+localFile.getOwner()+"!="+(owner));
			return same;
		}
	}
	private boolean isFileEnabled(SyncropItem localFile,String path,String owner){
		if(localFile!=null&&!localFile.isEnabled()){
			logger.log("File is not enabled so it could not be downloaded; path="+path);
			return false;
		}
		else if(ResourceManager.getAccount(owner).isPathEnabled(path))
			return true;
		else {
			logger.logDebug(path+" is not enabled");
			return false;
		}
	}
	private boolean isFileSizeToLarge(byte[]bytes,String path){
		if(bytes!=null){
			if(bytes.length>Settings.getMaxTransferSize())
				logger.logDebug("file path="+path+" packet is to large to download");
			return bytes.length>Settings.getMaxTransferSize();
		}
		return false;
	}
	private boolean isRoomLeftInAccountAfterTransfer(String id,String path,String owner,long size){
		if(ResourceManager.getAccount(owner).willBeFull(size))
		{
			logger.logDebug("No space left in account, so file cannot be synced");
			cancelDownload(id,path,true);
			return false;
		}
		return true;
	}
	private boolean shouldConflictBeMadeForFileBeingSent(SyncropItem localFile, int key,long modificationDate,String id){
		if(localFile!=null&&localFile.exists())
			if(key!=localFile.getKey()&&
				modificationDate<localFile.getDateModified())
				//if conflict should be made for the file being sent
			{
				logger.log("Download failed because local file is newer");
				cancelDownload(id,localFile.getPath(),true);
				//send local file to sender so that they can make a conflict
				addToSendQueue(localFile, id);
				return true;
			}
		return false;
	}
	
	private boolean convertFileToDir(SyncropItem localFile,String id,int key){
		//if local file is dir and receiving file is not dir
		if(localFile!=null&&localFile.isDir()&&SyncropItem.represetsDir(key))
			if(localFile.exists()){
				logger.log("Downloaded file is being made into a conflict because local file is a dir");
				//data[3]=key;
				//deletes file and replaces it with dir; if keys don't match, then file is conflicted
				
				addToSendQueue(localFile, id);
				return true;
			}
			else localFile.deleteMetadata();
		return false;
	}
		
	
	public void uploadRequest(Message message){
		final String header=message.getHeader();
		String originalPath=header.equals(HEADER_FILE_SUCCESSFULLY_UPLOADED)?
						(String)((Object[])message.getMessage())[INDEX_PATH]:
						(String)message.getMessage();
						
		String path=isNotWindows()?
				originalPath:
				SyncropItem.toWindowsPath(originalPath);
		
		if(message.getHeader().equals(HEADER_FILE_SUCCESSFULLY_UPLOADED)){
			Object[]syncropMetadata=(Object[]) message.getMessage();
			if(!daemon.verifyUser(message.getUserID(),(String)syncropMetadata[INDEX_OWNER])){
				logger.log("Received header:"+HEADER_FILE_SUCCESSFULLY_UPLOADED+" but user:"+message.getUserID()+
						"did not have permission");
				return;
			}
			onSuccessfulFileUpload(message);
		}
		else if(message.getHeader().equals(HEADER_CANCEL_UPLOAD)){
			if(path!=null&&path.equals(getPathOfLargeFileBeingSent(message.getUserID()))){
				logger.log("Large upload of"+path+" is being canceled");
				cancelUpload(message.getUserID(),path,false);
				return; 
			}
			else logger.log("Cancel request ignored becaues there is nothing to do:"+path);
		}
		else logger.log("Unknown header:"+ header);
		
	}
	
	public void downloadRequest(Message message){
		String originalPath=(String)(message.getMessage() instanceof Object[]?
				((Object[])message.getMessage())[INDEX_PATH]:message.getMessage());
		String path=isNotWindows()?originalPath:
			SyncropItem.toWindowsPath(originalPath);
		String sender=message.getUserID();
		if(message.getHeader().equals(HEADER_CANCEL_DOWNLOAD))
			if(path!=null&&path.equals(getPathOfLargeFileBeingSent(message.getUserID()))){
				cancelDownload(sender,path,false);
				return;
			}
		
		Object syncData[]=(Object[])message.getMessage();
		
		String owner=(String)syncData[INDEX_OWNER];
		
		if(!ResourceManager.getAccount(owner).isPathEnabled(path))
			cancelDownload(sender,path,true);
		if(!daemon.verifyUser(message.getUserID(),owner))
			return;
		
		long dateModified=(long)syncData[INDEX_DATE_MODIFIED];
		int key=(int)syncData[INDEX_KEY];
		int filePermissions=(int) syncData[INDEX_FILE_PERMISSIONS];
		boolean exists=(boolean)syncData[INDEX_EXISTS];
		boolean updatedSinceLastUpdate=(boolean)syncData[INDEX_MODIFIED_SINCE_LAST_KEY_UPDATE];
		String target=(String)syncData[INDEX_SYMBOLIC_LINK_TARGET];
		
		long size=(long)syncData[INDEX_SIZE];
		
		switch (message.getHeader()) 
		{
			case HEADER_REQUEST_SMALL_FILE_DOWNLOAD:
				daemon.downloadFile(sender, path,owner, dateModified, key,updatedSinceLastUpdate,filePermissions,exists,(byte[])syncData[INDEX_BYTES], size,target, false,true);
				break;
			case HEADER_REQUEST_LARGE_FILE_DOWNLOAD_START:
				daemon.startDownloadOfLargeFile(sender, path,owner, dateModified, key,updatedSinceLastUpdate,filePermissions,exists, size);
				break;
			case HEADER_REQUEST_LARGE_FILE_DOWNLOAD:
				daemon.downloadLargeFile(sender, path,owner, dateModified, key,updatedSinceLastUpdate,filePermissions,exists,(byte[])syncData[INDEX_BYTES], size);
				break;
			case HEADER_REQUEST_END_LARGE_FILE_DOWNLOAD:
				daemon.endDownloadOfLargeFile(sender, path, owner, dateModified, key, updatedSinceLastUpdate, filePermissions, exists, size);
				break;
		}
		
	}
	public void addToQueueRequest(Message message){

		String sender=message.getUserID();
		switch (message.getHeader()) 
		{
			case HEADER_ADD_TO_SEND_QUEUE:
				addToSendQueue(
						(String)message.getMessage(),sender, message.getUserID());
				break;
			case HEADER_ADD_MANY_TO_SEND_QUEUE:
				addToSendQueue(
						(String[])message.getMessage(),sender, message.getUserID());
				break;
		}
	}
	public int getDownloadCount(){return downloadedFiles.size();}
	public int getUploadCount(){return uploadedFiles.size();}
	
	
	public static byte[]getHash(byte[]bytes){
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
				return md.digest(bytes);
		} catch (NoSuchAlgorithmException  e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public String getNameOfDownloadFile() {
		return nameOfDownloadFile;
	}

	public String getNameOfUploadedFile() {
		return nameOfUploadedFile;
	}

	
}
