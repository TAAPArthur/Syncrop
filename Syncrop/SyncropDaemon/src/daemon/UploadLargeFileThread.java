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

import daemon.client.SyncropClientDaemon;

import file.SyncROPFile;
import transferManager.FileTransferManager;

class UploadLargeFileThread extends Thread
{
	SyncROPFile file;
	String path;
	String target;
	int totalTimeOfTransfer;
	private volatile boolean canUploadFile=false;
	FileTransferManager fileTransferManager;
	
	public UploadLargeFileThread(FileTransferManager fileTransferManager)
	{
		super("upload large file thread");
		this.fileTransferManager=fileTransferManager;
	}
	public void startUploadingOfFile(SyncROPFile file,String path,String target)
	{
		this.file=file; 
		this.path=path;
		this.target=target; 
		canUploadFile=true;
	}
	public boolean isUploadingLargeFile()
	{
		return canUploadFile;
	}
	public void run()
	{
		try {
			while(!isShuttingDown())
				if(canUploadFile)
				{
					uploadFile();
					canUploadFile=false;
				} else
					SyncropClientDaemon.sleep();
		} catch (Exception|Error e) {
			logger.logFatalError(e, "");
			System.exit(0);
		}
	}
	public void uploadFile()
	{
		try 
		{
			long startTime=System.currentTimeMillis();
			MappedByteBuffer map=null;
			map=file.mapAllBytesFromFile();
			//files have to be less than 2GB, which is the maxim Integer
			long size=(int)file.getSize();
			int offset=TRANSFER_SIZE;
			logger.log("uploading large file; size="+size);
			//mainClient.pausePrinting(true);
			//mainClient.logs();
			for(int i=0;i<size;i+=offset)
			{	
				//System.out.println(3.3);
				if(i!=0)
				{
					for(int x=0;!fileTransferManager.canSendNextPacket()&&!isShuttingDown();x++)
					{
						sleepShort();
						//Every 100ms, this loop will be entered
						if(x%5==0)
						{
							//checks to make sure that the file being sent is still the file 
							//that should be sent and that the connection has not closed
							if(!mainClient.isConnectionAccepted())
								throw new IOException("connection lost with server");
							else if(!fileTransferManager.isSending())
								return;
							else if(!path.equals(fileTransferManager.getFileSending()))
								throw new IllegalAccessException("path "+path+" does not match "+
									fileTransferManager.getFileSending());
						}
					}
					if(isShuttingDown())
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