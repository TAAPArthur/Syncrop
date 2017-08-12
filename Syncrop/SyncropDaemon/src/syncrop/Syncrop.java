package syncrop;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import daemon.cloud.SyncropCloud;
import notification.Notification;
import settings.Settings;
import settings.SettingsManager;

/**
 * This class contains general information about the Syncrop 
 * instance that is not user specific
 *
 */
public abstract class Syncrop {

	/**
	 * The version number of Syncrop.<br/>
	 * This value is the value version of the Syncrop DEB.
	 */
	static final private String VERSION_ID="2.4.2";
	static final public String APPLICATION_NAME="Syncrop";
	/**
	 * The version of the metadata. This value is stored in the metadata dir.
	 * When this value differs from the stored value, the metadata is cleared.
	 * A difference indicates that the metadata format has changed significantly and
	 * is not compatible. Clearing the metadata directory is prone to cause conflicts
	 * @see {@link ResourceManager#getMetadataDirectory()}
	 */
	static final private String METADATA_VERSION="6";
	
	/**
	 * 2^10 bytes
	 */
	public static final int KILOBYTE=1024;
	/**
	 * 1024^2 bytes or 2^10 KILOBYTE
	 */
	public static final int MEGABYTE=KILOBYTE*1024;
	/**
	 * 1024^2 KILOBYTE or 2^10 MEGABYTE
	 */
	public static final int GIGABYTE=MEGABYTE*1024;
	
	
	/**
	 * The suffix of the SyncropDaemon; To avoid conflict, 
	 * When multiple instances of Syncrop are running, 
	 * a suffix is appended to all the config files
	 * <br/>
	 * This string is used to allow multiple instances of Syncrop
	 * to run without interferring with each other. 
	 */
	protected static String instance="";
	
	
	/**
	 * If the OS is not a version of windows; 
	 * When this is true, then  Syncrop runs as if it is running on Linux 
	 * else Syncrop rops as if it is on Windows
	 * @see #notMac
	 */
	final static boolean notWindows=!System.getProperty("os.name").toLowerCase().contains("window");
	
	/**
	 * If the OS is a version of OS X; 
	 * When this is true, then  Syncrop runs as if it is running on Linux else 
	 * Syncrop rops as if it is on Linux<br/>
	 * Currently the only difference between Mac and Linux is where the configuration files are stored.
	 * @see #notWindows
	 */
	static boolean notMac=!System.getProperty("os.name").toLowerCase().contains("mac os x");
	/**
	 * the path for windows configuration files; To avoid redundancy, 
	 * the path is gotten once and is stored in this variable;
	 * @see #notWindows
	 */
	
	/**
	 * if the application is run on cloud or not. If true, then the application is treated as if it 
	 * was run on Cloud
	 */
	private static boolean instanceOfCloud;
		
	/**
	 * Used by all classes to log data
	 */
	public static SyncropLogger logger;
	protected String logFileName;
	
	/**
	 * The name of the Syncrop logo located within the jar
	 */
	private static String imageFileName="SyncropIcon.png";
	
	/**
	 * The time Syncrop started running
	 */
	private static long startTime;
	
	/**
	 * true if and only if Syncrop has entered its shutdown method
	 * @see #shutdown()
	 */
	private static volatile boolean shuttingDown=false;
	
	/**
	 * Creates a Syncrop instance with a specified instance String. This string is used to allow multiple instances of Syncrop
	 * to run without interfering with each other.<br/>
	 * This is the same as calling Syncrop(instance, false)
	 *  
	 * @param instance the instance of this Syncrop
	 * @throws IOException
	 * @see {@link #instance}
	 */
	public Syncrop(String instance){
		
		Syncrop.instance=instance;
		Syncrop.instanceOfCloud=(this instanceof SyncropCloud);
		init();
	}

	/**
	 * Creates a Syncrop instance with a specified instance String. This string is used to allow multiple instances of Syncrop
	 * to run without interfering with each other.<br/>
	 *  
	 * @param instance the instance of this Syncrop
	 * @param runAsCloud -run Syncrop as if it was the cloud
	 * @throws IOException
	 * @see {@link #instance}, {@link #isInstanceOfCloud()} 
	 */
	public Syncrop(String instance,boolean runAsCloud){
		Syncrop.instance=instance;
		Syncrop.instanceOfCloud=runAsCloud;
		init();
	}
	
	/**
	 * Sets whether Syncrop runs as Cloud
	 * @param runAsCloud if true, Syncrop will run if it was a Cloud
	 * @see {@link #isInstanceOfCloud()}
	 */
	public static void setInstanceOfCloud(boolean runAsCloud){
		Syncrop.instanceOfCloud=runAsCloud;
	}
	protected String getLogFileName(){
		return "syncrop.log";
	}
	private void init(){
	
		//sets the start time 
		startTime=System.currentTimeMillis();
	
		try {
			//define the logger
			ResourceManager.initializeConfigurationFiles();
			logger=new SyncropLogger(getLogFileName());
			logger.log("Running instance of "+getClass().getName());
			//Loads settings
			SettingsManager.loadSettings();
			
			addShutdownHook();
			
			//initilize Notification;
			Notification.initilize();
			FileMetadataManager.startConnectionSession();
		} catch (IOException|SQLException e) {
			logger.logFatalError(e,"");
			System.exit(1);
		}
		
		//logs basic config info
		logger.log("Version: "+VERSION_ID+":"+METADATA_VERSION+"; Encoding: "+System.getProperty("file.encoding")+
				"; OS: "+System.getProperty("os.name")+
				"; host"+(Settings.isSSLConnection()?" (SSL)":" ")+Settings.getHost()+":"+
				(Settings.isSSLConnection()?Settings.getSSLPort():Settings.getPort())+
				" log level "+logger.getLogLevel());
		if(instanceOfCloud)
			logger.log("HOME:"+Settings.getCloudHomeDir());
		else 
			logger.log("HOME:"+Settings.getHomeDir());
		//if config files cannot be read, quit
		if(!ResourceManager.canReadAndWriteSyncropConfigurationFiles()){			
			logger.log("cannot read and write Syncrop Configuration files",SyncropLogger.LOG_LEVEL_FATAL);
			System.exit(0);
			return;
		}
		ResourceManager.checkMetadataVersion();
		
		//file account config files (.ini file)
		ResourceManager.readFromConfigFile();
		
	}
	
