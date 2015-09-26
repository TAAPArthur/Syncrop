package listener;

import static syncrop.Syncrop.isNotWindows;
import static syncrop.Syncrop.logger;

import java.util.HashSet;

import daemon.SyncropClientDaemon;
import syncrop.ResourceManager;
import syncrop.Syncrop;
import account.Account;
import file.RemovableDirectory;
import file.SyncROPDir;

public class RemovableFileWatcher implements Runnable{
	FileWatcher fileWatcher;

	volatile HashSet<RemovableDirectory>activeRemovaleDirs=new HashSet<>();
	public RemovableFileWatcher(FileWatcher fileWatcher){
		this.fileWatcher=fileWatcher;
	}
	@Override
	public void run() {
		while(!SyncropClientDaemon.isShuttingDown())
		{
			for(Account a:ResourceManager.getAllEnabledAccounts())
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
							} catch (IllegalAccessException e) {
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
	void addActiveRemovableDir(RemovableDirectory dir){
		activeRemovaleDirs.add(dir); 
	}
	void clear(){activeRemovaleDirs.clear();}
	
	void syncNewlyAddedRemovableDir(String path)throws IllegalAccessException
	{
		if(Syncrop.isInstanceOfCloud())
			throw new IllegalAccessException("Cloud cannot new removable dirs");
		if(logger.isDebugging())
			logger.log("Syncing removable dir"+path+"; active removable dirs= "+activeRemovaleDirs);
		if(!isNotWindows())
			path=SyncROPDir.toLinuxPath(path);
		
		logger.log("Syncing newly added removable dirs");
		
		((SyncropClientDaemon) fileWatcher.daemon).syncFilesToCloud(path, path);
	}
}