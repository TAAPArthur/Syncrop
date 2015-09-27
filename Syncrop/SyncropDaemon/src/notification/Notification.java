package notification;

import static syncrop.ResourceManager.*;
import static syncrop.Syncrop.*;

import java.awt.AWTException;
import java.awt.SystemTray;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import settings.Settings;
import syncrop.Syncrop;

public class Notification {
	private static WindowsNotifications windowsNotifications=null;
	private static String pathToImage=null;
	
	public static void initilize(Class<?>c){

		//setup notifications
		if(Settings.showNotifications())
			if(!isNotWindows())
				if(SystemTray.isSupported())
				{
					windowsNotifications=new WindowsNotifications();
					try {
						windowsNotifications.setUpWindowsNotifications();
					} 
					catch (AWTException e) {
						Settings.setShowNotifications(false);
						logger.logError(e, "occured while trying to setup Windows notifications");
					}
				}
				else Settings.setShowNotifications(false);
			else //loads Realmofpi symbol for notifications
			{
				File image=new File(getConfigFilesHome(),Syncrop.getImageFileName());
				if(!image.exists())
					try {
						Files.copy(c.getResourceAsStream("/SyncropIcon.png"),image.toPath());
					} catch (IOException e) {
						logger.logError(e, "; Image could not be created");
					}
				pathToImage=image.getAbsolutePath();
				//AccountManager.log("path to image="+pathToImage);
			}
	}
	
	/**
	 * Notifies the user for updates
	 * <br/><b>Only works with Linux and Windows</b>
	 * @param message -the notification message
	 */
	public static void displayNotification(String message)
	{
		if(!Settings.showNotifications())return;
		if(isNotWindows())
			try {
				Runtime.getRuntime().exec(new String []{"notify-send","-i",pathToImage,"Syncrop "+getInstance(),message});
			} catch (IOException|NullPointerException e) {
				Settings.setShowNotifications(false);
				logger.logError(e, 
						"occured while trying to notify user of message '"+message+
						"'. Notifications will now be turned off");				
			}
		else 
		{
			if(windowsNotifications!=null)
				windowsNotifications.addNotification(message);
		}
	}
	public static void close(){
		if(windowsNotifications!=null)
			windowsNotifications.shutdown();
		
	}
}