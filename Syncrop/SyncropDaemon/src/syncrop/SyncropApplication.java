package syncrop;

import java.io.IOException;

import daemon.client.SyncropClientDaemon;
import daemon.cloud.SyncropCloud;
import gui.SyncropGUI;

public class SyncropApplication {

	public static void main(String[] args) throws IOException {
		//handles parameters
		String instance="";
		boolean startCloud=false;
		boolean startGUI=false;
		
		if(args!=null)
			for(String s:args){
				if(s.startsWith("-i"))
					instance=s.substring(2).trim();
				else if(s.startsWith("-v")){
					System.out.println(Syncrop.getVersionID());
					System.exit(0);
				}
				else if (s.equals("--cloud"))
					startCloud=true;
				else if (s.equals("--gui"))
					startGUI=true;
			}
		if(startGUI)
			new SyncropGUI(instance,startCloud);
		else if(startCloud)
			new SyncropCloud(instance);
		else new SyncropClientDaemon(instance);

	}

}