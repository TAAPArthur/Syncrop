package transferManager;

import static daemon.SyncDaemon.TRANSFER_SIZE;
import static file.SyncROPItem.INDEX_BYTES;
import static file.SyncROPItem.INDEX_DATE_MODIFIED;
import static file.SyncROPItem.INDEX_EXISTS;
import static file.SyncROPItem.INDEX_FILE_PERMISSIONS;
import static file.SyncROPItem.INDEX_KEY;
import static file.SyncROPItem.INDEX_MODIFIED_SINCE_LAST_KEY_UPDATE;
import static file.SyncROPItem.INDEX_OWNER;
import static file.SyncROPItem.INDEX_PATH;
import static file.SyncROPItem.INDEX_SIZE;
import static file.SyncROPItem.INDEX_SYMBOLIC_LINK_TARGET;
import static notification.Notification.displayNotification;
import static syncrop.ResourceManager.getFile;
import static syncrop.Syncrop.isInstanceOfCloud;
import static syncrop.Syncrop.isNotWindows;
import static syncrop.Syncrop.logger;

import java.util.LinkedList;

import listener.FileWatcher;
import message.Message;
import settings.Settings;
import syncrop.ResourceManager;
import syncrop.Syncrop;
import syncrop.SyncropLogger;
import transferManager.queue.QueueMember;
import transferManager.queue.SendQueue;
import daemon.SyncDaemon;
import daemon.client.SyncropClientDaemon;
import daemon.cloud.SyncropCloud;
import file.SyncROPFile;
import file.SyncROPItem;
/**
 * This class has a queue for sent and receive files. File transfer requests will be
 * sent in the order in which they appear. Only file can be transfered at a time and 
 * the receive and send queues alternate: send, then receive, then send...
 * @author taaparthur
 *
 */
public class FileTransferManager extends Thread{
	

	/**
	 * used to add a file to the recipient receive queue
	 */
	public final static String HEADER_ADD_TO_RECEIVE_QUEUE="add to receive queue";
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
	
	public final static String HEADER_UPDATE_KEYS="UPDATE_KEYS";
	
