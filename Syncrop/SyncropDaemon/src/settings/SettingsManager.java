package settings;

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
	
	
	public enum Options{
		HOST("Host",String.class,"getHost","setHost",true),
		PORT("Port",int.class,"getPort","setPort",true),
		LOG_LEVEL("Log Level",int.class,"getLogLevel","setLogLevel",true),
		MAX_ACCOUNT_SIZE("Max Account Size (MB)",double.class,"getMaxAccountSize","setMaxAccountSize",true),
		MULTIPLE_INSTANCES("Multiple Instances",boolean.class,"allowMultipleInstances","setMultipleInstances",true),
		AUTO_QUIT("Auto Quit",boolean.class,"autoQuit","setAutoQuit",true),
		WINDOWS_COMPATIBLE("Windows Compatible",boolean.class,"isWindowsCompatible","setWindowsCompatible",true),
		ALLOW_SCRIPTS("Allow Scripts",boolean.class,"allowScripts","setAllowScripts",true),
		ALLOW_ENCRYPTION("Allow Encryption",boolean.class,"getAllowEncription","setAllowEncription",true),
		
		SHOW_NOTIFICATIONS("Show Notifications",boolean.class,"showNotifications","setShowNotifications",false),
		SYNC_HIDDEN_FILES("Sync Hidden Files",boolean.class,"canSyncHiddenFiles","setSyncHiddenFiles",false),
		AUTO_START("Auto Start",boolean.class,"autoStart","setAutoStart",false);
		String title;
		Class<?> clazz;
		String getterName,setterName;
		boolean advancedOption=false;
		Options(String title,Class<?> clazz,String getterName,String setterName,boolean advancedOption){
			this.title=title;
			this.clazz=clazz;
			this.getterName=getterName;
			this.setterName=setterName;
			this.advancedOption=advancedOption;
		}
		
		public String getName(){return name();}
		public String getTitle(){return title;}
		public Class<?> getType(){return clazz;}
		
		public void setValue(Object value){ 
			try {
				Method setter=Settings.class.getMethod(setterName,clazz);
				setter.invoke(null, value);
				
			} catch (NoSuchMethodException | SecurityException
					| IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				logger.logError(e);
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
		
		File settings=getSettingsFile();
		createSettingsFile();
		try {
			BufferedReader in=new BufferedReader(new InputStreamReader(new FileInputStream(settings)));
			
			while(in.ready())
				interpretSettings(in.readLine());
			in.close();
			
		} catch (FileNotFoundException e) {
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
		Object value=s[1];
		Options option=null;
		try {
			option=Options.valueOf(name);
		} catch (IllegalArgumentException e) {
		}
		if(option!=null){
			if(option.getType().equals(double.class))
				value=Double.parseDouble((String) value);
			else if(option.getType().equals(int.class))
				value=Integer.parseInt((String) value);
			else if(option.getType().equals(boolean.class))
				value=Boolean.parseBoolean((String) value);
				option.setValue(value);
		}
		else logger.log("Unrecognized option when parsing settings: "+s[0],LOG_LEVEL_WARN);		
	}
	
	public void saveSettings() throws FileNotFoundException{
		saveSettings(false);
	}
	public void saveSettings(boolean comment) throws FileNotFoundException{
		File settings=getSettingsFile();
		PrintWriter out=new PrintWriter(settings);
		for(Options option:Options.values())
			out.println((comment?"#":"")+option.name()+":"+option.getValue());
		out.close();
	}
	
	
}
