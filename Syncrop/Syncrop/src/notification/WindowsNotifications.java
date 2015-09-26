package notification;

import static syncrop.Syncrop.logger;

import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.swing.ImageIcon;

import settings.Settings;
import syncrop.WindowsUpdator;

public class WindowsNotifications implements ActionListener
{
	final SystemTray notificationsTray = SystemTray.getSystemTray();
	final PopupMenu popup = new PopupMenu();
	TrayIcon trayIcon=null;
	
	MenuItem help=new MenuItem("Help");
	MenuItem about=new MenuItem("About");
	MenuItem exit=new MenuItem("Exit");
	MenuItem update=new MenuItem("Update");
	
	
	
	void setUpWindowsNotifications() throws AWTException
	{
		Image image=null;
		
		
		URL url=getClass().getResource("/icon.png");
		//url=getClass().getClassLoader().getResource("icon.png");
		//url=ClassLoader.getSystemResource("icon.png");
		
		image = new ImageIcon(url, "Syncrop icon").getImage().
			getScaledInstance(16, 16, Image.SCALE_SMOOTH);
		logger.log(image+"");
		trayIcon=new TrayIcon(image ,"Syncrop");
		help.addActionListener(this);
		about.addActionListener(this);
		update.addActionListener(this);
		exit.addActionListener(this);
		
		
		popup.add(help);
		popup.add(about);
		popup.add(update);
		popup.add(exit);
		
		trayIcon.setPopupMenu(popup);
		notificationsTray.add(trayIcon);
                
	}
	public void addNotification(String message)
	{
		trayIcon.displayMessage("Syncrop", 
				message, 
				TrayIcon.MessageType.INFO);
	}
	void shutdown()
	{
		notificationsTray.remove(trayIcon);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if(e.getSource().equals(help))
			try {
				Desktop.getDesktop().browse(new URI("http://"+Settings.getHost()+"/syncrop.html"));
			} catch (IOException | URISyntaxException e1) {
				logger.logError(e1, "occured when trying to load syncrop.html");
			}
		else if(e.getSource().equals(about))
			trayIcon.displayMessage("Syncrop", 
					"Syncrop is a free online syncronization service hosted on Realm of Pi.", 
					TrayIcon.MessageType.NONE);
		else if(e.getSource().equals(update)){
			if(WindowsUpdator.isUpdateAvailable())
				WindowsUpdator.update();
		}
		else if(e.getSource().equals(exit))
		{
			logger.log("User terminated Syncrop from the notification area");
			System.exit(0);
		}
		
	}
	
	
}