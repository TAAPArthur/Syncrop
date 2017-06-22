package settings;
import static syncrop.Syncrop.GIGABYTE;
import static syncrop.Syncrop.MEGABYTE;
import static logger.Logger.LOG_LEVEL_WARN;
import static syncrop.ResourceManager.getConfigFilesHome;
import static syncrop.Syncrop.isInstanceOfCloud;
import static syncrop.Syncrop.logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import syncrop.SyncropLogger;
import syncrop.Syncrop;
import client.GenericClient;

public class SettingsManager {
	
	private static final int TYPE_SIMPLE=0;
	private static final int TYPE_ADVANCED=1;
	private static final int TYPE_CLOUD=2;
	public enum Options{
		
		HOST("Host",String.class,"getHost","setHost",TYPE_ADVANCED),
		PORT("Port",int.class,"getPort","setPort",TYPE_ADVANCED),
		
		SSL_CONNECTION("Host",boolean.class,"isSSLConnection","setSSLConnection",TYPE_ADVANCED),
		SSL_PORT("SSL Port",int.class,"getSSLPort","setSSLPort",TYPE_ADVANCED),
		
		LOG_LEVEL("Log Level",int.class,"getLogLevel","setLogLevel",TYPE_ADVANCED),
		
		MAX_ACCOUNT_SIZE("Max Account Size (MB)",long.class,"getMaxAccountSize","setMaxAccountSize",TYPE_ADVANCED),
		MAX_FILE_SIZE("Max File Size (MB)",long.class,"getMaxFileSize","setMaxFileSize",TYPE_ADVANCED),
		MAX_TRANSFER_SIZE("Max Transfer Size (MB)",long.class,"getMaxTransferSize","setMaxTransferSize",TYPE_ADVANCED),
		
		MULTIPLE_INSTANCES("Multiple Instances",boolean.class,"allowMultipleInstances","setMultipleInstances",TYPE_ADVANCED),
		AUTO_QUIT("Auto Quit",boolean.class,"autoQuit","setAutoQuit",TYPE_ADVANCED),
		WINDOWS_COMPATIBLE("Windows Compatible",boolean.class,"isWindowsCompatible","setWindowsCompatible",TYPE_ADVANCED),
		ALLOW_SCRIPTS("Allow Scripts",boolean.class,"allowScripts","setAllowScripts",TYPE_ADVANCED),
		ALLOW_ENCRYPTION("Allow Encryption",boolean.class,"getAllowEncription","setAllowEncription",TYPE_ADVANCED),
		
		SHOW_NOTIFICATIONS("Show Notifications",boolean.class,"showNotifications","setShowNotifications",TYPE_SIMPLE),
		SYNC_HIDDEN_FILES("Sync Hidden Files",boolean.class,"canSyncHiddenFiles","setSyncHiddenFiles",TYPE_SIMPLE),
		ALLOW_CONFLICTS("Allow Conflicts",boolean.class,"isConflictsAllowed","setConflictsAllowed",TYPE_ADVANCED),
		CONFLICT_RESOLUTION("Conflict Resolution",int.class,"getConflictResolution","setConflictResolution",TYPE_ADVANCED),
		DELETING_FILES_NOT_ON_CLIENT("Conflict Resolution",boolean.class,"isDeletingFilesNotOnClient","setDeletingFilesNotOnClient",TYPE_ADVANCED);
		
		
		int type;
		String title;
		Class<?> clazz;
		String getterName,setterName;
		
		
		Options(String title,Class<?> clazz,String getterName,String setterName,int type){
			this.title=title;
			this.clazz=clazz;
			this.getterName=getterName;
			this.setterName=setterName;
			this.type=type;
		}
		
		public String getName(){return name();}
		public String getTitle(){return title;}
		public Class<?> getDataType(){return clazz;}
		public int getOptionType(){return type;}
		
