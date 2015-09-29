package syncrop;

import static syncrop.ResourceManager.getConfigFilesHome;
import static syncrop.Syncrop.logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import settings.Settings;

/**
 * 
 * Used to handle updates of Syncrop
 *
 */
public class WindowsUpdator {
	/**
	 * Downloads the newest version of Syncrop from the server
	 * @param newVersion the new version to download (for logging purposes)
	 */
	public static void update(){
		String file="syncrop/windows/syncrop-windows-installer.exe";
		try {
			
	        // Create a URL for the desired page
	        URL url = new URL("http://"+Settings.getHost()+"/"+file);
	        File installer=new File(getConfigFilesHome(),"syncrop-windows-installer.exe");

	        
	        Files.copy(url.openStream(), installer.toPath(), StandardCopyOption.REPLACE_EXISTING);
	        installer.setExecutable(true);
	        logger.log("updating");
	        //TODO restart
	        Runtime.getRuntime().exec(installer.getAbsolutePath());
	       
	    } catch (IOException|SecurityException e) {
	    	Syncrop.logger.logError(e, "occured while trying update");
	    }
	}
	/**
	 * Gets the latest version of Syncrop {@link Settings#getHost()}/syncrop_version.html<br/>
	 * The version should be on the first line and the last word on the line. 
	 * @return the latest version of Syncrop or "-1" if the version cannot be determined 
	 */
	private static String getLatestVersionName(){

		String file="syncrop_version.html";
		try {
			logger.logTrace("Checking for update");
	        // Create a URL for the desired page
	        URL url = new URL("http://"+Settings.getHost()+"/"+file);       

	        // Read all the text returned by the server
	        BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
	        String str= in.readLine();
	        in.close();          
            String s[]=str.trim().split(" ");
            String version=s[s.length-1].trim();
            return version;	        
	    } catch (IOException|NumberFormatException e) {
	    	logger.log(e.toString()+" occured while trying to check for updates");
	    }
		return "-1";
	}
	/**
	 * 
	 * @param newVersion the version to compare the current version to.
	 * @return the difference between versions
	 * @see #isMarjorUpdateAvailable()
	 * @see #isMarjorUpdateAvailable()
	 */
	private static double getVersionDiffrence(String newVersion){
		double newVersionValue=Double.parseDouble(newVersion);
		double currentVersionValue=Double.parseDouble(Syncrop.getVersionID());
		return newVersionValue-currentVersionValue;
	}
	/**
	 * 
	 * @return true if there is a newer version of Syncrop available
	 * @see #getLatestVersionName()
	 */
	public static boolean isUpdateAvailable(){
		return getVersionDiffrence(getLatestVersionName())>0;
	}
	/**
	 * 
	 * @return true if there is has been an major update in Syncrop
	 * @see #getLatestVersionName()
	 */
	public static boolean isMarjorUpdateAvailable(){
		return getVersionDiffrence(getLatestVersionName())>1;
	}
	
}