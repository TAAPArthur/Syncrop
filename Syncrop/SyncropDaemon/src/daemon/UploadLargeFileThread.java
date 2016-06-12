package daemon;

import static daemon.SyncDaemon.TRANSFER_SIZE;
import static daemon.SyncDaemon.mainClient;
import static syncrop.Syncrop.isShuttingDown;
import static syncrop.Syncrop.logger;
import static syncrop.Syncrop.sleepShort;
import static transferManager.FileTransferManager.HEADER_REQUEST_END_LARGE_FILE_DOWNLOAD;
import static transferManager.FileTransferManager.HEADER_REQUEST_LARGE_FILE_DOWNLOAD;

import java.io.IOException;
import java.nio.MappedByteBuffer;

import transferManager.FileTransferManager;
import file.SyncROPFile;

public class UploadLargeFileThread extends Thread
{
	SyncROPFile file;
	String path;
	String target;
	int totalTimeOfTransfer;
	FileTransferManager fileTransferManager;
	
	public UploadLargeFileThread(SyncROPFile file,String path,String target,FileTransferManager fileTransferManager)
	{
		super("upload large file thread");
		this.file=file; 
		this.path=path;
		this.target=target;
		this.fileTransferManager=fileTransferManager;
	}
	
	public void run()
	{
		try {
			uploadFile(fileTransferManager);
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
			MappedByteBuffer map=file.mapAllBytesFromFile();
			//files have to be less than 2GB, which is the maxim Integer
			long size=(int)file.getSize();
			int offset=TRANSFER_SIZE;
			logger.log("uploading large file; size="+size);
			//mainClient.pausePrinting(true);
			//mainClient.logs();
			for(int i=0;i<size;i+=offset)
			{	
				if(!fileTransferManager.isSending())
					throw new IOException("Upload of "+path+" was aborted");
				if(i!=0){
					//waits for approval to send next packet
					while(!fileTransferManager.canSendNextPacket()&&!isShuttingDown()){
						

						//checks to make sure that the file being sent is still the file 
						//that should be sent and that the connection has not closed
						if(!mainClient.isConnectionAccepted())
							throw new IOException("connection lost with server");
						else if(!fileTransferManager.isSending()||!path.equals(fileTransferManager.getFileSending()))
							throw new IllegalAccessException("path "+path+" does not match "+
								fileTransferManager.getFileSending());
						else if(!file.exists())
							break;//handled outside of loop
						sleepShort();
					}
					
				}
				if(isShuttingDown()){
					logger.log("Large file upload aborted; shutting down");
					return;
				}
				if(!file.exists()){
					fileTransferManager.cancelUpload(path, true, true);
					logger.log("file was deleted so it was not sent "+file.getPath());
					
					fileTransferManager.resetSendInfo();
					return;
				}
				byte[]bytes=new byte[(int)Math.min(offset, size-i)];
				
				//Quickly load all bytes; This method has the benefit of memorizing 
				//memory usage and is not affected by concurrent changes in the file
				map.get(bytes, 0, bytes.length);
				
				//This will be sent to true by MainClient when the receivers responds
				//that they received the packet
				fileTransferManager.sentPacket();
				
				//sends the packet or sends the packet with a signal that the upload is finished
				mainClient.printMessage(
						file.formatFileIntoSyncData(bytes,size),
						(i+offset<size)?HEADER_REQUEST_LARGE_FILE_DOWNLOAD:HEADER_REQUEST_END_LARGE_FILE_DOWNLOAD,
						target
						);
				fileTransferManager.updateTimeSending();
			}
			
			logger.log("done total time:"+(System.currentTimeMillis()-startTime)/1000.0+"s");
			map.clear();
			map=null;
		}
		catch (IllegalAccessException e)
		{
			logger.log(e.toString());
		}
		catch (OutOfMemoryError | SecurityException | IOException e)
		{
			logger.logError(e,"occured while trying to upload large file path="+path) ;
			if(fileTransferManager.isSending()&&mainClient.isConnectionAccepted())
				fileTransferManager.cancelUpload(path,true, !(e instanceof IOException));
		}
	}	
}