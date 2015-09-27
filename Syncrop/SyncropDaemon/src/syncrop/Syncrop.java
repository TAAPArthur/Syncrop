package syncrop;

import java.io.IOException;

import daemon.SyncDaemon;
import daemon.SyncropCloud;
import notification.Notification;
import settings.Settings;
import settings.SettingsManager;

public abstract class Syncrop {

	/**
	 * The version number of Syncrop
	 */
	static final private String VERSION_ID="0.996";
	static final private String METADATA_VERSION="2";
	
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
	 * the maximum size of a file that can be sent. A file with a larger size will 
	 * be considered disabled until its size is less than {@value #MAX_FILE_SIZE}
	 */
	public static final int MAX_FILE_SIZE=Integer.MAX_VALUE;
	
	
	/**
	 * The suffix of the SyncropDaemon; To avoid conflict, 
	 * When multiple instances of Syncrop are running, 
	 * a suffix is appended to all the config files
	 * <br/>
	 * This string is used to allow multiple instances of Syncrop
	 * to run without interferring with each other. 
	 */
	private static String instance="";
	
	
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
	 * A set of Accounts
	 */
	
	
	public static SyncropLogger logger;
	
	private static String imageFileName="SyncropIcon.png";
	
	private static long startTime;
	
	private static volatile boolean shuttingDown=false;
	
	
	
	
	/**
	 * Creates a Syncrop instance with a specifed instance String. This string is used to allow multiple instances of Syncrop
	 * to run without interferring with each other.
	 * @param instance the instance of this Syncrop
	 * @throws IOException
	 * @see {@link #Syncrop()}, {@link #instance}
	 */
	public Syncrop(String instance) throws IOException{
		Syncrop.instance=instance;
		Syncrop.instanceOfCloud=(this instanceof SyncropCloud);
		init();
	}
	public Syncrop(String instance,boolean runAsCloud) throws IOException{
		Syncrop.instance=instance;
		Syncrop.instanceOfCloud=runAsCloud;
		init();
	}
	public static void setInstanceOfCloud(boolean runAsCloud){
		Syncrop.instanceOfCloud=runAsCloud;
	}
	private void init()throws IOException{
		startTime=System.currentTimeMillis();
		logger=new SyncropLogger((this instanceof SyncDaemon?"syncrop":"syncropGUI")+".log");
		Notification.initilize(getClass());
		
		new SettingsManager().loadSettings();
		ResourceManager.initializeConfigurationFiles();
		
		logger.log("Version: "+VERSION_ID+"; Encoding: "+System.getProperty("file.encoding")+
				"; OS: "+System.getProperty("os.name")+"; host "+Settings.getHost()+":"+Settings.getPort()+" log level"+logger.getLogLevel());
				
		if(!ResourceManager.canReadAndWriteSyncropConfigurationFiles())
		{
			logger.log("cannot read and write Syncrop Configuration files",SyncropLogger.LOG_LEVEL_FATAL);
			System.exit(0);
			return;
		}
		ResourceManager.readFromConfigFile();
	
	}
	
	
	public static boolean isInstanceOfCloud(){
		return instanceOfCloud;
	}
	
	public static boolean isNotMac(){return notMac;}
	public static boolean isNotWindows(){return notWindows;}
	
	public static String getVersionID(){return VERSION_ID;}
	public static String getMetaDataVersion(){return METADATA_VERSION;}
	public static String getInstance(){return instance;}
	
	
	
	public static String getImageFileName(){
		return imageFileName;
	}
	
	/**
	 * Causes the currently executing thread to sleep (temporarily cease execution) 
	 * for the specified number of milliseconds, subject to the precision and accuracy of 
	 * system timers and schedulers. The thread does not lose ownership of any monitors.
	 * @param i -milliseconds to sleep
	 */
	public static void sleep(long i)
	{
		try {Thread.sleep(i);} catch (InterruptedException e) {}
	}
	/**
	 * Causes the currently executing thread to sleep (temporarily cease execution) 
	 * for the specified 1000 milliseconds(1 second), subject to the precision and accuracy 
	 * of system timers and schedulers. The thread does not lose ownership of any monitors.
	 * <br/>It is equivalent of calling sleep(1000)
	 */
	public static void sleep(){sleep(500);}
	/**
	 * Causes the currently executing thread to sleep (temporarily cease execution) 
	 * for the specified 12000 milliseconds(12 seconds), subject to the precision and accuracy 
	 * of system timers and schedulers. The thread does not lose ownership of any monitors.
	 * <br/>It is equivalent of calling sleep(12000)
	 */
	public static void sleepLong(){sleep(4000);}
	/**
	 * Causes the currently executing thread to sleep (temporarily cease execution) 
	 * for the specified 100 milliseconds(.1 seconds), subject to the precision and accuracy 
	 * of system timers and schedulers. The thread does not lose ownership of any monitors.
	 * <br/>It is equivalent of calling sleep(100)
	 */
	public static void sleepShort(){sleep(20);}
	public static void sleepVeryShort(){sleep(5);}
	public static long getStartTime() {
		return startTime;
	}
	
	public static  void shutdown(){
		shuttingDown=true;
	}
	/**
	 * The ClI is shutting down if shutDown is called; This method is called to let
	 * the various threads know to stop so that the SYNCROP can safly shut down
	 * @return true if the SyncropDaemon is shutting down; false otherwise
	 */
	public static boolean isShuttingDown(){return shuttingDown;}
	
}