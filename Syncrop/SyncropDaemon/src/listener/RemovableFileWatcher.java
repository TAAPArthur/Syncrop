package listener;

import static syncrop.Syncrop.isNotWindows;
import static syncrop.Syncrop.logger;

import java.io.IOException;
import java.util.HashSet;

import daemon.client.SyncropClientDaemon;
import syncrop.ResourceManager;
import syncrop.Syncrop;
import account.Account;
import file.RemovableDirectory;
import file.SyncropDir;

/**
 * 
 * Monitors the root directory of removable files to see if to see if it exists.
 * The root directory is the absolute path stored in the configuration file.<br/>
 * When a root removable directory is detected and the connection is active, it is synced to Cloud   
 *
 */
public class RemovableFileWatcher implements Runnable{
	private FileWatcher fileWatcher;

	/**
	 * Set of the root removable directories that exist
	 */
	
	private volatile HashSet<RemovableDirectory>activeRemovaleDirs=new HashSet<>();
	public RemovableFileWatcher(FileWatcher fileWatcher){
		this.fileWatcher=fileWatcher;
	}
	@Override
	public void run() {
		while(!SyncropClientDaemon.isShuttingDown())
		{
			for(Account a:ResourceManager.getAllAuthenticatedAccounts())
				for (RemovableDirectory parentDir : a.getRemovableDirectories())
				{
					if(parentDir.exists())
					{
						if(activeRemovaleDirs.contains(parentDir))
							continue;
						activeRemovaleDirs.add(parentDir);
						fileWatcher.checkFiles(a, parentDir.getDir(),true);
						if(SyncropClientDaemon.isConnectionActive())
							try {
								syncNewlyAddedRemovableDir(parentDir.getDir());
							} catch (IOException|IllegalAccessException e) {
								logger.logError(e);
							}
					}
					else if(activeRemovaleDirs.contains(parentDir))
						activeRemovaleDirs.remove(parentDir);
					SyncropClientDaemon.sleepShort();
				}
			SyncropClientDaemon.sleep();
		}		
	}
	
	/**
	 * Syncs files to path and all its children to Cloud
	 * @param path - the parent path of the files to sync
	 * @throws IllegalAccessException
	 */
	void syncNewlyAddedRemovableDir(String path)throws IOException,IllegalAccessException
	{	
		if(Syncrop.isInstanceOfCloud())
			throw new IllegalAccessException("Cloud cannot new removable dirs");
		if(logger.isDebugging())
			logger.log("Syncing removable dir"+path+"; active removable dirs= "+activeRemovaleDirs);
		if(!isNotWindows())
			path=SyncropDir.toLinuxPath(path);
		
		logger.log("Syncing newly added removable dirs");
		
		((SyncropClientDaemon) fileWatcher.daemon).syncFilesToCloud(path, path);
	
	}
}