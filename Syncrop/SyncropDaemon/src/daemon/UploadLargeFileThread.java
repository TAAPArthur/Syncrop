package daemon;


import static daemon.SyncDaemon.mainClient;
import static syncrop.Syncrop.isShuttingDown;
import static syncrop.Syncrop.logger;
import static transferManager.FileTransferManager.HEADER_REQUEST_END_LARGE_FILE_DOWNLOAD;
import static transferManager.FileTransferManager.HEADER_REQUEST_LARGE_FILE_DOWNLOAD;
import static transferManager.FileTransferManager.HEADER_REQUEST_LARGE_FILE_DOWNLOAD_START;

import java.io.IOException;
import java.nio.MappedByteBuffer;

import daemon.client.SyncropClientDaemon;
import file.SyncropFile;
import settings.Settings;
import syncrop.Syncrop;
import transferManager.FileTransferManager;

public class UploadLargeFileThread extends Thread
{
	SyncropFile file;
	String path;
	String target;
	int totalTimeOfTransfer;
	FileTransferManager fileTransferManager;
	
	public UploadLargeFileThread(SyncropFile file,String target,FileTransferManager fileTransferManager)
	{
		super("upload large file thread");
		this.file=file; 
		this.path=file.getPath();
		this.target=target;
		this.fileTransferManager=fileTransferManager;
		fileTransferManager.setLargeFileTransferInfo(target, path);
	}
	MappedByteBuffer map;
	public void run()
	{
		try {
			map=file.mapAllBytesFromFile();
			uploadFile(fileTransferManager);
			map.clear();
		} catch (Exception|Error e) {
			logger.logFatalError(e, "");
			System.exit(0);
		}
	}
	
	public void uploadFile(FileTransferManager fileTransferManager)
	{
		try 
		{

			long startTime=System.currentTimeMillis();
			
			//files have to be less than 2GB, which is the maxim Integer
			long size=(int)file.getSize();
			int offset=(int)Settings.getMaxTransferSize();
			logger.log("uploading large file; size="+size);
			//mainClient.pausePrinting(true);
			//mainClient.logs();
			
			long dateMod=file.getDateModified();
			mainClient.printMessage(file.toSyncData(),HEADER_REQUEST_LARGE_FILE_DOWNLOAD_START,target);
			logger.log("wait time:"+fileTransferManager.getDaemon().getExpectedFileTransferTime());
			SyncropClientDaemon.sleep(fileTransferManager.getDaemon().getExpectedFileTransferTime());
			
			for(int i=0;i<size;i+=offset){	
				if(SyncropClientDaemon.isConnectionActive());
				if(Settings.isLimitingCPU()&&Math.random()>.9){
					if(file.getDateModified()!=dateMod||file.getSize()!=size){
						fileTransferManager.cancelUpload(target, path, true);
						logger.log("large file has been updated during upload");
						return;
					}
					Syncrop.sleepShort();
				}
				if(isShuttingDown()){
					logger.log("Large file upload aborted; shutting down");
					return;
				}
				//checks to make sure that the file being sent is still the file 
				//that should be sent and that the connection has not closed
				else if(!mainClient.isConnectionAccepted())
					throw new IOException("connection lost with server");
				else if(!file.exists()){
					fileTransferManager.cancelUpload(target,path, true);
					logger.log("file was deleted so it was not sent "+file.getPath());
					return;
				}
				else if(!fileTransferManager.isLargeFileUploadOngoing(target,path)){
					logger.log("Large file upload of "+path+" has been canceled"+fileTransferManager.getPathOfLargeFileBeingSent(target));
					return;
				}
				else {
					byte[]bytes=new byte[(int)Math.min(offset, size-i)];
					
					//Quickly load all bytes; This method has the benefit of memorizing 
					//memory usage and is not affected by concurrent changes in the file
					map.get(bytes, 0, bytes.length);
									
					//sends the packet or sends the packet with a signal that the upload is finished
					mainClient.printMessage(file.toSyncData(bytes),HEADER_REQUEST_LARGE_FILE_DOWNLOAD,target);				
				}
			}
			mainClient.printMessage(file.toSyncData(),HEADER_REQUEST_END_LARGE_FILE_DOWNLOAD,target);
			logger.log("done total time:"+(System.currentTimeMillis()-startTime)/1000.0+"s");
			Syncrop.sleep();
		}
		catch (OutOfMemoryError | SecurityException | IOException e)
		{
			logger.logError(e,"occured while trying to upload large file path="+path) ;
			if(mainClient.isConnectionAccepted())
				fileTransferManager.cancelUpload(target,path,true);
		}
	}	
}