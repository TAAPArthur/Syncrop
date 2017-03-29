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
import java.nio.file.LinkOption;
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

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import account.Account;
import daemon.SyncDaemon;
import daemon.client.SyncropClientDaemon;
import daemon.cloud.filesharing.SharedFile;
import file.Directory;
import file.RemovableDirectory;
import file.SyncROPDir;
import file.SyncROPFile;
import file.SyncROPItem;
import file.SyncROPSymbolicLink;
import settings.Settings;
import syncrop.MetadataWalker;
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
    public static void checkAllMetadataFiles() throws IOException{
    	checkAllMetadataFiles(true);
    }
    public static void checkAllMetadataFiles(final boolean perserveMetadataForDeletedFiles) throws IOException{
    	
    	logger.logTrace("checking metadata");
    	new MetadataWalker(){

			@Override
			public void onMetadataFile(File metaDataFile) {
				SyncROPItem file=ResourceManager.readFile(metaDataFile);
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
						file.save();
					}
				
			}
    		
    	}.walk();
    	
		logger.logTrace("finished checking metadata");

    }

    
	public void checkAllFiles(){
		
		for (Account a : ResourceManager.getAllEnabledAccounts())
		{
			long size=0;
			
			//checks regular files
			for (Directory parentDir : a.getDirectories())
				size+=checkFiles(a, parentDir.isLiteral()?parentDir.getDir():"",false);
			//checks removable files
			for (RemovableDirectory parentDir : a.getRemovableDirectories())
			{
				if(parentDir.exists())
				{
					size+=checkFiles(a, parentDir.getDir(),true);
				}
			}
			a.setRecordedSize(size);
			
		}
		logger.log("finshed checking files");
	}

	long checkFiles(Account a,final String path,boolean removable){

		if(Syncrop.isShuttingDown())
			throw new SyncropCloseException();
		
		if(!a.isPathEnabled(path))return 0;
		long count=0;
		File file=new File(ResourceManager.getHome(a.getName(),removable),path);
		SyncROPItem item=null;
	
		logger.log("Checking "+path,SyncropLogger.LOG_LEVEL_ALL);
		File metadataFile=ResourceManager.getMetadataFile(path, a.getName());
		
		if(!ResourceManager.isLocked(path,a.getName()))
			if(Files.isSymbolicLink(file.toPath())){
				if(!metadataFile.exists()){
					item=tryToCreateSyncropLink(file, path, a,removable);
					if(!item.isEnabled()){
						logger.log(item+" is not enabled");
						return 0;
					}
					logger.logTrace("detected new file "+path);
				}
				else 
					item=ResourceManager.getFile(path, a.getName());
				
				if(Syncrop.isInstanceOfCloud())
					checkIfFileIsShared(item);
				if(Files.isDirectory(item.getFile().toPath())&&(!(item instanceof SyncROPSymbolicLink)||
						!a.isPathEnabled(((SyncROPSymbolicLink) item).getTargetPath())))
					try {
						logger.log("Registering symbolic dir "+file,SyncropLogger.LOG_LEVEL_ALL);
						register(file.toPath(),path,a, removable);
						for(String f:file.list())
							count+=checkFiles(a,path+((path.isEmpty()&&!removable)
								||path.equals(File.separator)?
								"":
									File.separator)+f,removable);
					} catch (IOException e) {
						logger.logError(e, "occured when trying to register dir: "+file);
					}
			}
			else if(Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS)){
				try {
					Syncrop.sleepVeryShort();
					String files[]=file.list();
					//IF cannot read sub dirs
					if(files==null)return 0;
			 		
					if(metadataFile.exists())
						item=ResourceManager.getFile(path, a.getName());
					if(item==null){
						if(metadataFile.exists())
							metadataFile.delete();
						item=new SyncROPDir(path,a.getName(),file.lastModified());
					}
				
					//else daemon.setPropperPermissions(AccountManager.getDirByPath(path, a.getName()));
					
					register(file.toPath(),path,a, removable);
					
					for(String f:file.list())
						checkFiles(a,path+((path.isEmpty()&&!removable)
							||path.equals(File.separator)?
							"":
								File.separator)+f,removable);
				} catch (IOException e) {		
					logger.logError(e, "occured when trying to register dir: "+file);
				}
			}
			else if(!metadataFile.exists()){	
				item=new SyncROPFile(path,a.getName());
				if(!item.isEnabled())return 0;
				logger.logTrace("detected new file "+path);
				
			}
			else {
				item=ResourceManager.getFile(path, a.getName());
				if(item==null){
					logger.logWarning("error occured when reading file. path="+path);
				}
				
			}
		if(file.exists()){
			if(item==null||item.hasBeenUpdated())
				updateAccountSize(a, file, item);
			if(item!=null)
				count+=item.getSize();
			if(Syncrop.isInstanceOfCloud())
				daemon.setPropperPermissions(item, null);
		}
		if(item!=null&&item.hasBeenUpdated()){
			onFileChange(item,item.getAbsPath());
			if(item.getMetadataFile().exists())
				if(SyncDaemon.isInstanceOfCloud()&&item instanceof SyncROPFile)
					((SyncROPFile)item).updateKey();
			item.save();
			
			if(daemon!=null){
				daemon.addToSendQueue(item);
			}
		}
		
		item=null;
		return count;		
	}
	
	public static SyncROPFile tryToCreateSyncropLink(File file,String path, Account a,boolean removable){
		String target;
		try {
			Path targetPath=Files.readSymbolicLink(file.toPath());
			target = targetPath.toString().replace(
					ResourceManager.getHome(a.getName(), removable), "");
			if(a.isPathEnabled(target))
				return new SyncROPSymbolicLink(path,a.getName(),target);
		} catch (IOException e) {
			logger.logWarning(e.toString()+" file was not a symbolic link file="+file);
		}
		return new SyncROPFile(path, a.getName()) ;
	}
	
	public void checkIfFileIsShared(SyncROPItem item){
		if(item==null||!Syncrop.isInstanceOfCloud())return;
		try {
			String targetOfLink=Files.readSymbolicLink(item.getFile().toPath()).toString();
			if(!targetOfLink.startsWith(item.getHome()))
				if(targetOfLink.startsWith(ResourceManager.getSyncropCloudHome())){
					SharedFile sharedFile=ResourceManager.getSharedFile(targetOfLink);
					if(sharedFile!=null){
						if(sharedFile.hasLinkToSharedFile(item.getAbsPath()))
							sharedFile.addLinkToSharedFile(item.getAbsPath());
					}
					else new SharedFile(item.getTargetPath(), item.getOwner(), item.getAbsPath()); 
				}
		} catch (IOException e1) {
			
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
		int delay=100;
		while(!SyncropClientDaemon.isShuttingDown())
			try {
				listenForDirectoryChanges();
				while(!eventQueue.isEmpty()){
					EventQueueMember member=eventQueue.peek();
					if(System.currentTimeMillis()-member.timeStamp>delay*4)
						reactToDirectoryChanges(eventQueue.pop());
					else break;
					logger.logTrace("Event queue is: "+eventQueue.size());
					Thread.sleep(delay);
				}
				Thread.sleep(delay);
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
	class EventQueueMember{
		WatchedDir dir;
		String path;
		WatchEvent.Kind<?>kind;
		long timeStamp=System.currentTimeMillis();
		EventQueueMember(WatchedDir dir,String path, WatchEvent.Kind<?>kind){
			this.dir=dir;
			this.path=path;
			this.kind=kind;
		}
		public WatchEvent.Kind<?> getKind(){return kind;}
		public boolean equals(Object o){
			return o instanceof EventQueueMember&&
					((EventQueueMember)o).dir.equals(dir)&&
					((EventQueueMember)o).path.equals(path);
		}
		public String toString(){
			return "path:"+path+" "+kind;
		}
	}
	LinkedList<EventQueueMember>eventQueue=new LinkedList<>();
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
				addEvent(dir, path, e.kind());
			}
			catch (IllegalArgumentException e1){}
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
			
		if(item!=null&&item.hasBeenUpdated())
			if(Syncrop.isInstanceOfCloud()&&item instanceof SyncROPFile){
				((SyncROPFile) item).updateKey();
				logger.log(item.toString());
				//item.save();
			}
		
		if(item==null||item.hasBeenUpdated())
			logger.log("Detected change in file "+file+" "+member.getKind());
		
		if(member.getKind()==ENTRY_CREATE)
			onCreate(dir, path, file, item);
		else if(member.getKind()==ENTRY_MODIFY)
			onModify(dir, path, file, item);
		else if(member.getKind()==ENTRY_DELETE)
			onDelete(dir, path, file, item,member.timeStamp);
		else logger.log("Unexpected kind: "+member.getKind());				
	
	
	}

	void onModify(WatchedDir dir,String path,File file,SyncROPItem item){
		if(item==null)
			onCreate(dir, path, file, item);
		else
			if(item.hasBeenUpdated()){//item.getDateModified()!=item.getFile().lastModified()){
				if(SyncDaemon.isInstanceOfCloud()&&item instanceof SyncROPFile)
					((SyncROPFile)item).updateKey();
				tryToSendFile(item);
				if(Syncrop.isInstanceOfCloud())
					daemon.setPropperPermissions(item, null);
				item.save();
			}
	}
	void onCreate(WatchedDir dir,String path,File file,SyncROPItem item){
		String owner=dir.getAccountName();
		if(item==null){
			if(Files.isSymbolicLink(file.toPath())){
				item=tryToCreateSyncropLink(file, path, dir.getAccount(),dir.isRemovable());
				checkIfFileIsShared(item);
			}
			else if(file.isDirectory()){
				if(file.list().length==0)
					item=new SyncROPDir(path, owner);						
			}
			else item=new SyncROPFile(path, owner);
			
			if(item!=null){
				if(Syncrop.isInstanceOfCloud())
					daemon.setPropperPermissions(item, null);
				item.save();
				tryToSendFile(item);
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
	
	
	private void tryToSendFile(SyncROPItem item){
		if(SyncropClientDaemon.isConnectionActive())
			if(item.isSyncable()){
				logger.logDebug("Listener is adding files to queue");
				daemon.addToSendQueue(item);
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
			File meatadataDir=new File(ResourceManager.getMetadataFile(relativePath, owner).getParent(),item.getName());
			if(meatadataDir.exists())
				new MetadataWalker(file){
					@Override
					public void onMetadataFile(File metaDataFile){
						SyncROPItem item=ResourceManager.readFile(metaDataFile);
						recordFileDeletion(item, timestamp);
					}
				}.walk();
			//remove file
			recordFileDeletion(item, timestamp);
						
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
		if(!item.isDeletionRecorded()){
			logger.log("fileListerner deemed this file to not exists "
					+item.getFile(),SyncropLogger.LOG_LEVEL_DEBUG);
			item.setDateModified(timeOfDelete);	
		}
		if(item.hasBeenUpdated())
			item.save();
		tryToSendFile(item);
		
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