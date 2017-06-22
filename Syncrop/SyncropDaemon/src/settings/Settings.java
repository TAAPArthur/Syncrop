package settings;

import static syncrop.ResourceManager.HOME;
import static syncrop.ResourceManager.getConfigFilesHome;
import static syncrop.Syncrop.GIGABYTE;
import static syncrop.Syncrop.MEGABYTE;
import static syncrop.Syncrop.isInstanceOfCloud;
import static syncrop.Syncrop.isNotMac;
import static syncrop.Syncrop.isNotWindows;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

import syncrop.ResourceManager;
import syncrop.Syncrop;


public class Settings {

	
	
	/**
	 * the maximum size of a file that can be sent. A file with a larger size will 
	 * be considered disabled until its size is less than {@value #MAX_FILE_SIZE}
	 */
	private static long maxFileSize=Integer.MAX_VALUE;
	/**
	 * The maxium size of an account measured in bytes
	 */
	private static long maximumAccountSize=4L*GIGABYTE;
	/**
	 * the maximum package size of a file being transfered. If the file size is less
	 * than this value, {@value #transferSize}, the entire file will be sent at once.
	 * 
	 * The highest value this can be is {@link Integer#MAX_VALUE}
	 */
	private static long transferSize=MEGABYTE;
	
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
	private static boolean sslConnection=false;
	
	
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
	static private int sslPort;
	static private boolean autoStart=false;
	
	static final String settingsFileName="syncrop.settings";
	static final String cloudSettingsFileName="syncrop-cloud.settings";
	
	
	
	public static final int DEFAULT=0;
	public static final int LOCAL_FILE_ALWAYS_WINS=1;
	public static final int LOCAL_FILE_ALWAYS_LOSES=-1;
	static int conflictResolution=0;
	public static int getConflictResolution(){return conflictResolution;}
	public static void setConflictResolution(int conflictResolution){Settings.conflictResolution=conflictResolution;}
	static boolean allowConflicts=true;
	public static boolean isConflictsAllowed(){return allowConflicts;}
	public static void setConflictsAllowed(boolean conflictsAllowed){allowConflicts=conflictsAllowed;}
	
	static boolean deletingFilesNotOnClient=false;
	public static boolean isDeletingFilesNotOnClient(){return deletingFilesNotOnClient;}
	public static void setDeletingFilesNotOnClient(boolean b){deletingFilesNotOnClient=b;}
	

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
	public static int getSSLPort() {return sslPort;}
	public static void setSSLPort(int port) {Settings.sslPort=port;}

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
	
	public static long getMaxAccountSize(){
		return maximumAccountSize;
	}
	public static void setMaxAccountSize(long size){
		maximumAccountSize=size;
	}
	public static long getMaxFileSize(){
		return maxFileSize;
	}
	public static void setMaxFileSize(long size){
		maxFileSize=size;
	}
	public static long getMaxTransferSize(){
		return transferSize;
	}
	public static void setMaxTransferSize(long size){
		if(size>Integer.MAX_VALUE)
			throw new IllegalArgumentException("valye must be less than 2GB");
		transferSize=size;
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
	
	public static boolean isSSLConnection(){
		return sslConnection;
	}
	public static void setSSLConnection(boolean b){
		sslConnection=b;
	}
	
}