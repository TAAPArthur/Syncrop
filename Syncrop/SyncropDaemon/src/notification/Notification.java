package notification;

import static syncrop.Syncrop.getInstance;
import static syncrop.Syncrop.isNotWindows;
import static syncrop.Syncrop.logger;

import java.io.IOException;

import logger.Logger;
import settings.Settings;
import syncrop.Syncrop;

public class Notification {
	
	private static String pathToImage=null;
	
	public static void initilize(){
		//loads Realmofpi symbol for notifications
		pathToImage="/usr/share/pixmaps/syncrop.png";
	}
	
	/**
	 * Notifies the user for updates
	 * <br/><b>Only works with Linux and Windows</b>
	 * @param message -the notification message
	 */
	public static synchronized void displayNotification(String summary){
		displayNotification(Logger.LOG_LEVEL_INFO,summary,"");
	}
	public static synchronized void displayNotification(int logLevel,String summary){
		displayNotification(logLevel,summary,"");
	}
	public static synchronized void displayNotification(int logLevel,String summary,String body)
	{
		if(!Settings.isShowingNotifications(logLevel))return;
		logger.log("Notification: "+summary+" body:"+body);
		if(isNotWindows())
			try {
				Runtime.getRuntime().exec(new String []{"notify-send","--app-name",Syncrop.APPLICATION_NAME+" "+getInstance(),"-i",pathToImage,
						summary+"",body+""});
			} catch (IOException|NullPointerException e) {
				Settings.setNotificationLevel(-1);
				logger.logError(e,"can't notify user of message: '"+body+
						"'. Notifications will now be turned off");				
			}
	}
	public static void close(){
	}
}