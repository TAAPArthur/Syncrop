
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;

import daemon.client.SyncropClientDaemon;
import syncrop.FileMetadataManager;
public class SyncropTester {
	
	@Test
	public void createDatabase() throws IOException{
		//SyncropClientDaemon client=
				new SyncropClientDaemon("test");
		assertEquals(true,FileMetadataManager.doesDatabaseExists());
		//SyncropClientDaemon.shutdown();
	}
  }