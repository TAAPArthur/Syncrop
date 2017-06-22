package syncrop;

import java.io.IOException;

import daemon.client.SyncropClientDaemon;
import daemon.cloud.SyncropCloud;

public class SyncropApplication {

	public static void main(String[] args) throws IOException {
		//handles parameters
		String instance="";
		boolean startCloud=false;
		if(args.length>0)
			for(String s:args)
				if(s.startsWith("-i"))
					instance=s.substring(2).trim();
				else if(s.startsWith("-v")){
					System.out.println(Syncrop.getVersionID());
					System.exit(0);
				}
				else if (s.startsWith("--cloud"))
					startCloud=true;
		if(startCloud)
			new SyncropCloud(instance);
		else new SyncropClientDaemon(instance);

	}

}
