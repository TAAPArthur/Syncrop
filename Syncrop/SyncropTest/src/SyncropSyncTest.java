

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static syncrop.Syncrop.logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;

import daemon.client.SyncropClientDaemon;
import file.SyncropDir;
import file.SyncropFile;
import file.SyncropItem;
import listener.FileChecker;
import listener.FileWatcher;
import listener.actions.SyncROPFileAction;
import settings.Settings;
import settings.SettingsManager;
import syncrop.FileMetadataManager;
import syncrop.ResourceManager;
import syncrop.Syncrop;

public class SyncropSyncTest extends Syncrop{

		static SyncROPFileAction fileActions=new ErrorConflictsAction();
		final static String pathToJar="/home/arthur/Documents/Syncrop/Jars/Syncrop.jar";
		static Process cloudProcess;
		static SyncropClientDaemon daemon;
		static Connection conn;
		public static void main(String args[]) {
			try {
				new SyncropSyncTest();
			} catch (Error|Exception e) {
				e.printStackTrace();
				
			}
			System.exit(1);
		}
		public SyncropSyncTest() throws IOException, InterruptedException, SQLException{
			super("Test",false);
			runTest();
		}
		@Override
		public void stopThreads() {
			super.quit();
			cloudProcess.destroy();
			daemon.quit();
			System.out.println("quiting");
		}
		static void deleteCloudFiles() throws IOException {
			modifyCloudFiles(true);
		}
		static void modifyCloudFiles(final boolean delete) throws IOException  {
			File file=new File(ResourceManager.getHome(ResourceManager.getAccount().getName(), false,true));
			if(!file.exists())return;
			Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {
				   @Override
				   public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				       if(delete)
				    	   Files.delete(file);
				       else file.toFile().setLastModified(System.currentTimeMillis());
				       return FileVisitResult.CONTINUE;
				   }

				   @Override
				   public FileVisitResult postVisitDirectory(Path file, IOException exc) throws IOException {
					   if(delete)
				    	   Files.delete(file);
				       else 
				    	   file.toFile().setLastModified(System.currentTimeMillis());
				       return FileVisitResult.CONTINUE;
				   }
				});
		}
		static void runTest() throws IOException, InterruptedException, SQLException {
			
			conn=DriverManager.getConnection("jdbc:"+SettingsManager.getDefaultDatabaseFile(true)+"?autoReconnect=true",Settings.getDatabaseUsername(),Settings.getDatabasePassword());
			ResourceManager.getMetaDataVersionFile().delete();
			deleteCloudFiles();
			cloudProcess = new ProcessBuilder("java", "-jar",pathToJar, "-iTest","--cloud").start();
			
			//clientProcess = new ProcessBuilder("java", "-jar",pathToJar, "-iTest").start();
			Thread.sleep(2000);
			ResourceManager.getMetaDataVersionFile().delete();
			daemon=new SyncropClientDaemon("Test",false);
			new Thread() {
				public void run() {
					daemon.startConnection();
				}
			}.start();
			System.out.println("starting test");
			
			waitForConnection();
			testSync();
			daemon.disconnect();
			clientSync();
			cloudSync();
			conflictHandeling();
			System.out.println("ending test");
			Thread.sleep(4000);
			
		}
		static void conflictHandeling() throws IOException {
			String name="test.txt";
			File cloudParent=new File(ResourceManager.getHome(ResourceManager.getAccount().getName(), false,true));
			SyncropItem test=new SyncropFile(name, ResourceManager.getAccount().getName());
			File cloudFile=new File(cloudParent,name);
			test.createFile(System.currentTimeMillis());
			testSync();
			daemon.disconnect();
			testSync();
			daemon.disconnect();
			cloudFile.setLastModified(0);
			long now=System.currentTimeMillis();
			test.createFile(now);
			testSync();
			test=new SyncropFile(name, ResourceManager.getAccount().getName());
			assert(test.hasSameDateModifiedAs(cloudFile.lastModified()));
			assert(test.hasSameDateModifiedAs(now));
			SyncropItem conflict=new SyncropFile(name+SyncropItem.CONFLICT_ENDING+"1", ResourceManager.getAccount().getName());
			assert(conflict.exists());
			conflict.delete(now);
			testSync();
		}
		static void cloudSync() throws IOException {
			System.out.println("starting cloud test");
			daemon.getFileTransferManager().pause(true);
			modifyCloudFiles(false);
			daemon.disconnect();
			testSync();
			File cloudParent=new File(ResourceManager.getHome(ResourceManager.getAccount().getName(), false,true));
			File file=new File(cloudParent,System.currentTimeMillis()+"");
			file.createNewFile();
			File dir=new File(cloudParent,System.currentTimeMillis()+"");
			dir.mkdir();
			testSync();
			file.setLastModified(System.currentTimeMillis());
			testSync();
			file.delete();
			dir.delete();
			testSync();
		}
		static void clientSync() throws IOException {
			System.out.println("starting client test");
			waitForConnection();
			testSync();
			SyncropItem file=new SyncropFile(System.currentTimeMillis()+"", ResourceManager.getAccount().getName());
			file.createFile();
			SyncropItem dir=new SyncropDir(System.currentTimeMillis()+"", ResourceManager.getAccount().getName());
			dir.createFile();
			testSync();
			file.delete(System.currentTimeMillis());
			dir.delete(System.currentTimeMillis());
			testSync();
			daemon.disconnect();
			waitForConnection();
			file.createFile();
			testSync();
			file.setDateModified(System.currentTimeMillis());
			testSync();
		}
		static boolean testSync() throws IOException {
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {}
			waitForFilesToFinishTransfering();
			return areAllFilesSynced();
		}
		static boolean areAllFilesSynced() throws IOException {
			System.out.println("checking if files match");
			final HashMap<String, SyncropItem>map=new HashMap<>();
			FileChecker local =new FileChecker() {
				protected SyncropItem getItem(String path,File file) {
					SyncropItem item=super.getItem(path, file);
					map.put(path, item);
					
					return item;
				}
			};
			
			FileWatcher watcher=new FileWatcher(null,local);
			watcher.checkAllFiles(fileActions);
			File file=new File(ResourceManager.getHome(ResourceManager.getAccount().getName(), false));
			Files.walkFileTree(file.toPath(), new FileChecker() {
				@Override
		    	public FileVisitResult preVisitDirectory(Path file, BasicFileAttributes attrs) {
					stack.push(compare(file));
					return FileVisitResult.CONTINUE;
		        }
				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					stack.pop();
					return FileVisitResult.CONTINUE;
		        }
		    	@Override
		    	public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
		    		compare(file);
		    		return FileVisitResult.CONTINUE;
		        }
		    	SyncropItem compare(Path file) {
		    		String path=getPath(file);
		    		
		    		SyncropItem item=SyncropItem.getInstance(path, ResourceManager.getAccount().getName(), file.toFile());
		    		SyncropItem localItem=map.get(path);
		    		SyncropItem item2 = null;
					try {
						item2 = FileMetadataManager.getFile(item.getPath(), ResourceManager.getAccount().getName(), conn);
					} catch (SQLException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
						assert(false);
					}
		    		try {
		    			
						assertEquals(SyncropItem.SyncropPostCompare.SKIP,localItem.compare(item));
						if(item2!=null)
							assertEquals(SyncropItem.SyncropPostCompare.SKIP,localItem.compare(item2));
						map.remove(path);
					} catch (NullPointerException|IOException e) {
						System.out.println(path);
						e.printStackTrace();
						fail();
					}
		    		return item;
		    	}
		        
		    });
			assertEquals(true, map.isEmpty());
			return true;
		}
		static void waitForConnection() {
			for(int i=0;i<10&&!SyncropClientDaemon.isConnectionActive();i++) {
				try {Thread.sleep(1000);} catch (InterruptedException e) {}
			}
			if(!SyncropClientDaemon.isConnectionActive())
				throw new AssertionError("not connected");
		}
		static void waitForFilesToFinishTransfering() {
			for(int i=0;i<60&&!daemon.haveAllFilesFinishedTranferring();i++) {
				try {Thread.sleep(1000);} catch (InterruptedException e) {}
			}
			if(!daemon.haveAllFilesFinishedTranferring())
				throw new AssertionError("files have not finished transfering");
		}
		
		protected String getLogFileName(){
			return "tester.log";
		}
}
class ErrorConflictsAction implements SyncROPFileAction{

	@Override
	public boolean performOn(SyncropItem item) {
		File file=item.getFile();
		if(file.isFile()&&file.getName().matches(".*"+SyncropFile.CONFLICT_ENDING+"\\d+")){
			logger.log("deleting file path="+file.getAbsolutePath());
			file.delete();
			return true;
		}
		else return false;
	}
}