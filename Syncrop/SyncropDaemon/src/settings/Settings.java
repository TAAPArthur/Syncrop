package settings;

import static syncrop.ResourceManager.HOME;
import static syncrop.ResourceManager.getConfigFilesHome;
import static syncrop.Syncrop.isInstanceOfCloud;
import static syncrop.Syncrop.isNotMac;
import static syncrop.Syncrop.isNotWindows;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

import account.Account;
import syncrop.ResourceManager;
import syncrop.Syncrop;


public class Settings {

	
	/**
	 * Encoding to use to read configuration values
	 */
	private static final String ENCODING="UTF-8";
		
	private static boolean showNotifications=true;
	
	/**
	 * if true Syncrop will exit after initial syncing is complete
	 */
	static private boolean autoQuit=false;
	static private boolean windowsCompatible=true;
	static private boolean allowScripts=false;
	
	private static boolean allowEncription=false;
	private static String encryptionAlgorithm="AES";
	
	/**
	 * Allows for multiple instances of syncrop to be run simultaneously. 
	 *  
	 */
	static private boolean multipleInstances=false;
	/**
	 * Allows for hidden files to be synced
	 */
	static private boolean syncHiddenFiles=true;
	
	/**
	 * The server to connect to
	 */
	static private String host;
	/**
	 * The port to connect on the server
	 */
	static private int port;
	static private boolean autoStart=false;
	
	static final String settingsFileName="syncrop.settings";
	
	
	

	/**
	 * 
	 * @return the server Syncrop is trying to connect to
	 */
	public static String getHost() {return host;}
	public static void setHost(String host) {Settings.host=host;}

	

	/**
	 * 
	 * @return the port on the server Syncrop is trying to connect to
	 */
	public static int getPort() {return port;}
	public static void setPort(int port) {Settings.port=port;}

	/**
	 * 
	 * @return true if and only if Syncrop will immediately exit after the initial sync is complete
	 */
	public static boolean autoQuit() {return autoQuit;}
	public static void setAutoQuit(boolean autoQuit) {Settings.autoQuit=autoQuit;}

	public static boolean allowScripts() {return allowScripts;}
	public static void setAllowScripts(boolean allowScripts) {Settings.allowScripts=allowScripts;}
	
	
	public static boolean isWindowsCompatible() {return windowsCompatible;}
	public static void setWindowsCompatible(boolean windowsCompatible) {Settings.windowsCompatible=windowsCompatible;}
	
	

	/**
	 * 
	 * @return true if and only if multiple instances of Syncrop are allowed to run
	 */
	public static boolean allowMultipleInstances() {return multipleInstances;}
	public static boolean setMultipleInstances(boolean b) {return multipleInstances=b;}

	

	/**
	 * 
	 * @return true if and only if hidden files can be synced
	 */
	public static boolean canSyncHiddenFiles() {return syncHiddenFiles;}
	public static boolean setSyncHiddenFiles(boolean b) {return syncHiddenFiles=b;}
	
	public static int getLogLevel(){return Syncrop.logger.getLogLevel();}
	public static void setLogLevel(int i){Syncrop.logger.setLogLevel(i);}
	
	public static double getMaxAccountSize(){return Account.getMaximumAccountSizeInMegaBytes();}
	public static void setMaxAccountSize(double d){
		Account.setMaximumAccountSize((long)(d*Syncrop.MEGABYTE));}
	
	public static boolean autoStart(){return autoStart;}
	public static void setAutoStart(boolean b) throws IOException{
		createAutoStartFile(b);
		autoStart=b;
	}
	public static void deleteAutoStartFile(){
			File startSyncrop=isNotWindows()&&isNotMac()?
					new File(getConfigFilesHome(),"startSyncrop"+Syncrop.getInstance()):
					new File(HOME,"AppData/Roaming/Microsoft/Windows/Start Menu/Programs/Startup/startSyncrop.bat");
			startSyncrop.delete();
	}
	public static void createAutoStartFile(boolean create) throws IOException{
		if(isNotWindows()&&isNotMac())
		{
			
			if(!isInstanceOfCloud())
			{
				File autoStartSyncrop=new File(HOME,".config/autostart/startSyncrop"+Syncrop.getInstance()+".desktop");
				if(create){
					if(!autoStartSyncrop.exists())
					{
						System.out.println("creating auto start file: "+autoStartSyncrop);
						autoStartSyncrop.createNewFile();
						PrintWriter out=new PrintWriter(autoStartSyncrop);
						out.println("[Desktop Entry]\nEncoding="+ENCODING+"\nVersion="+Syncrop.getVersionID()+"\n" +
								"Type=Application\nName=Syncrop "+Syncrop.getInstance()+"\nComment=Launch Syncrop\n" +
								"Exec= syncrop-daemon start " +Syncrop.getInstance()+
								"\nStartupNotify=false\nTerminal=false\nHidden=false\n");
						out.close();
					}
				}
				else if(autoStartSyncrop.exists())
					autoStartSyncrop.delete();
			}
		}
		else if(isNotMac())
		{
			File startSyncrop=new File(HOME,"AppData/Roaming/Microsoft/Windows/Start Menu/Programs/Startup/startSyncrop.bat");
	
			if(create){
				File installdirinfo=new File(ResourceManager.getConfigFilesHome(),"installdir.txt");
				BufferedReader in=new BufferedReader(new FileReader(installdirinfo));
				String installDir=in.readLine();
				in.close();
				if(!startSyncrop.exists())
				{
					startSyncrop.createNewFile();
					PrintWriter out=new PrintWriter(startSyncrop);
					out.println("java -jar "+installDir+"\\Jars\\SyncropDaemon.jar");
					out.close();
				}
				if(!startSyncrop.canExecute())
					startSyncrop.setExecutable(true);
			}
			else if(startSyncrop.exists())
				startSyncrop.delete();
		}
	}
	
	
	public static boolean showNotifications(){
		return showNotifications;
	}
	public static void setShowNotifications(boolean b){showNotifications=b;}
	
	public static String getEncoding(){return ENCODING;}
	public static String getEncryptionAlgorithm() {
		return encryptionAlgorithm;
	}
	public static void setEncryptionAlgorithm(String encryptionAlgorithm) {
		Settings.encryptionAlgorithm = encryptionAlgorithm;
	}
	public static boolean getAllowEncription() {
		return allowEncription;
	}
	public static void setAllowEncription(boolean allowEncription) {
		Settings.allowEncription = allowEncription;
	}
	
}