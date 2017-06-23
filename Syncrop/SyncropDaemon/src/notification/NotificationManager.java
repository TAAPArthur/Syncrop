package notification;

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
			int sum=transferManager.getDownloadCount()+transferManager.getUploadCount();
			if(sum!=0)
				timeSinceFirstFileTransfered++;
			if(Settings.showNotifications())
				if(timeSinceFirstFileTransfered==60||
					timeSinceFirstFileTransfered>10&&transferManager.haveAllFilesFinishedTranferring()
					&&transferManager.getTimeFromLastCompletedFileTransfer()>10){
					
					transferManager.notifyUser();
					timeSinceFirstFileTransfered=0;
				}		
			Syncrop.sleep(1000);
		}		
	}
}
