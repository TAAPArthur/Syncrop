package notification;

import static notification.Notification.displayNotification;
import static syncrop.Syncrop.logger;

import settings.Settings;
import syncrop.Syncrop;
import transferManager.FileTransferManager;

public class NotificationManager extends Thread {
	
	final FileTransferManager transferManager;
	public NotificationManager(FileTransferManager transferManager){
		super("Notification Manager");
		this.transferManager=transferManager;
	}

	@Override
	public void run() {
		int timeSinceFirstFileTransfered=0;
		while(!Syncrop.isShuttingDown())
		{
			Syncrop.sleep(1000);
			int sum=transferManager.getDownloadCount()+transferManager.getUploadCount();
			if(sum==0)continue;
			timeSinceFirstFileTransfered++;
			if(Settings.showNotifications())
				if(timeSinceFirstFileTransfered==60||transferManager.haveAllFilesFinishedTranferring()
					&&transferManager.getTimeFromLastCompletedFileTransfer()>2){
					notifyUser();
					timeSinceFirstFileTransfered=0;
				}		
			
		}		
	}
	public void notifyUser()
	{
		String summary="File transfer still in progress";
		if(transferManager.haveAllFilesFinishedTranferring()){
			//prefix="All files finished transferring\n";
			summary="\n\nAll files finished transferring";
		}
		String notification="";
		if(transferManager.getUploadCount()!=0)
			if(transferManager.getUploadCount()==1)
				notification+=transferManager.getNameOfUploadedFile()+" was uploaded to cloud";
			else notification+=transferManager.getNameOfUploadedFile()+" and "+(transferManager.getUploadCount()-1)+
					" other file"+(transferManager.getUploadCount()-1==1?"":"s")+" were uploaded to cloud";
		if(transferManager.getDownloadCount()!=0){
			if(!notification.isEmpty())notification+="\n\n";
			if(transferManager.getDownloadCount()==1)
				notification+=transferManager.getNameOfDownloadFile()+" was downloaded from cloud";
			else notification+=transferManager.getNameOfDownloadFile()+" and "+(transferManager.getDownloadCount()-1)+
					" other file"+(transferManager.getDownloadCount()-1==1?"":"s")+" were downloaded from cloud";
		}
		displayNotification(summary,notification);
		if(transferManager.haveAllFilesFinishedTranferring())
		{
			if(logger.isDebugging())
				logger.log("All files finished transferring");
			if(Settings.autoQuit()){
				logger.log("auto quitting");
				System.exit(0);
			}
		}
		transferManager.resetTransferRecord();			
	}
	
}