	/**
	 * creates the shutdown hook that will safely kill Syncrop when asked to shutdown or
	 * when an unexpected error occurred.
	 */
	protected void addShutdownHook()
	{
		Runtime.getRuntime().addShutdownHook(
				new Thread("Shutdown-thread") {
	        public void run() 
	        {
	        	quit();
	        }
	    });
	}
	/**
	 * 
	 * @return whether or not Syncrop will as Cloud
	 */
	public static boolean isInstanceOfCloud(){
		return instanceOfCloud;
	}
	
	/**
	 * 
	 * @return true if and only if the OC is not a Mac
	 */
	public static boolean isNotMac(){return notMac;}
	/**
	 * 
	 * @return true if and only if the OC is not a Windows
	 */
	public static boolean isNotWindows(){return notWindows;}
	
	/**
	 * 
	 * @return the version of Syncrop
	 * @see #VERSION_ID
	 */
	public static String getVersionID(){return VERSION_ID;}
	/**
	 * 
	 * @return the metadata version
	 * @see #METADATA_VERSION
	 */
	public static String getMetaDataVersion(){return METADATA_VERSION;}
	/**
	 * returns the instance of Syncrop
	 * @see #instance
	 * @return
	 */
	public static String getInstance(){return instance;}
	
	
	/**
	 * 
	 * @return the name of the Syncrop logo located in the jar
	 */
	public static String getImageFileName(){
		return imageFileName;
	}
	
	/**
	 * Sleeps the current thread by i milliseconds. 
	 * This is a convince method for {@link Thread#sleep(long)} that suppress the error
	 * @param i -milliseconds to sleep
	 */
	public static void sleep(long i)
	{
		try {Thread.sleep(i);} catch (InterruptedException e) {}
	}
	/**
	 * Sleeps the current thread. This method should be used when a 
	 * loop needs to be run after a small, arbitrary amount a time  
	 * This is a convince method for {@link Thread#sleep(long)} that suppress the error
	 */
	public static void sleep(){sleep(100);}
	
	/**
	 * Sleeps the current thread. This method should be used when a 
	 * loop needs to be run after a large, arbitrary amount a time  
	 * This is a convince method for {@link Thread#sleep(long)} that suppress the error
	 * @param i -milliseconds to sleep
	 */
	public static void sleepLong(){sleep(4000);}
	/**
	 * Sleeps the current thread. This method should be used when a 
	 * loop needs to be run after a small, arbitrary amount a time  
	 * This is a convince method for {@link Thread#sleep(long)} that suppress the error
	 * @param i -milliseconds to sleep
	 */
	public static void sleepShort(){sleep(50);}
	/**
	 * Sleeps the current thread. This method should be used when a 
	 * loop needs to be run after an arbitrarily small amount a time  
	 * This is a convince method for {@link Thread#sleep(long)} that suppress the error
	 * @param i -milliseconds to sleep
	 */
	public static void sleepVeryShort(){sleep(10);}
	/**
	 * 
	 * @return the time Syncrop started
	 */
	public static long getStartTime() {
		return startTime;
	}
	
	/**
	 * Call to indicate that Syncrop is shutting down safely
	 */
	public void quit(){
		shuttingDown=true;
		FileMetadataManager.endSession();
	}
	/**
	 * The ClI is shutting down if shutDown is called; This method is called to let
	 * the various threads know to stop so that the SYNCROP can safly shut down
	 * @return true if the SyncropDaemon is shutting down; false otherwise
	 */
	public static boolean isShuttingDown(){return shuttingDown;}
	
	/**
	 * Sends a file to trash bin
	 * TODO Windows and Mac support
	 * @param f the file to send to trash
	 */
	public static void sendToTrash(File file)
	{
		if(!file.exists())return;
		if(logger.isDebugging())
			logger.log("Sending file to trash path="+file);
		
		try {
			if(isNotWindows()&&isNotMac())
				sendToLinuxTrash(file);
			else 
				file.delete();
		}
		
		catch (IOException | InterruptedException e) {
			logger.logError(e, "occured while trying to send file to trash path="+file);
		}
	}
	
	private static void sendToLinuxTrash(File file) throws IOException, InterruptedException
	{
		Process trash =new ProcessBuilder("trash",file.getAbsolutePath()).start();
		if(trash.waitFor()!=0) {
			logger.log("Could not send file to trash; deleting");
			if(file.exists())
				file.delete();
		}
		
		
	
	}

	
}