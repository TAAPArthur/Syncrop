import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.Test;

import account.Account;
import daemon.client.SyncropClientDaemon;

public class SyncropClientTest {

	File baseDir=new File(System.getProperty("user.home"),"ClientTest");
	Account account =new Account("test", "", "", true, null, new String[] {baseDir.toString()}, null);
	@Test
	public void fileTransfer() throws IOException, InterruptedException {
		
		Files.createTempDirectory(baseDir.toPath(), "");
		final SyncropClientDaemon client=new SyncropClientDaemon("test",false);
		
		new Thread() {
			public void run() {
				client.startConnection();
			}
		}.start();
		for(int i=0;i<20 && !client.isConnectionAccepted();i++) {
			Thread.sleep(1000);
			if (i==19)
				 fail("could  not connect to cloud");
		}
		
	}

}
