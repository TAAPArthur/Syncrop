
import static org.junit.Assert.*;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;

import org.junit.Test;

import account.Account;
import file.SyncropDir;
import file.SyncropFile;
import file.SyncropItem;
import file.SyncropSymbolicLink;
import listener.FileChecker;
import listener.FileWatcher;
import logger.Logger;
import notification.Notification;
import settings.Settings;
import settings.SettingsManager;
import settings.SettingsManager.Options;
import syncrop.FileMetadataManager;
import syncrop.ResourceManager;
import syncrop.Syncrop;
public class SyncropTester extends Syncrop{
	
	public SyncropTester() throws IOException {
		super("Test");
		System.out.println(ResourceManager.getConfigFilesHome());
	}
	protected String getLogFileName(){
		return "tester.log";
	}

	@Test
	public void test() throws IOException {
		testSettings();
		
		SettingsManager.loadDefaultSettings();
		Settings.setHomeDir(ResourceManager.getConfigFilesHome()+"/Test");
		Settings.setCloudHomeDir(ResourceManager.getConfigFilesHome()+"/CloudTest");
		notifyTest();
		ResourceManager.deleteAllAccounts();
		createAccount();
		createDatabase();
		fileChecker();
		try {
			
			ResourceManager.writeConfigFile();
			Settings.setHost("localhost");
			Settings.setSSLConnection(false);
			Settings.setNotificationLevel(10);
			Settings.setAuthenticationScript("true");
			
			SettingsManager.saveSettings(true);
			
			Syncrop.setInstanceOfCloud(true);
			ResourceManager.initializeConfigurationFiles();
			ResourceManager.writeConfigFile();
			
		} catch (IOException e) {
			fail();
		}
		
	}
	
	public void notifyTest(){
		try {
			Notification.displayNotification(Logger.LOG_LEVEL_ALL,"test","test");
			Notification.displayNotification(Logger.LOG_LEVEL_ALL,null,"test");
			Notification.displayNotification(Logger.LOG_LEVEL_ALL,"test",null);
		} catch (Exception e) {
			
			e.printStackTrace();
			assertTrue(false);
		}
	}
	
	
	public void createDatabase(){
		try {
			FileMetadataManager.recreateDatabase();
		} catch (SQLException e) {
			e.printStackTrace();
			assertTrue(false);
		}
		String owner="test";
		new SyncropFile("testFile", owner).save();
		new SyncropDir("testDir", owner).save();
		new SyncropSymbolicLink("testLink", owner,"").save();
		assertNotNull(ResourceManager.getFile("testFile", owner));
		assertNotNull(ResourceManager.getFile("testLink", owner));
		assertNotNull(ResourceManager.getFile("testDir", owner));
	}
	
	public void testSettings(){
		
		for(Options option:Options.values())
			if(option.getDataType().equals(String.class)){
				option.setValue("hi/");
				assertEquals(option.getValue(),"hi/");
			}
			else if(option.getDataType().equals(boolean.class)){
				option.setValue(true);
				assertEquals(option.getValue(), true);
				option.setValue(false);
				assertEquals(option.getValue(), false);
			}
			else if(option.getDataType().equals(long.class)){
				option.setValue(1L);
				option.getValue();
			}
			else if(option.getDataType().equals(int.class)){
				option.setValue(1);
				option.getValue();
			}
		try {
			assertEquals(true,SettingsManager.loadSettings());
			SettingsManager.saveSettings(false);
			assertEquals(true,SettingsManager.loadSettings());
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public void createAccount() {
		Account account=new Account("test", "test@test.com", true);
		account.addDirs("*");
		ResourceManager.addAccount(account);
	}
	public void fileChecker() {
		
		FileChecker a =new FileChecker() {
			protected SyncropItem getItem(String path,File file) {
				SyncropItem item=super.getItem(path, file);
				if(item!=null)
					assertEquals(file, item.getFile());
				return item;
			}
		};
		try {
			FileWatcher watcher=new FileWatcher(null,a);
			watcher.checkAllFiles();
		} catch (IOException e) {
			fail();
		}
	}

	

}