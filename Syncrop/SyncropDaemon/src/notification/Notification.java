package notification;

import static syncrop.Syncrop.getInstance;
import static syncrop.Syncrop.isNotWindows;
import static syncrop.Syncrop.logger;

import java.io.IOException;

import settings.Settings;
import syncrop.Syncrop;

public class Notification {
	
	private static String pathToImage=null;
	
	public static void initilize(){

		//setup notifications
		if(Settings.showNotifications())//loads Realmofpi symbol for notifications
			pathToImage="/usr/share/pixmaps/syncrop.png";
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
				Runtime.getRuntime().exec(new String []{"notify-send","-i",pathToImage,Syncrop.APPLICATION_NAME+" "+getInstance(),message});
			} catch (IOException|NullPointerException e) {
				Settings.setShowNotifications(false);
				logger.log("can't notify user of message: '"+message+
						"'. Notifications will now be turned off");				
			}
	}
	public static void close(){
	}
}