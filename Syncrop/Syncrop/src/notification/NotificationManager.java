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
		int count=0;
		while(!Syncrop.isShuttingDown())
		{
			if(transferManager.getDownloadCount()+transferManager.getUploadCount()!=0)
				count++;
			
			if(Settings.showNotifications())
				if(count==30||(count>5&&transferManager.haveAllFilesFinishedTranferring())){
					transferManager.checkForNotifications();
					count=0;
				}
			
			Syncrop.sleep(1000);
		}		
	}

}
