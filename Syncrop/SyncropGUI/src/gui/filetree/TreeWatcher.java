package gui.filetree;

import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;

import gui.SyncropGUI;

public class TreeWatcher extends Thread{
	private final WatchService watcher;
	public TreeWatcher(File directoryToWatch) throws IOException{
		this.watcher = FileSystems.getDefault().newWatchService();
		directoryToWatch.toPath().register(watcher, ENTRY_DELETE, ENTRY_MODIFY);
	}
	
	@Override
	public void run(){
		while(!SyncropGUI.isShuttingDown()){
			try {
				WatchKey key=watcher.take();
				List<WatchEvent<?> >events=key.pollEvents();
				
				for (WatchEvent<?>e :events){
					//TODO implement
					//String fileName=(String) e.context();
					
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	

}