	/**
	 * Used to delete many files
	 */
	public final static String HEADER_DELETE_MANY_FILES="delete many files";
	
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
	 * to indicate that the next packet of a large file should be uploaded.<br/>
	 * This message that should be sent should contain a Sting with the path of large file.
	 * Upon receiving this message, the next packet of the large file should be sent.
	 *  
	 * <br/>
	 * <b>Note that if the recipient is not expecting a file with the designated path, the 
	 * notification will fail.</b> To upload properly, use one of {@link FileTransferManager}'s 
	 * addToSendQueue methods like
	 *  {@link FileTransferManager#addToSendQueue(String, String, String...)}
	 * @see Message
	 */
	public final static String HEADER_FILE_UPLOAD_NEXT_PACKET="upload next packet";
	
	/**
	 * When true, this variable indicates that the next packet of a large file should be 
	 * sent. When false, the {@link #uploadLargeFileThread}  is slept until the value is 
	 * changed to true
	 */
	private static boolean canSendNextPacket;
	
	/**
	 * This String is used as a header for a {@link Message}
	 * to tell the recipient that the upload failed and cannot be continued. The recipient
	 * should stop trying to receive the file.<br/>
	 * The file should not be resent until an attempt has been made to fix the issue. And
	 * if an attempt has been made, the file should be re-added to the end of the send queue<br/>
	 * This message that should be sent should contain a Sting with the path of file that failed to be uploaded 
	 * <br/>
	 * <b>Note that if the recipient is not expecting a file with the designated path, the 
	 * notification will fail.</b> To upload properly, use one of {@link FileTransferManager}'s 
	 * addToSendQueue methods like
	 *  {@link FileTransferManager#addToSendQueue(String, String, String...)}
	 * @see Message
	 */
	public final static String HEADER_UPLOAD_FAILED="upload failed";
	
	/**
	 * Tells recipient that the download failed and cannot be continued. The recipient
	 * should stop trying to send the file.<br/>
	 * The file will not not be resent until an attempt has been made to fix the issue. And
	 * if an attempt has been made the file will be re-added to the end of the send queue 
	 */
	public final static String HEADER_DOWNLOAD_FAILED="download failed";
	
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
	 * less than or equal to the transfer size, {@value SyncDaemon#TRANSFER_SIZE}. <br/>
	 * The message that should accompany this header is defined by 
	 * {@link SyncROPItem#formatFileIntoSyncData(byte[])}
	 */
	public final static String HEADER_REQUEST_SYMBOLIC_LINK_DOWNLOAD="request symbolic link download";
	
	/**
	 * Requests recipient to download a small file. A small fire is a file whose size is 
	 * less than or equal to the transfer size, {@value #TRANSFER_SIZE}. <br/>
	 * The message that should accompany this header is defined by 
	 * {@link SyncROPItem#formatFileIntoSyncData(byte[])}
	 */
	public final static String HEADER_REQUEST_SMALL_FILE_DOWNLOAD="request small file download";
	/**
	 * Requests recipient to download a small file. A small fire is a file whose size is 
	 * greater than the transfer size, {@value #TRANSFER_SIZE}. <br/>
	 * The message that should accompany this header is defined by 
	 * {@link SyncROPItem#formatFileIntoSyncData(byte[])}
	 */
	public final static String HEADER_REQUEST_LARGE_FILE_DOWNLOAD="request large file download";
	
	/**
	 * tells the recipient that the a large download file has completed and that the 
	 * temp file should be copied to the real file<br/>
	 * The message that should accompany this header is defined by 
	 * {@link #formatFileIntoSyncData(SyncROPFile, byte[])}
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

	
	volatile int uploadCount;
	volatile int downloadCount;
	volatile boolean keepRecord=true;
	/**
	 * The name of the first file uploaded/downloaded since the last save
	 */
	String downloadNameOfFirstFile,uploadNameOfFirstFile;
	
	private volatile long timeOfLastCompletedFileTransfer;
	private boolean hasMetadataBeenCleaned=false;
	/**
	 * a record of files to send with a 2D array of targets to include(0) and targets
	 * to excluded
	 * 
	 *  gareenteed to have a non null value that has a size that is greater than 0
	 */
	private volatile SendQueue sendQueue=new SendQueue();
	/**
	 * a list of files to receive. The key is the absolute path 
	 * and the value an array of the path, owners and sender
	 */
	private volatile LinkedList<String[]>receiveQueue=new LinkedList<String[]>();
	/**
	 * if a file is being sent. No two files will be sent simultaneously 
	 */
	private volatile boolean sending=false;
	/**
	 * if a file is being received. No two files will be received simultaneously 
	 */
	private volatile boolean receiveing=false;
	private volatile String userSendingTo,userReceivingFrom;
	private volatile String fileSending, fileReceiveing;
	private volatile long fileSendingDate;
	private String fileSendingOwner;
	
	private volatile long timeStartReceiving=0;
	private volatile long timeStartSending=0;
	private volatile boolean paused;
	private volatile int timeOutSending=60,timeOutReceiving=120;
	
	private volatile int failCount=0;
	
	final SyncDaemon daemon;
	
	public FileTransferManager(SyncDaemon daemon)
	{
		super("file transfer manager");
		this.daemon=daemon;
		
	}
	
	/**
	 * Resets this FTM to default settings. The queues are cleared and it is 
	 * not sending nor receiving. The temporary file is also deleted if it exists
	 */
	public void reset()
	{
		ResourceManager.deleteTemporaryFile();
		sendQueue.clear();
		receiveQueue.clear();
		resetSendInfo();
		resetReceiveInfo();
		failCount=0;
		timeOfLastCompletedFileTransfer=0;
		hasMetadataBeenCleaned=false;
		
	}
	
	/**
	 * receive 
	 * @return true if both the sendQueue and the receiveQueue are empty
	 */
	public boolean isEmpty()
	{
		return sendQueue.isEmpty()&&receiveQueue.isEmpty();
	}
	public int getSendQueueSize()
	{
		return sendQueue.size();
	}

	public void addToSendQueue(String[] paths,String owner,String target)
	{
		for(String path:paths)
			addToSendQueue(path, owner, target);
	}
	public void addToSendQueue(String path,String owner,String target)
	{
		if(!isNotWindows())
			path=SyncROPFile.toWindowsPath(path);
		SyncROPItem file=getFile(path, owner);
		
		addToSendQueue(file,target);
	}
	/**
	 * adds a file to the send que
	 * @param file -the file to send
	 * @param targets -who to send it to
	 */
	public void addToSendQueue(SyncROPItem file,String target)
	{
		if(file==null){
			throw new NullPointerException("Cannont add a null file to send queue");
		}
		else if(!file.isEnabled()){
			logger.log(file.getPath()+" cannot be added to send queue because it is not enabled");
			return;
		}
		if(file.getSize()>SyncropClientDaemon.MAX_FILE_SIZE)
		{
			logger.log("File is too big to sync path="+file.getPath()+" size="+
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
	
	public void addToReceiveQueue(String path,String owner,String sender)
	{	
		if(!isNotWindows())
			path=SyncROPFile.toWindowsPath(path);
				
		SyncROPItem file=getFile(path, owner);
		
		if((file!=null&&!file.isEnabled())||
				!ResourceManager.getAccount(owner).isPathEnabled(path))
		{
			System.out.println(file+" "+ResourceManager.getAccount(owner).isPathEnabled(path));
			if(logger.isDebugging())
				logger.log("Stopping upload of "+path+" because file is not enabled or is " +
						"restricted");
			
			daemon.printMessage(path,HEADER_UPLOAD_FAILED,sender);
			//SyncropDaemon.mainClient.printMessage(new Object[]{path,new String[]{owner}},SyncropDaemon.HEADER_REMOVE_FILE,sender);
		}
		else {
			logger.logDebug("Adding "+path+" to receive queue");
			receiveQueue.add(new String[]{path,owner,sender});
		}
	}
	
	public boolean isReceiving(){return receiveing;}
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
	
	public boolean isSending(){return sending;}
	
	private void sendFile()
	{
		
		QueueMember member=sendQueue.poll();
		SyncROPItem file=ResourceManager.getFile(member.getPath(),member.getOwner());
		userSendingTo=member.getTarget();
		if(isInstanceOfCloud()&&!SyncropCloud.hasUser(userSendingTo))
			return;

		if(file==null)logger.log("file "+file+" was not sent because it is null",SyncropLogger.LOG_LEVEL_DEBUG);
		else if(file.isDir()&&!file.isEmpty())
			logger.log("file "+file+" was not sent because it is a non empty dir",SyncropLogger.LOG_LEVEL_DEBUG);
		else if(file.isEnabled())
		{
			daemon.printMessage(
					new String[]{
							isNotWindows()?
									file.getPath():
									SyncROPFile.toLinuxPath(file.getPath())
								,file.getOwner()},
					HEADER_ADD_TO_RECEIVE_QUEUE,userSendingTo);
			sending=true;
			
			fileSendingDate=file.getDateModified();
			fileSending=file.getPath();
			logger.log("Sending: "+file.getPath()+" "+userSendingTo);
			fileSendingOwner=file.getOwner();
		}
		else logger.log("file "+file+" was not sent because it is not" +
					"enabled",SyncropLogger.LOG_LEVEL_DEBUG);
	}
	
	private void receiveFile()
	{
		String values[]=receiveQueue.pop();
		
		String path=values[0];
		if(!isNotWindows())
			path=SyncROPFile.toLinuxPath(path);
		if(isInstanceOfCloud()&&!SyncropCloud.hasUser(values[2]))
			return;
		userReceivingFrom=values[2];
		
		daemon.printMessage(new String[]{path,values[1]},
				HEADER_REQUEST_FILE_UPLOAD,userReceivingFrom);
		fileReceiveing=path;
	
		receiveing=true;
		
	}
	public void receivedFile()
	{
		timeOfLastCompletedFileTransfer=System.currentTimeMillis();
		if(isKeepingRecord()){
			if(downloadCount==0)
				downloadNameOfFirstFile=fileReceiveing;
			downloadCount++;
		}
		resetReceiveInfo();
		failCount=0;
	}
	public boolean isKeepingRecord(){
		return keepRecord;
	}
	public void keepRecord(boolean b){
		keepRecord=b;
	}
	public void resetRecord(){
		uploadCount=downloadCount=0;
		uploadNameOfFirstFile=downloadNameOfFirstFile=null;
	}
	public void sentFile(long newKey)
	{
		SyncROPItem fileSent=ResourceManager.getFile(fileSending, fileSendingOwner);
		if(!SyncDaemon.isInstanceOfCloud()&& fileSent instanceof SyncROPFile&&fileSent.exists()){
			((SyncROPFile)fileSent).setKey(newKey);
			if(fileSendingDate!=fileSent.getDateModified())
				fileSent.setModifiedSinceLastKeyUpdate(true);
			fileSent.save();
			//if(fileSent.getDateModified()!=dateMod)addToSendQueue(fileSent, userSendingTo);
		}
		timeOfLastCompletedFileTransfer=System.currentTimeMillis();
		
		
		if(isKeepingRecord())
		{
			if(uploadCount==0)
				uploadNameOfFirstFile=fileSending;
			uploadCount++;
		}
		logger.log("File upload success"+fileSent);
		resetSendInfo();
		failCount=0;
	}
	public void stopUpload()
	{
		resetSendInfo();
	}
	public void resetReceiveInfo()
	{
		if(ResourceManager.getTemporaryFile().exists())
			ResourceManager.getTemporaryFile().delete();
		userReceivingFrom=null;
		fileReceiveing=null;
		receiveing=false;
	}
	public void resetSendInfo()
	{
		userSendingTo=null;
		fileSending=null;
		fileSendingOwner=null;
		sending=false;
		fileSendingDate=-1;
	}

	
	public void cancelUpload(String fileThatCouldNotBeUploaded,boolean localCommand,boolean failed)
	{
		if(localCommand)
			//tells recipient to stop downloading the file that this client was uploading
			cancel(fileThatCouldNotBeUploaded,
					failed?
							HEADER_DOWNLOAD_FAILED:
							HEADER_CANCEL_DOWNLOAD,
					userSendingTo);
		if(isSending()&&fileSending.equals(fileThatCouldNotBeUploaded)){
			if(!localCommand)
				logger.log("Remote cancel upload;");
			
			if(!failed)
			{
				logger.log("Re-adding file to send queue");
				addToSendQueue(fileSending,fileSendingOwner, userSendingTo);
				logger.log("Stopped sending file "+fileThatCouldNotBeUploaded);
			}
			else logger.log("file failed to be sent "+fileThatCouldNotBeUploaded);
					
				resetSendInfo();
		}
	}
	public void cancelDownload(String fileThatCouldNotBeDownloaded,boolean localCommand,boolean failed)
	{	
		if(localCommand)
			//tells recipient to stop uploading the file that this client was downloading
			cancel(fileThatCouldNotBeDownloaded,
					failed?
							HEADER_UPLOAD_FAILED:
							HEADER_CANCEL_UPLOAD,
						userReceivingFrom);
		
		if(isReceiving()&&fileReceiveing.equals(fileThatCouldNotBeDownloaded)){
			if(!localCommand)logger.log("Remote cancel download");
			if(!failed)
				logger.log("Stopped downloading file "+fileThatCouldNotBeDownloaded);
			else logger.log("failed to download file:"+fileThatCouldNotBeDownloaded);

			resetReceiveInfo();
		}
	}
		
	private void cancel(String path,String header,String target)
	{
		if(failCount==4)
		{
			SyncropClientDaemon.removeUser(target, "Out of sync with Cloud");
			return;
		}
		else failCount++;
		
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
				SyncropClientDaemon.sleepShort();
				if(paused){
					Syncrop.sleep();
					continue;
				}
				if(isEmpty()){
					if(!Syncrop.isInstanceOfCloud()&&daemon.isConnectionAccepted()&&timeOfLastCompletedFileTransfer!=0&&!hasMetadataBeenCleaned){
						FileWatcher.checkAllMetadataFiles(false);
						hasMetadataBeenCleaned=true;
					}
					SyncropClientDaemon.sleep();
					continue;
				}
				//if(isSending()||isReceiving())continue;
				if(daemon.isConnectionAccepted())	
				{
					if(!isSending()&&!sendQueue.isEmpty())
					{
						if(System.currentTimeMillis()-sendQueue.peek().getTimeStamp()>1000){
							sendFile();
							updateTimeSending();
						}
					}
					if(!isReceiving()&&!receiveQueue.isEmpty())
					{
						receiveFile();
						updateTimeReceiving();
					}
					if(receiveing&&(System.currentTimeMillis()-timeStartReceiving)/1000>timeOutReceiving)
					{
						logger.log("Timeout receiving file:"+fileReceiveing);
						cancelDownload(fileReceiveing,true, false);
					}
					if(sending&&(System.currentTimeMillis()-timeStartSending)/1000>timeOutSending)
					{
						logger.log("Timeout sending file:"+fileSending);
						cancelUpload(fileSending, true, false);
					}
				}
			} 
			catch (Throwable e) {
				logger.logFatalError(e, "");
				System.exit(0);
			}
		}
	}
	public void updateTimeReceiving()
	{
		timeStartReceiving=System.currentTimeMillis();
	}
	public void updateTimeSending()
	{
		timeStartSending=System.currentTimeMillis();
	}
	public String toString()
	{
		return "sendQueue size="+sendQueue.size()+" receiveQueuesize="+receiveQueue.size()+" " +
				"fileSending="+isSending()+" file being sent="+fileSending+" " +
						"fileRevieing="+isReceiving()+" file being received="+fileReceiveing;				
	}
	
	public String getUserReceivingFrom() {
		return userReceivingFrom;
	}
	public String getUserSendingTo() {
		return userSendingTo;
	}
	public boolean isUserReceivingFrom(String user){
		if(!isInstanceOfCloud())
			return true;
		return isReceiving()&&user.equals(userReceivingFrom);
	}
	public boolean isUserSendingTo(String user){
		if(!isInstanceOfCloud())
			return true;
		return isSending()&&user.equals(userSendingTo);
	}
	
	
	public String getFileSending() {
		return fileSending;
	}
	public String getFileReceiveing() {
		return fileReceiveing;
	}
	public void setTimeOutSending(int i)
	{
		timeOutSending=i;
	}
	public long getTimeFromLastCompletedFileTransfer() {
		return System.currentTimeMillis()-timeOfLastCompletedFileTransfer;
	}
	
	public boolean haveAllFilesFinishedTranferring(){
		return isEmpty()&&!isSending()&&!isReceiving();
	}
	
	
	public boolean canDownloadPacket(SyncROPItem localFile, String id,String path,String owner, long dateModified, long key, byte[]bytes,long size){
		return isFileEnabled(localFile,path,owner)&&!isFileSizeToLarge(bytes,path)
				&&isRoomLeftInAccountAfterTransfer(path,owner, size)
				&&!convertFileToDir(localFile, id,key)
				&&!shouldConflictBeMadeForFileBeingSent(localFile, key, dateModified, id)
				&&isValidOwner(localFile, owner);
	}
	private boolean isValidOwner(SyncROPItem localFile,String owner){
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
	private boolean isFileEnabled(SyncROPItem localFile,String path,String owner){
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
			if(bytes.length>TRANSFER_SIZE)
				logger.logDebug("file path="+path+" packet is to large to download");
			return bytes.length>TRANSFER_SIZE;
		}
		return false;
	}
	private boolean isRoomLeftInAccountAfterTransfer(String path,String owner,long size){
		if(ResourceManager.getAccount(owner).willBeFull(size))
		{
			logger.logDebug("No space left in account, so file cannot be synced");
			cancelDownload(path,true, true);
			return false;
		}
		return true;
	}
	private boolean shouldConflictBeMadeForFileBeingSent(SyncROPItem localFile, long key,long modificationDate,String id){
		if(localFile!=null&&localFile.exists())
			if(key!=localFile.getKey()&&
				modificationDate<localFile.getDateModified())
				//if conflict should be made for the file being sent
			{
				logger.log("Download failed because local file is newer");
				cancelDownload(localFile.getPath(),true, true);
				//send local file to sender so that they can make a conflict
				addToSendQueue(localFile, id);
				return true;
			}
		return false;
	}
	
	private boolean convertFileToDir(SyncROPItem localFile,String id,long key){
		//if local file is dir and receiving file is not dir
		if(localFile!=null&&localFile.isDir()&&key!=-1)
			if(localFile.exists()){
				logger.log("Downloaded file is being made into a conflict because local file is a dir");
				//data[3]=key;
				//deletes file and replaces it with dir; if keys don't match, then file is conflicted
				
				addToSendQueue(localFile, id);
				return true;
			}
			else localFile.remove();
		return false;
	}
	
	public void checkForNotifications()
	{
		
		if(!isInstanceOfCloud()&&downloadCount+uploadCount!=0&&daemon.isConnectionAccepted())
			if(getTimeFromLastCompletedFileTransfer()>12000
					||downloadCount+uploadCount>=12
					||haveAllFilesFinishedTranferring()){
		
				notitfyUser();
			}
	}
	private void notitfyUser()
	{
		String prefix="",suffix="";
		if(haveAllFilesFinishedTranferring()){
			//prefix="All files finished transferring\n";
			suffix="\n\nAll files finished transferring";
		}
		if(uploadCount!=0)
			if(uploadCount==1)
				displayNotification(prefix+uploadNameOfFirstFile+" was uploaded to cloud"+suffix);
			else displayNotification(prefix+uploadNameOfFirstFile+" and "+(uploadCount-1)+
					" other file"+(uploadCount-1==1?"":"s")+" were uploaded to cloud"+suffix);
		if(downloadCount!=0)
			if(downloadCount==1)
				displayNotification(prefix+downloadNameOfFirstFile+" was downloaded from cloud"+suffix);
			else displayNotification(prefix+downloadNameOfFirstFile+" and "+(downloadCount-1)+
					" other file"+(downloadCount-1==1?"":"s")+" were downloaded from cloud"+suffix);
		if(haveAllFilesFinishedTranferring())
		{
			if(logger.isDebugging())
				logger.log("All files finished transferring");
			if(Settings.autoQuit()){
				logger.log("auto quitting");
				System.exit(0);
			}
		}
		uploadCount=downloadCount=0;
		uploadNameOfFirstFile=downloadNameOfFirstFile=null;			
	}
	
	public boolean canSendNextPacket(){return canSendNextPacket;}
	public void sentPacket(){canSendNextPacket=false;}
	public void uploadRequest(Message message){

		final String header=message.getHeader();
		String originalPath=
				header.equals(HEADER_REQUEST_FILE_UPLOAD)||header.equals(HEADER_FILE_SUCCESSFULLY_UPLOADED)?
						(String)((Object[])message.getMessage())[0]:
						(String)message.getMessage();
						
		String path=isNotWindows()?
				originalPath:
				SyncROPItem.toWindowsPath(originalPath);
			
		if(header.equals(HEADER_CANCEL_UPLOAD)||header.equals(HEADER_UPLOAD_FAILED)){
			if(path.equals(getFileSending()))
				if(!isUserSendingTo(message.getUserID())||
						!daemon.verifyUser(message.getUserID(), fileSendingOwner)){
					logger.log("Received signal to cancel upload but user:"+message.getUserID()+
							"did not have permission to stop it");
					return;
				}
			if(message.getHeader().equals(HEADER_CANCEL_UPLOAD)){
				logger.log("Received signal to cancel upload");
				cancelUpload(path,false, false);
			}
			else {
				logger.log("Received signal to cancel upload");
				cancelUpload(path,false, true);
			}
				
			if(daemon.isUploadingLargeFile()){
				daemon.interruptUploadingLargeFile();
				SyncropClientDaemon.sleep();
			}
		}
		else if(isUserSendingTo(message.getUserID())&&
				path.equals(getFileSending())&&
				daemon.verifyUser(message.getUserID(), fileSendingOwner))
			switch (header)
			{
				case HEADER_REQUEST_FILE_UPLOAD:
					String owner=((String[])message.getMessage())[1];
					if(owner.equals(fileSendingOwner))
						daemon.uploadFile(path,owner,message.getUserID());
					break;
				case HEADER_FILE_UPLOAD_NEXT_PACKET:
					canSendNextPacket=true;
					break;
				case HEADER_FILE_SUCCESSFULLY_UPLOADED:
					long newKey=(long)((Object[])message.getMessage())[1];
					sentFile(newKey);
					break;
				
				default:
					logger.log("Unknown header:"+ header);
			}
		else
		{
			String s="405 Error! message "+message.getHeader()+" has been ignored because ";
			if(!isSending()) s+="fileTransferManager is not sending";
			else if(!isUserSendingTo(message.getUserID()))
				s+=message.getUserID()+"!="+getUserSendingTo();
			else if(!path.equals(getFileSending()))
				s+=path+"!="+getFileSending();
			else 
				s+=message.getUserID()+" does not have access to "+fileSendingOwner;
			logger.log(s);
			cancelUpload(originalPath, true, false);
		}
	}
	public void deleteManyFiles(Object []array){
		
		daemon.printMessage(array, HEADER_DELETE_MANY_FILES);
	}
	public void deleteManyRequest(Message message){
		daemon.deleteManyFiles(message.getUserID(), (Object[][])message.getMessage());
		//((SyncropCloud)daemon).updateAllClients(message,owner,message.getUserID());
	}
	public void downloadRequest(Message message){

		String originalPath=(String)(message.getMessage() instanceof Object[]?
				((Object[])message.getMessage())[INDEX_PATH]:message.getMessage());
		String path=isNotWindows()?originalPath:
			SyncROPItem.toWindowsPath(originalPath);
		
		if(isUserReceivingFrom(message.getUserID())&&
				path.equals(getFileReceiveing()))
		{
			if(message.getHeader().equals(HEADER_CANCEL_DOWNLOAD))
			{
				cancelDownload(path,false, false);
				return;
			}
			else if(message.getHeader().equals(HEADER_DOWNLOAD_FAILED))
			{
				cancelDownload(path,false, true);
				return;
			}
			
			Object syncData[]=(Object[])message.getMessage();
			String sender=message.getUserID();
			String owner=(String)syncData[INDEX_OWNER];
			
			if(!ResourceManager.getAccount(owner).isPathEnabled(path))
				cancelDownload(path,true, true);
			
			long dateModified=(long)syncData[INDEX_DATE_MODIFIED];
			long key=(long)syncData[INDEX_KEY];
			String filePermissions=(String) syncData[INDEX_FILE_PERMISSIONS];
			boolean exists=(boolean)syncData[INDEX_EXISTS];
			boolean updatedSinceLastUpdate=(boolean)syncData[INDEX_MODIFIED_SINCE_LAST_KEY_UPDATE];
			if(message.getHeader().equals(HEADER_REQUEST_SYMBOLIC_LINK_DOWNLOAD)){
				String target=(String)syncData[INDEX_SYMBOLIC_LINK_TARGET];
				daemon.downloadFile(sender, path,owner, dateModified, key,updatedSinceLastUpdate,filePermissions,exists,null, 0,target, false,true);
				return;
			}
			
			byte[] bytes=null;
			long size=-1;
			//String pathOfTarget=null;
			if(syncData.length>5)
			{
					bytes=(byte[])syncData[INDEX_BYTES];
					size=(long)syncData[INDEX_SIZE];						
			}
			
			switch (message.getHeader()) 
			{
				case HEADER_REQUEST_SMALL_FILE_DOWNLOAD:
					if(bytes!=null&&bytes.length!=size)
						throw new IllegalArgumentException(
								"length of bytes needs to equal size when syncing small file");
					daemon.downloadFile(sender, path,owner, dateModified, key,updatedSinceLastUpdate,filePermissions,exists,bytes, size, false);
					break;
				case HEADER_REQUEST_LARGE_FILE_DOWNLOAD:
					daemon.downloadLargeFile(sender, path,owner, dateModified, key,updatedSinceLastUpdate,filePermissions,exists,bytes, size, false);
					break;
				case HEADER_REQUEST_END_LARGE_FILE_DOWNLOAD:
					daemon.downloadLargeFile(sender, path,owner, dateModified, key,updatedSinceLastUpdate,filePermissions,exists,bytes, size, true);
					break;
			}
		}
		else 
		{
			String s="406 Error! message "+message.getHeader()+" has been ignored because ";
			if(!isReceiving())
				s+="fileTransferManager is not receiving";
			else if(!path.equals(getFileReceiveing()))
				s+=path+"!="+getFileReceiveing();
			else if(!isUserReceivingFrom(message.getUserID()))
					s+=message.getUserID()+"!="+getUserReceivingFrom();
			logger.log(s);
					
			cancel(originalPath, HEADER_CANCEL_UPLOAD, message.getUserID());
		}

	
	}
	public void addToQueueRequest(Message message){

		String sender=message.getUserID();
		switch (message.getHeader()) 
		{
			case HEADER_ADD_TO_RECEIVE_QUEUE:
				String s[]=(String[])message.getMessage();
				String path=s[0];
				String owner=s[1];
				if(daemon.verifyUser(message.getUserID(), owner))
					addToReceiveQueue(path, owner, sender);
				break;
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
	public int getDownloadCount(){return downloadCount;}
	public int getUploadCount(){return uploadCount;}
	
	public void  updateKeys(Object[][]array){
		for(Object[] fileInfo:array){
			SyncROPItem file=ResourceManager.getFile((String)fileInfo[0], null);
			if(file instanceof SyncROPFile){
				((SyncROPFile)file).setKey((long)fileInfo[1]);
				file.save();
			}
		}
		
	}
}



/*
 * 
 * File renaming detection is not complete
public void renameRequest(Message m)throws IOException{
	Object syncData[]=(Object[])message.getMessage();
	
	String originalPath=(String) syncData[0],
			originalTargetPath=(String)syncData[5];
	
	String path=isNotWindows()?originalPath:
		SyncROPItem.toWindowsPath(originalPath),
		targetPath=isNotWindows()?originalTargetPath:
			SyncROPItem.toWindowsPath(originalTargetPath);
	
	String owners[]=getVerifiedOwners(message.getUsername(),(String[])syncData[1]);
	String owner=owners[0];
	if(!isPathEnabled(path, owners))
	{
		if(logger.isDebugging())
			logger.log("file "+path+" is considered to be disabled");
		
		return;
	}
	
	//long dateModified=(long)syncData[2];
	long key=(long)syncData[3];
	
	SyncROPItem originalFile;
	
	//SyncROPItem item;
	if(key==-1)//is directory
		originalFile=ResourceManager.getDirByPath(path, owners[0]);
	else 
		originalFile=ResourceManager.getFileByPath(path, owners[0]);
	
	if(originalFile==null||!originalFile.exists())return;
	originalFile.rename(new File(ResourceManager.getHome(owner, originalFile.isRemovable())), targetPath);
	mainClient.printMessage(syncData,
			HEADER_REQUEST_FILE_RENAME,originalFile.getOwners(),message.getUsername());
	
}
*/