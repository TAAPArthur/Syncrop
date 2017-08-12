package notification;

import static notification.Notification.displayNotification;
import static syncrop.Syncrop.logger;

import daemon.SyncDaemon;
import logger.Logger;
import settings.Settings;
import syncrop.Syncrop;

public class NotificationManager extends Thread {
	
	final SyncDaemon daemon;
	public NotificationManager(SyncDaemon daemon){
		super("Notification Manager");
		this.daemon=daemon;
	}

	@Override
	public void run() {
		int timeSinceFirstFileTransfered=0;
		while(!Syncrop.isShuttingDown())
		{
			Syncrop.sleep(1000);
			
			if(daemon.getFileTransferManager().getTransferedFiles()==0)continue;
			timeSinceFirstFileTransfered++;
			if(timeSinceFirstFileTransfered==60||daemon.haveAllFilesFinishedTranferring()
				&&daemon.getFileTransferManager().getTimeFromLastCompletedFileTransfer()>2000){
				notifyUser();
				timeSinceFirstFileTransfered=0;
			}
		
		}		
	}
	public void notifyUser()
	{
		String summary="File transfer still in progress";
		if(daemon.getFileTransferManager().haveAllFilesFinishedTranferring()){
			//prefix="All files finished transferring\n";
			summary="\n\nAll files finished transferring";
		}
		String notification="";
		if(daemon.getFileTransferManager().getUploadCount()!=0)
			if(daemon.getFileTransferManager().getUploadCount()==1)
				notification+=daemon.getFileTransferManager().getNameOfUploadedFile()+" was uploaded to cloud";
			else notification+=daemon.getFileTransferManager().getNameOfUploadedFile()+" and "+(daemon.getFileTransferManager().getUploadCount()-1)+
					" other file"+(daemon.getFileTransferManager().getUploadCount()-1==1?"":"s")+" were uploaded to cloud";
		if(daemon.getFileTransferManager().getDownloadCount()!=0){
			if(!notification.isEmpty())notification+="\n\n";
			if(daemon.getFileTransferManager().getDownloadCount()==1)
				notification+=daemon.getFileTransferManager().getNameOfDownloadFile()+" was downloaded from cloud";
			else notification+=daemon.getFileTransferManager().getNameOfDownloadFile()+" and "+(daemon.getFileTransferManager().getDownloadCount()-1)+
					" other file"+(daemon.getFileTransferManager().getDownloadCount()-1==1?"":"s")+" were downloaded from cloud";
		}
		
		displayNotification(Logger.LOG_LEVEL_INFO,summary,notification);
		if(daemon.haveAllFilesFinishedTranferring()&&daemon.isConnectionAccepted())
		{
			
			logger.log("All files finished transferring");
			if(Settings.autoQuit()){
				logger.log("auto quitting");
				System.exit(0);
			}
		}
		daemon.getFileTransferManager().resetTransferRecord();			
	}	
}