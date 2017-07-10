
import static org.junit.Assert.assertEquals;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.junit.Test;

import settings.SettingsManager;
import settings.SettingsManager.Options;
import syncrop.Syncrop;
public class SyncropTester extends Syncrop{
	
	public SyncropTester() throws IOException {
		super("Test");
	}
	protected String getLogFileName(){
		return "tester.log";
	}

	/*
	@Test
	public void createDatabase() throws IOException{
		//SyncropClientDaemon client=
				new SyncropClientDaemon("test");
		assertEquals(true,FileMetadataManager.doesDatabaseExists());
		//SyncropClientDaemon.shutdown();
	}*/
	@Test
	public void testSettings(){
		
		for(Options option:Options.values())
			if(option.getDataType().equals(String.class)){
				option.setValue("hi");
				assertEquals(option.getValue(),"hi");
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
  }