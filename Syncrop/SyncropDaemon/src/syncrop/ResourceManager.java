package syncrop;
import static logger.Logger.LOG_LEVEL_ERROR;
import static notification.Notification.displayNotification;
import static syncrop.Syncrop.isInstanceOfCloud;
import static syncrop.Syncrop.logger;
import static syncrop.Syncrop.notWindows;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.regex.Pattern;

import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import account.Account;
import file.Directory;
import file.SyncROPItem;
import settings.Settings;

/** 
 * 
 * Contains commands for manipulation of accounts. To be used as a static class. 
 * 
 */
public class ResourceManager 
{
	
	public static String METADATA_ENDING=".metadata";

	/**
	 * path to configuration file windows
	 */
	private static String CONF_PATH_WINDOWS=null;
	
	/**
	 * This file stores the user configuration like ID, Account names and passwords<br/>
	 //*Modifying this file while Syncrop is running will cause it to reload the info 
	 */
	static File configFile;
	
	
	
	
	static final String REMOVABLE_DIR_NAME="removable";
	static final String REGULAR_DIR_NAME="regular";
	
	/**
	 * The home folder for the user<br/> 
	 * All non removable file paths are relative to Home
	 */
	public static final String HOME=System.getProperty("user.home");
	private static String SYNCROP_CLOUD_HOME=File.separatorChar+"home/Syncrop"+File.separatorChar;
	
	/**
	 * The name of the directory that holds configuration files
	 */
	public static String configFilesDirName="syncrop";
	
	private static File temp;
	
	
	/**
	 * A String containing characters that are not allowed to be in path names
	 * @see #illegalCharsPattern 
	 */
	public static final String illegalCharsRegex="[<>:\"\\|\\?\\*"+(notWindows?"\\\\":"/")+"]";
	/**
	 * A {@link Pattern} compiled when illegalCharsRegex. It is used to see if a path contains illegal
	 * characters
	 * @see #illegalCharsRegex
	 */
	public final static Pattern illegalCharsPattern=Pattern.compile(".*"+ResourceManager.illegalCharsRegex+".*");
	
	
	private final static HashSet<Account> accounts=new HashSet<Account>(1);
	
	private static String ID;

	/**
	 * Holds the the last know modification date of the {@link #configFile}. 
	 * When the current modification of the config file does not match this value, the config file is 
	 * reloaded;
	 * @see #readFromConfigFile()
	 */
	public static long lastRecordedModificationDateOfConfigFile;
	
	
	private volatile static String lockedPath;
	private volatile static String lockedOwner;
	
	
	
	/**
	 * Gets a single specified Account from a username.
	 * @param username the -name of the Account
	 * @return the account with the name of username or null if not found
	 */
	public static Account getAccount(String username) {
		for(Account account:accounts) 
			if(account.getName().equals(username)) 
				return account;
		return null;
	}
	public static void addAccount(Account account){
		accounts.add(account);
	}
	public static void deleteAccount(Account account) throws IOException{
		account.deleteFolder();
		accounts.remove(account);
	}
	public static Account getAccount() {
		for(Account account:accounts)
			return account;
		return null;
	}
		
	/**
	 * Gets all registered Accounts.
	 * @return ArrrayList of Accounts
	 */
	public static HashSet<Account> getAllAccounts() {
		return accounts;
	}
	public static HashSet<Account> getAllEnabledAccounts() 
	{
		HashSet<Account> enabledAccounts=new HashSet<Account>();
		for(Account a:accounts)
			if(a.isEnabled())
				enabledAccounts.add(a);
		return enabledAccounts;
	}
	public static HashSet<String> getAllEnabledAccountNames() 
	{
		HashSet<String> enabledAccounts=new HashSet<String>();
		for(Account a:accounts)
			if(a.isEnabled()&&a.isAuthenticated())
				enabledAccounts.add(a.getName());
		return enabledAccounts;
	}
	
	public static String getAbsolutePath(String path,String accountName){
		return getHome(accountName, isFileRemovable(path))+path;
	}
	public static String getRelativePath(String absPath,String accountName,boolean removable){
		String home=getHome(accountName, removable);
		String relativePath=absPath.substring(home.length());
		return relativePath;
	}
	public static String getSyncropCloudHome(){return SYNCROP_CLOUD_HOME;}
	
