package notification;

import static syncrop.ResourceManager.getConfigFilesHome;
import static syncrop.Syncrop.getInstance;
import static syncrop.Syncrop.isNotWindows;
import static syncrop.Syncrop.logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import settings.Settings;
import syncrop.Syncrop;

public class Notification {
	
	private static String pathToImage=null;
	
	public static void initilize(Class<?>c){

		//setup notifications
		if(Settings.showNotifications())//loads Realmofpi symbol for notifications
			{
				File image=new File(getConfigFilesHome(),Syncrop.getImageFileName());
				if(!image.exists())
					try {
						Files.copy(c.getResourceAsStream("/Resources/SyncropIcon.png"),image.toPath());
					} catch (NullPointerException|IOException e) {
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
		logger.log("Notification: "+message);
		if(isNotWindows())
			try {
				Runtime.getRuntime().exec(new String []{"notify-send","-i",pathToImage,"Syncrop "+getInstance(),message});
			} catch (IOException|NullPointerException e) {
				Settings.setShowNotifications(false);
				logger.log("can't notify user of message: '"+message+
						"'. Notifications will now be turned off");				
			}
	}
	public static void close(){
	}
}