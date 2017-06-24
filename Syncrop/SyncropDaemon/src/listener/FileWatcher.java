package listener;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static syncrop.Syncrop.logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import account.Account;
import daemon.SyncDaemon;
import daemon.client.SyncropClientDaemon;
import file.Directory;
import file.RemovableDirectory;
import file.SyncROPDir;
import file.SyncROPFile;
import file.SyncROPItem;
import file.SyncROPSymbolicLink;
import settings.Settings;
import syncrop.FileMetadataManager;
import syncrop.ResourceManager;
import syncrop.Syncrop;
import syncrop.SyncropCloseException;
import syncrop.SyncropLogger;

public class FileWatcher extends Thread{
	
    private final WatchService watcher;
    private final Map<WatchKey,WatchedDir> keys;
    private final Map<Path,WatchKey> keyMap=new HashMap<>();
    private final RemovableFileWatcher removableFileWatcher;
    final SyncDaemon daemon;
   
    private final HashSet<Command>commands=new HashSet<>();

    public FileWatcher(SyncDaemon daemon) throws IOException{
    	super ("File Listener");
    	this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<WatchKey,WatchedDir>();
        this.daemon=daemon;
        removableFileWatcher=!Syncrop.isInstanceOfCloud()?new RemovableFileWatcher(this):null;
        
    }

    /**
     * Register the given directory with the WatchService
     */
    private void register(Path path,String accessPath,Account account,boolean removable) throws IOException {
    	
    	if(keyMap.containsKey(path)){
    		logger.logTrace("Dir not registered because it is already registered "+path);
    	}
    	else{
    		logger.logAll("Registering "+path);
    		WatchedDir dir=new WatchedDir(path,accessPath,account, removable);
    		WatchKey key = dir.registerDir(watcher);
    		keyMap.put(path, key);
    		keys.put(key, dir);
    	}
    }
    
    public static void checkMetadataForAllFiles(boolean perserveMetadataForDeletedFiles) throws IOException{
    	Iterable<SyncROPItem>items=FileMetadataManager.iterateThroughAllFileMetadata(null);
    	for(SyncROPItem item:items){
    		checkFileMetadata(item,perserveMetadataForDeletedFiles);
    	}
    	
    }
    public static void checkFileMetadata(SyncROPItem file,boolean perserveMetadataForDeletedFiles){
		if(file==null)return;
		if(!file.isEnabled()){
			file.deleteMetadata();
		}
		else if(!file.exists())
			if(!perserveMetadataForDeletedFiles){
				file.deleteMetadata();
			}
			else if(!file.isDeletionRecorded()){
				logger.logTrace("File was deleted while server was off path="+file.getPath());
				file.setDateModified(Syncrop.getStartTime());
			}
		if (file.hasBeenUpdated())
			file.save();
		
    }


    
	public void checkAllFiles(){
		
		for (Account a : ResourceManager.getAllEnabledAccounts())
		{
			//checks regular files
			for (Directory parentDir : a.getDirectories())
				checkFiles(a, parentDir.isLiteral()?parentDir.getDir():"",false);
			//checks removable files
			for (RemovableDirectory parentDir : a.getRemovableDirectories())
				if(parentDir.exists())
					checkFiles(a, parentDir.getDir(),true);
			
			
		}
		logger.log("finshed checking files");
	}