	public static String getOwner(String absPath){
		String temp=absPath.substring(SYNCROP_CLOUD_HOME.length());
		return temp.substring(0, temp.indexOf(File.separatorChar));
	}
	public static String getHome(String accountName,boolean removable)
	{
		//TODO make path settings
		if(Syncrop.isInstanceOfCloud())
			return SYNCROP_CLOUD_HOME+accountName+File.separatorChar+
				(removable?REMOVABLE_DIR_NAME:REGULAR_DIR_NAME)+File.separatorChar;
		else 
			return removable?"":HOME+File.separatorChar;
	}
	

	public static boolean isFileRemovable(String path)
	{
		//TODO removable extra on cloud and Windows
		return Syncrop.isNotWindows()?path.startsWith("/"):path.matches("[A-Z]:\\\\");
	}	
	
	/**
	 * writes to the config file
	 * @throws IOException
	 */
	public static synchronized void writeConfigFile() throws IOException
	{
		Files.copy(configFile.toPath(), new File(configFile.getAbsolutePath()+".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
		PrintWriter out=new PrintWriter(configFile,Settings.getEncoding());
		
		for(Account account:accounts)
		{
			try
			{
				if(Syncrop.isInstanceOfCloud()){
					out.println(account.getName()+"\t"+account.getEmail()+"\t"+account.isEnabled());
				}
				else {
					out.println(account.getName());
					out.println(account.getEmail());
					out.println(account.getToken());
					out.println(account.isEnabled()+"");
					out.println(formatCollection(account.getRestrictions()));
					out.println(formatCollection(account.getDirectories()));
					out.println(formatCollection(account.getRemovableDirectories()));
				}
			}
			catch (NullPointerException e) {
				logger.logError(e, "occurred when saving Account "+account.getName());
			}
		}
		out.close();
		lastRecordedModificationDateOfConfigFile=configFile.lastModified();
	}
	public static boolean hasConfigFileBeenModified()
	{
		return lastRecordedModificationDateOfConfigFile!=configFile.lastModified();
	}
	public static String formatCollection(HashSet<? extends Directory>collection)
	{
		String dirs="";
		if(collection.isEmpty())return dirs;
		
		for(Directory dir:collection)
			dirs+="\t"+dir;
		return dirs.substring(1);
	}
	
	public static File getConfigFile(){return configFile;}
	
	/**
	 * Reads the {@link #configFile} and constructs accounts based on its data. 
	 * 
	 */
	public static void readFromConfigFile()
	{
		accounts.clear();
		
		short numberOfAccounts=0;
		try {
			BufferedReader in=new BufferedReader(new InputStreamReader(new FileInputStream(configFile)));
			while(in.ready())
			{
				numberOfAccounts++;
				try {
					if(Syncrop.isInstanceOfCloud()){
						String s[]=in.readLine().split("\t");
						String username=s[0];//name of account
						String email=s[1];//email of account
						boolean enabled=Boolean.parseBoolean(s[2]);
						if(!SyncROPItem.isValidFileName(username))
							logger.log("Account name:"+username+"  has an invalid char",SyncropLogger.LOG_LEVEL_WARN);
						else if(!SyncROPItem.isValidFileName(email))
							logger.log("Account name:"+email+"  has an invalid char",SyncropLogger.LOG_LEVEL_WARN);
						else {
							File f=new File(getHome(username, false));
							if(!f.exists())f.mkdirs();
							f=new File(getHome(username, true));
							if(!f.exists())f.mkdirs();
							accounts.add(new Account(username, email,enabled));
						}
					}
					else {
						String username=in.readLine();//name of account
						String email=in.readLine();//email of account
						String authToken=in.readLine();
						if(!SyncROPItem.isValidFileName(username))
							logger.log("Account name:"+username+"  has an invalid char",SyncropLogger.LOG_LEVEL_WARN);
						else if(!SyncROPItem.isValidFileName(email))
							logger.log("Account name:"+email+"  has an invalid char",SyncropLogger.LOG_LEVEL_WARN);
						else if(!SyncROPItem.isValidFileName(authToken))
							logger.log("Account name:"+email+"  has an invalid char");
						else 
							accounts.add(new Account(
								username, 
								email,
								authToken,
								Boolean.parseBoolean(in.readLine()),
								in.readLine().split("\t"),//files/dirs that should not be read
								in.readLine().split("\t"),//every parrent dir
								in.readLine().split("\t")//every parrent removable dir
								));
					}
					Syncrop.sleepShort();
					
				}
				
				catch (NoSuchElementException  e) {
					displayNotification("Config file is not formated correctly");
					logger.logError(e,"trying to read config file because it is not formated correctly; "+
					(numberOfAccounts-1)+" accounts read successfully");
					System.exit(0);
				}
				catch (IOException e){
					in.close();
					logger.logError(e,"trying to read from configFile; "+configFile+" is corrupt. Trying to read from back up file");
					if(configFile.getName().contains(".bak")){
						logger.logFatalError(e, "trying to read from backup configFile; "+configFile+" is corrupt.");
						System.exit(0);
					}
						
					File backup=new File(configFile.getAbsolutePath()+".bak");
					if(backup.exists()){
						String s=configFile.getAbsolutePath();
						logger.log("moving back up config file to config file", SyncropLogger.LOG_LEVEL_DEBUG);
						if(configFile.renameTo(new File(s+".corrupt"))&&
						backup.renameTo(new File(s))){
							configFile=backup;
							readFromConfigFile();
							return;
						}
					}
					throw e;
				}
				catch (Exception e) {
					logger.logFatalError(e,"occured while trying to read from config file");
					System.exit(0);
				}
			}
			logger.logDebug("Accounts: "+accounts.toString());
			in.close();
			
		}
		catch (IOException|SecurityException e){
			logger.logFatalError(e,"tyring to read from config file");
			System.exit(0); 
		}
		lastRecordedModificationDateOfConfigFile=configFile.lastModified();
		generateID();
	}
	private static void deleteMetadata() throws IOException{
		logger.log("deleting metadata");
		FileMetadataManager.recreateDatabase();
		
	}
	
	public static SyncROPItem getFile(String relativePath,String owner){
		return FileMetadataManager.getFile(relativePath, owner);
	}	
	
	
	public static  boolean deleteFile(SyncROPItem file){
		return FileMetadataManager.deleteFileMetadata(file);
	}
		
	public static synchronized void lockFile(String path,String owner){
		lockedPath=path;
		lockedOwner=owner;
		//logger.logTrace("locking file path:"+lockedPath+" owner:"+lockedOwner);
	}
	public static void unlockFile(String path,String owner){
		if(isLocked(path,owner)){
			//logger.logTrace("unlocking file path:"+lockedPath+" owner:"+lockedOwner);
			lockedPath=null;
			lockedOwner=null;
		}
	}
	public static boolean isLocked(String path,String owner){
		return owner.equals(lockedOwner)&&path.equals(lockedPath);
	}
	public static void writeFile(SyncROPItem item){
		FileMetadataManager.updateFileMetadata(item);
	}
	
	
	/**
	 * Creates the configuation files if the do not exists
	 */
	public static void initializeConfigurationFiles()
	{
		configFilesDirName+=Syncrop.getInstance();
		if(!new File(getConfigFilesHome()).exists())
			new File(getConfigFilesHome()).mkdirs();
		configFile=new File(getConfigFilesHome(),"syncrop.ini");
				
		temp=new File(getConfigFilesHome(),".temp");
		
		try {
			if(!temp.exists())temp.mkdir();
			if(!configFile.exists())configFile.createNewFile();
			deleteAllTemporaryFiles();
			
		}
		catch (IOException e) 
		{
			System.err.println("trying to create one of the Syncrop configuration files");
			e.printStackTrace();
		}
	}
	public static void checkMetadataVersion(){

		try {			
			boolean sameMetaDataVersion=false;
			File metaDataVersionFile=new File(getConfigFilesHome(),"METADATA_VERSION");
			
			if(FileMetadataManager.doesDatabaseExists()&&FileMetadataManager.doesDatabaseExists()){
				if(metaDataVersionFile.exists()){
					BufferedReader in=new BufferedReader(new InputStreamReader(new FileInputStream(metaDataVersionFile)));
					sameMetaDataVersion=in.readLine().trim().equals(Syncrop.getMetaDataVersion());
					in.close();
				}
				if(!sameMetaDataVersion){
					logger.log("Metadata version is not the same; current version is"+Syncrop.getMetaDataVersion());
					deleteMetadata();	
					updateMetadataVersionFile(metaDataVersionFile);
				}
			}
			else {
				FileMetadataManager.createDatabase();
				updateMetadataVersionFile(metaDataVersionFile);
			}
		}
		catch (IOException e) 
		{
			logger.logError(e);
		}
	}
	private static void updateMetadataVersionFile(File metaDataVersionFile) throws FileNotFoundException{
		PrintWriter out=new PrintWriter(metaDataVersionFile);
		out.print(Syncrop.getMetaDataVersion());
		out.close();
	}
	
	
	/**
	 * Deletes the temporary file if it already exists
	 * @return true if the temporaryFile was deleted successfully
	 */
	public static void deleteAllTemporaryFiles(){
		try {
			for(File f:temp.listFiles())
				if(f.getName().endsWith(".temp"))
					Files.deleteIfExists(f.toPath());
		} catch (IOException e) {
			logger.logError(e,"temps file failed to be deleted: ");
		}
	}
	public static void deleteTemporaryFile(String user,String path){
		try {
			Files.deleteIfExists(getTemporaryFile(user,path).toPath());
		} catch (IOException e) {
			logger.logError(e,"temp file failed to be deleted: "+getTemporaryFile(user,path));
		}
	}
	public static String getTemporaryFilename(String user,String path){
		return "~"+user+path.hashCode()+".temp";
	}
	public static File getTemporaryFile(String user,String path){
		return new File(temp,getTemporaryFilename(user,path));
	}
	
	
	public static File createTemporaryFile(File file){
		File tempFile=new File(temp,(""+Math.random()).substring(2)+file.getName());
		tempFile.deleteOnExit();
		return tempFile;
	}
	
	
	/**
	 * 
	 * @return the path the configuration files on this machine
	 */
	public static String getConfigFilesHome()
	{
		if(notWindows)
			if(Syncrop.notMac)//linux support
				return //Syncrop.isInstanceOfCloud()?File.separatorChar+configFilesDirName+File.separatorChar:
					HOME+File.separatorChar+"."+configFilesDirName;
			else //mac support 
				return HOME+"/Library/Application Support/."+configFilesDirName+"/";
		else//windows support
		{
			if(CONF_PATH_WINDOWS==null)
				setConfigFilesHomeForWindows();
			return CONF_PATH_WINDOWS;
		}
	}
	
	private static void setConfigFilesHomeForWindows()
	{
		try 
		{ 
			Process p = Runtime.getRuntime().exec("cmd /c \"echo %APPDATA%\""); 
			p.waitFor(); 
			Scanner sc = new Scanner(new InputStreamReader(p.getInputStream()));
			CONF_PATH_WINDOWS= sc.nextLine()+File.separatorChar+configFilesDirName;
			sc.close();
		}
		catch ( NullPointerException | IOException | InterruptedException e) 
		{
			logger.log("occured while trying to obtain the config home for Windows",LOG_LEVEL_ERROR,e); 
		}
	}

	
	/**
	 * 
	 * @return returns true if all of the configuration can be read and write
	 * 
	 * @see #canReadAndWriteFile(File)
	 */
	public static boolean canReadAndWriteSyncropConfigurationFiles()
	{
		return canReadAndWriteFile(configFile)&&
				canReadAndWriteFile(logger.getLogFile());
	}
	/**
	 * 
	 * @param f -the file to check
	 * @return true if the file has read write permissions; 
	 * return false if the file does not have read write permissions or if a 
	 * {@link SecurityException} occurred 
	 */
	private static boolean canReadAndWriteFile(File f)
	{
		try {
			if(f.canRead()&&f.canWrite())return true;
			if(!f.canRead())f.setReadable(true);
			if(!f.canWrite())f.setWritable(true);
			return f.canRead()&&f.canWrite();
		} catch (SecurityException e) {
			logger.logError(e, "occured on file:"+f);
			return false;
		}
	}

	
	
	
	public static String convertToPattern(String literal){
		literal="^\\Q"+literal+"\\E";
		literal=literal.replace("*", "\\E.*\\Q");
		literal=literal.replace("?", "\\E.\\Q");
		literal=literal.replace("\\Q\\E", "");
		return literal;
	}

	private static void generateID(){
		if(isInstanceOfCloud())
			ID= "Syncrop Cloud";
		else {
			String computerName="Unknown";
			try {
				computerName = java.net.InetAddress.getLocalHost().getHostName();
			} catch (UnknownHostException e) {
				logger.log("Could not find host name of computer");
			}
			Account a=getAccount();
			String name=a==null?null:a.getName();
			String id=name+"_"+System.getProperty("user.name")+"@"+computerName+System.currentTimeMillis();
			
			logger.log("Current id is "+id);
			ID= id;
		}
	}
	public static String getID(){
		return ID;
	}

	public static void shutDown(){
		logger.close();
	}
	
	public static JSONArray loadCommands(){
		try {
			JSONParser parser=new JSONParser();
			File commandFile=new File(getConfigFilesHome(),"commands.json");
			if(commandFile.exists())
				return (JSONArray)parser.parse(new FileReader(commandFile));
		} catch (IOException | ParseException e) {
			Settings.setAllowScripts(false);
			logger.logError(e, "while trying to load commands");
		}
		return null;
				
	}
}