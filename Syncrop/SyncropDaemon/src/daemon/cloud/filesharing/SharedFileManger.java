package daemon.cloud.filesharing;

import static syncrop.Syncrop.logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Scanner;

import syncrop.ResourceManager;

public class SharedFileManger {
	
	static HashMap<String, String> sharedFileTokens=new HashMap<>();
	private static File filesSharedByHostInfoFile;
	public static void initializeConfigurationFiles()
	{
		filesSharedByHostInfoFile=new File(ResourceManager.getConfigFilesHome(),"filesSharedByHostInfo.dat");
		
		
		try {
			if(!filesSharedByHostInfoFile.exists())filesSharedByHostInfoFile.createNewFile();
		}
		catch (IOException e) 
		{
			logger.logError(e,"trying to create one of the Syncrop configuration files");
		}
		loadFilesSharedByHostMetaData();
	}
	public static void setSharedFileToken(String token,String absPath){
		sharedFileTokens.put(token,absPath);
		//TODO save;
	}
	public static String getSharableFile(String token){
		return sharedFileTokens.get(token);
	}
	public static void loadFilesSharedByHostMetaData(){
		File file=filesSharedByHostInfoFile;
		try {
			Scanner sc=new Scanner(file);
			while(sc.hasNextLine()){
				String args[]=sc.nextLine().split("\t");
				sharedFileTokens.put(args[0],args[1]);
			}
			sc.close();
		} catch (FileNotFoundException e) {}
	}

}