	void checkFiles(Account a,final String baseDir,boolean removable){
		LinkedList<String> queue=new LinkedList<>();
		
		queue.add(baseDir);

		while(!queue.isEmpty()){
			if(Syncrop.isShuttingDown())
				throw new SyncropCloseException();
			String path=queue.pop();
			if(!a.isPathEnabled(path))continue;
			if(Settings.isLimitingCPU()&&Math.random()>.95)
				Syncrop.sleep();
			File file=new File(ResourceManager.getHome(a.getName(),removable),path);
			
			SyncROPItem item=ResourceManager.getFile(path, a.getName());
		
			logger.log("Checking "+path,SyncropLogger.LOG_LEVEL_ALL);
			
			if(!ResourceManager.isLocked(path,a.getName())){
				if(Files.isSymbolicLink(file.toPath())){
					if(item==null){	
						try {
							item=SyncROPSymbolicLink.getInstance(path, a.getName(), file);
						} catch (IOException e) {
							logger.logError(e);
							return;
						}
						if(!item.isEnabled()){
							logger.log(item+" is not enabled");
							return;
						}
						logger.logTrace("detected new file "+path);
					}		
				}
				if(Files.isDirectory(file.toPath())){
					try {
						Syncrop.sleepVeryShort();
						String files[]=file.list();
						//IF cannot read sub dirs
						if(files==null)continue;
						if(item==null){
							item=new SyncROPDir(path,a.getName(),file.lastModified());
						}					
						register(file.toPath(),path,a, removable);
						for(String f:file.list())
							queue.add(path+((path.isEmpty()&&!removable)||path.equals(File.separator)?"":File.separator)+f);
							
					} catch (IOException e) {		
						logger.logError(e, "occured when trying to register dir: "+file);
					}
				}
				else if(item==null){	
					item=new SyncROPFile(path,a.getName());
					if(!item.isEnabled())return;
					logger.logTrace("detected new file "+path);
				}
				
				if(item.hasBeenUpdated()){
					onFileChange(item,item.getAbsPath());
				
					if(SyncDaemon.isInstanceOfCloud()&&item instanceof SyncROPFile)
						((SyncROPFile)item).updateKey();
					item.save();
					
					if(daemon!=null){
						daemon.addToSendQueue(item);
					}
					if(file.exists()){
						if(item.hasBeenUpdated())
							updateAccountSize(a, file, item);
						//if(Syncrop.isInstanceOfCloud())daemon.setPropperPermissions(item, null);
					}
				}
			}	
			//else updateAccountSize(a, file, item);
		}
	}
	
	
	
	
	@Override
	public void start(){
		super.start();
		//this.setPriority(MIN_PRIORITY);
		if(!Syncrop.isInstanceOfCloud())
			new Thread(removableFileWatcher,"Removable File Listener").start();
	}
	
	
	@Override
	public void run(){
		
		while(!SyncropClientDaemon.isShuttingDown())
			try {
					logger.logTrace("Event queue is: "+eventQueue.size());
					listenForDirectoryChanges();
					EventQueueMember member=eventQueue.peek();
					
					if(member!=null&&member.isStable()){
						reactToDirectoryChanges(eventQueue.poll());
						if(daemon!=null&&SyncDaemon.isConnectionActive())
							Thread.sleep(daemon.getExpectedFileTransferTime());
					}
					else Thread.sleep(1000);
			} catch (InterruptedException x) {
				continue;
			}
			catch (IOException e){
				logger.log(e.toString()+" occcured in File Listener ");
			}
			catch (ClosedWatchServiceException e){
				logger.log("Watch service has been closed");
				break;
			}
			catch (Exception |Error e){
				logger.logFatalError(e, "");
				break;
			}
			
		try {
			if(!SyncropClientDaemon.isShuttingDown()){
				logger.log("Exited while loop prematurly shutind down");
				System.exit(0);
			}
			watcher.close();
		} catch (IOException e) {
			logger.logError(e, "occured while trying to close watcher");
		}
		
	}
	PriorityQueue<EventQueueMember>eventQueue=new PriorityQueue<>();
	private void addEvent(WatchedDir dir,String path, WatchEvent.Kind<?>kind){
		EventQueueMember queueMember=new EventQueueMember(dir, path, kind);
		if(eventQueue.contains(queueMember))
			eventQueue.remove(queueMember);
		eventQueue.add(queueMember);
	}
	
	void listenForDirectoryChanges() throws InterruptedException{
		WatchKey key=eventQueue.isEmpty()?watcher.take():watcher.poll();
		if(key==null)return;
		WatchedDir dir = keys.get(key);
		
		List<WatchEvent<?> >events=key.pollEvents();
		
		for (WatchEvent<?>e :events)
			try{
				if(dir==null)continue;//resync maybe?
				String path=dir.getPath()+File.separator+e.context();
				
				if(!ResourceManager.isLocked(path, dir.getAccountName()))
					addEvent(dir, path, e.kind());
			}
			catch (Exception e1){
				logger.logError(e1, "occured while listening for dir changes");
			}
        // reset key and remove from set if directory no longer accessible
		key.reset();
	
	
	}
	
	void reactToDirectoryChanges(EventQueueMember member) throws IOException{
		WatchedDir dir=member.dir;
		String path=member.path;
		
		File file=new File(ResourceManager.getHome(dir.getAccountName(), dir.isRemovable()),path);
		
		SyncROPItem item=ResourceManager.getFile(path, dir.getAccountName());
		//if(!(file.isDirectory()&&e.kind()==ENTRY_MODIFY)&&logger.isLogging(Logger.LOG_LEVEL_ALL))
		//	logger.log("Detected change in file "+file+" "+e.kind(),Logger.LOG_LEVEL_ALL);
		if(!dir.getAccount().isPathEnabled(path))return;
		if(item!=null&&!item.isEnabled())return;

		onFileChange(item,file.getAbsolutePath(),member.getKind());
		
		updateAccountSize(dir.getAccount(),file, item);
		
		if(ResourceManager.isLocked(path,dir.getAccountName())){
			logger.logTrace(path+" is locked");
			return;
		}
		
		if(item==null)
			logger.log("Detected new file: "+file+" event: "+member.getKind());
		else if(item.hasBeenUpdated())
			logger.log("Detected change in file "+file+" event: "+member.getKind());
		
		if(member.getKind()==ENTRY_CREATE)
			onCreate(dir, path, file, item);
		else if(member.getKind()==ENTRY_MODIFY)
			onModify(dir, path, file, item);
		else if(member.getKind()==ENTRY_DELETE)
			onDelete(dir, path, file, item,member.timeStamp);
		else logger.logWarning("Unexpected kind: "+member.getKind());				
	
	}