		public void setValue(Object value){ 
			try {
				Method setter=Settings.class.getMethod(setterName,clazz);
				setter.invoke(null, value);
				
			} catch (NoSuchMethodException | SecurityException
					| IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				logger.log(e.toString()+";Error setting value:"+value+" "+setterName);
			}
		}
		public Object getValue(){
			try {
				Method getter=Settings.class.getMethod(getterName);
				return getter.invoke(null);
			} catch (NoSuchMethodException | SecurityException
					| IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				logger.logError(e);
			}
			return null;
		}
		
	}
	
	
	/**
	 * Sets default settings
	 */
	private void loadDefaultSettings(){
		
		Settings.setPort(GenericClient.DEFAULT_PORT);
		Settings.setHost(isInstanceOfCloud()?"localhost":GenericClient.DEFAULT_HOST);
	}
	
	public File getSettingsFile(){
		File settings=new File(getConfigFilesHome(),Settings.settingsFileName);
		return settings;
	}
	
	
	public void createSettingsFile() throws IOException{
		File settings=getSettingsFile();
		if(!settings.exists())
			saveSettings(true);
	}
	
	/**
	 * Loads settings if set. These settings override the default settings.
	 * @throws IOException if an io error occurs
	 */
	public void loadSettings() throws IOException
	{
		loadDefaultSettings();
		
		File settingsFile=getSettingsFile();
		createSettingsFile();
		try {
			BufferedReader in=new BufferedReader(new InputStreamReader(new FileInputStream(settingsFile)));
			
			while(in.ready())
				interpretSettings(in.readLine());
			in.close();
			
		}
		catch (IllegalArgumentException e) {
			Syncrop.logger.logError(e);
			System.exit(1);
		}
		catch (FileNotFoundException e) {
			Syncrop.logger.log("file preference file does not exist but file.exists()==true", 
					SyncropLogger.LOG_LEVEL_ERROR,e);
		}
		
	}
	/**
	 * Translates the settings into code
	 * @param line the line of settings to interpret
	 * @throws IOException if an io error occurs
	 */
	private void interpretSettings(String line) throws IOException,IllegalArgumentException
	{
		line=line.trim();
		if(line.contains("#"))
			line=line.substring(0,line.indexOf("#"));
		if(line.isEmpty())return;
		String s[]=line.split(":",2);
		if(s.length==1)return;
		s[0]=s[0].trim().toUpperCase();		
		s[1]=s[1].trim();
		String name=s[0];
		String value=s[1];
		Object formattedValue=null;
		
		Options option=null;
		try {
			option=Options.valueOf(name);
		} catch (IllegalArgumentException e) {
			logger.logWarning(e.toString());
		}
		if(option!=null){
			if(option.getDataType().equals(long.class))
				if( value.endsWith("m")|| value.endsWith("M"))
					formattedValue=MEGABYTE*Long.parseLong(value.substring(0, value.length()-1));
				else if( value.endsWith("g")|| value.endsWith("G"))
					formattedValue=GIGABYTE*Long.parseLong(value.substring(0, value.length()-1));
				else 
					formattedValue=Long.parseLong(value);
			else if(option.getDataType().equals(int.class))
					formattedValue=Integer.parseInt(value);
			else if(option.getDataType().equals(boolean.class))
				formattedValue=Boolean.parseBoolean(value);
			else 
				formattedValue=value;
			option.setValue(formattedValue);
		}
		else logger.log("Unrecognized option when parsing settings: "+s[0],LOG_LEVEL_WARN);		
	}
	
	public void saveSettings() throws FileNotFoundException{
		saveSettings(false);
	}
	public void saveSettings(boolean comment) throws FileNotFoundException{
		
		PrintWriter out=new PrintWriter(getSettingsFile());
		for(Options option:Options.values())
			if(option.getOptionType()!=TYPE_CLOUD)
				out.println((comment?"#":"")+option.name()+":"+option.getValue());
		out.close();
	}
	
	
}