	void onModify(WatchedDir dir,String path,File file,SyncROPItem item){
		if(item==null)
			onCreate(dir, path, file, item);
		else
			if(item.hasBeenUpdated()){//item.getDateModified()!=item.getFile().lastModified()){
				if(Syncrop.isInstanceOfCloud())
					daemon.setPropperPermissions(item, null);
				daemon.addToSendQueue(item);
				item.save();
			}
	}
	void onCreate(WatchedDir dir,String path,File file,SyncROPItem item){
		String owner=dir.getAccountName();
		if(item==null){
			if(Files.isSymbolicLink(file.toPath())){
				try {
					item=SyncROPSymbolicLink.getInstance(path,dir.getAccountName(), file);
				} catch (IOException e) {
					logger.logError(e);
				}				
				
			}
			else if(file.isDirectory()){
				if(file.list().length==0)
					item=new SyncROPDir(path, owner);						
			}
			else item=new SyncROPFile(path, owner);
			
			if(item!=null){
				if(Syncrop.isInstanceOfCloud())
					daemon.setPropperPermissions(item, null);
				daemon.addToSendQueue(item);
				item.save();
			}
		}
		else {
			logger.log("Deleted file has been recreated; path="+path,SyncropLogger.LOG_LEVEL_TRACE);
			onModify(dir, path, file, item);
		}
		
		if(file.isDirectory()){
			logger.log("New dir detected "+path);
			checkFiles(dir.getAccount(), path, dir.isRemovable());
		}
	
	}
	
	
	
	void onDelete(WatchedDir dir,String relativePath,File file,SyncROPItem item,final long timestamp) throws IOException{
		if(file.exists()){
			logger.logTrace("file exists; deletion canceled");
			return;
		}
		else if(item==null){
			logger.logTrace("no metadata found for"+relativePath);
			return;
		}
		String owner=dir.getAccountName();
		
		if(!item.isDeletionRecorded()){
			Iterable<SyncROPItem>items=FileMetadataManager.getFilesStartingWith(relativePath, owner);
			for(SyncROPItem itemKids:items)
				recordFileDeletion(itemKids, timestamp-1);
			//recordFileDeletion(item, timestamp);
						
			if(item instanceof SyncROPDir)
				if(keyMap.containsKey(item.getFile().toPath())){
					logger.log("canceling key for"+item);
					keys.remove(keyMap.get(item.getFile().toPath()));
					keyMap.remove(item.getFile().toPath()).cancel();
				}
		}
		//TODO rename
	}
	
	
	private void recordFileDeletion(SyncROPItem item,final long timeOfDelete){
		if(item==null)return;
		if(item.exists())
			return;
		if(!item.isDeletionRecorded()){
			logger.log("fileListerner deemed this file to not exists "
					+item.getFile(),SyncropLogger.LOG_LEVEL_DEBUG);
			item.setDateModified(timeOfDelete);	
		}
		if(item.hasBeenUpdated()){
			daemon.addToSendQueue(item);
			item.save();
		}
		
	}
	
	public void onFileChange(SyncROPItem item,String path){onFileChange(item,path,StandardWatchEventKinds.ENTRY_MODIFY);}
	public void onFileChange(SyncROPItem item,String absPath,WatchEvent.Kind<?>  kind){
		try {
			if(Settings.allowScripts())
				for(Command c:commands)
					if(c.isCommandFor(absPath)&&c.canExecute(kind)){
						logger.log("running command for"+absPath);
						c.execute();
					}
				
		} catch (NullPointerException| IOException e1) {
			Settings.setAllowScripts(false);
			logger.logError(e1, "occured while executing command");
		}
	}
	
	public void updateAccountSize(Account account, File file,SyncROPItem item){
		if(item==null)
			account.setRecordedSize(account.getRecordedSize()+file.length());
		else 
			account.setRecordedSize(account.getRecordedSize()-item.getLastKnownSize()+item.getSize());
		
	}
	
	public void watch(JSONArray jsonArray){
		if(jsonArray==null)return;
		logger.log("loading commands ("+jsonArray.size()+")");
		for(int i=0;i<jsonArray.size();i++)
			commands.add(new Command((JSONObject) jsonArray.get(i)));
	}
}