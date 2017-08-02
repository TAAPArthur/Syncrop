package listener;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static syncrop.Syncrop.logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
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
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Scanner;

import account.Account;
import daemon.SyncDaemon;
import daemon.client.SyncropClientDaemon;
import file.Directory;
import file.RemovableDirectory;
import file.SyncropDir;
import file.SyncropItem;
import listener.actions.SyncROPFileAction;
import settings.Settings;
import syncrop.FileMetadataManager;
import syncrop.ResourceManager;
import syncrop.Syncrop;
import syncrop.SyncropLogger;

public class FileWatcher extends Thread{
	
    private final WatchService watcher;
    private final Map<WatchKey,WatchedDir> keys;
    private final Map<Path,WatchKey> keyMap=new HashMap<>();
    private final RemovableFileWatcher removableFileWatcher;
    final SyncDaemon daemon;
   
    private final HashSet<Command>commands=new HashSet<>();

    private FileChecker checker;
    public FileWatcher(SyncDaemon daemon) throws IOException {
    	this(daemon, new FileChecker());
    }
    public FileWatcher(SyncDaemon daemon,FileChecker checker) throws IOException{
    	super ("File Listener");
    	this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<WatchKey,WatchedDir>();
        this.daemon=daemon;
        removableFileWatcher=!Syncrop.isInstanceOfCloud()?new RemovableFileWatcher(this):null;
        
        this.checker=checker;
        checker.setFileWatcher(this);
        
    }

    /**
     * Register the given directory with the WatchService
     */
    protected boolean register(Path path,String accessPath,Account account,boolean removable) {
    	
    	if(keyMap.containsKey(path)){
    		logger.logTrace("Dir not registered because it is already registered "+path);
    		return false;
    	}
    	else{
    		try {
				logger.logAll("Registering "+path);
				WatchedDir dir=new WatchedDir(path,accessPath,account, removable);
				WatchKey key = dir.registerDir(watcher);
				keyMap.put(path, key);
				keys.put(key, dir);
				return true;
			} catch (IOException e) {
				logger.logError(e, "occured when trying to register dir: "+path);
				return false;
			}
    	}
    }
    
    public static void checkMetadataForAllFiles(boolean perserveMetadataForDeletedFiles) throws IOException{
    	logger.logTrace("checking metadata; perserveMetadataForDeletedFiles-"+perserveMetadataForDeletedFiles); 
    	Iterable<SyncropItem>items=FileMetadataManager.iterateThroughAllFileMetadata(null);
    	for(SyncropItem item:items){
    		checkFileMetadata(item,perserveMetadataForDeletedFiles);
    	}
    	
    }
    public static void checkFileMetadata(SyncropItem file,boolean perserveMetadataForDeletedFiles){
		if(file==null)return;
		if(!file.isEnabled()){
			file.deleteMetadata();
		}
		else if(!file.exists())
			if(!perserveMetadataForDeletedFiles){
				file.deleteMetadata();
				return;
			}
			else if(file.knownToExists()){
				logger.logTrace("File was deleted while server was off path="+file.getPath());
				file.setDateModified(Syncrop.getStartTime());
			}
		if (file.hasBeenUpdated())
			file.save();
		
    }


    
	public void checkAllFiles(SyncROPFileAction... fileActions){
		
		checker.setFileActions(fileActions);
	
		for (Account a : ResourceManager.getAllAuthenticatedAccounts()){
			//checks regular files
			for (Directory parentDir : a.getDirectories()) 
				checkFiles(a, parentDir.isLiteral()?parentDir.getDir():"", false);
			
			//checks removable files
			for (RemovableDirectory parentDir : a.getRemovableDirectories()) 
				if(parentDir.exists())
					checkFiles(a, parentDir.getDir(), true);
			
		}
	
		logger.log("finshed checking files");
	}
	public void checkFiles(Account account, String path, boolean removable){
		checker.setDir(account, removable, path);
		try {
			File startingFile=new File(ResourceManager.getAbsolutePath(path, account.getName()));
			if(startingFile.exists())
				Files.walkFileTree(startingFile.toPath(), checker);
		} catch (IOException e) {
			logger.logError(e);
		}
	}

	
	
	@Override
	public void start(){
		super.start();
		//this.setPriority(MIN_PRIORITY);
		if(!Syncrop.isInstanceOfCloud())
			new Thread(removableFileWatcher,"Removable File Listener").start();
	}
	
	public boolean isFileQueueEmpty() {
		return eventQueue.isEmpty();
	}
	public int getFileQueueSize() {
		return eventQueue.size();
	}
	@Override
	public void run(){
		while(!SyncropClientDaemon.isShuttingDown())
			try {
				listenForDirectoryChanges();
				EventQueueMember member=eventQueue.peek();
				
				if(member!=null&&member.isStable()){
					reactToDirectoryChanges(eventQueue.poll());
					if(daemon!=null&&SyncDaemon.isConnectionActive())
						Thread.sleep(daemon.getExpectedFileTransferTime());
				}
				else Thread.sleep(100);
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
				logger.log("Exited while loop prematurly shuting down");
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
		
		SyncropItem item=ResourceManager.getFile(path, dir.getAccountName());
		//if(!(file.isDirectory()&&e.kind()==ENTRY_MODIFY)&&logger.isLogging(Logger.LOG_LEVEL_ALL))
		//	logger.log("Detected change in file "+file+" "+e.kind(),Logger.LOG_LEVEL_ALL);
		if(item==null&&!dir.getAccount().isPathEnabled(path)||item!=null&&!item.isEnabled()){
			logger.logTrace(path+" is not enabled; skipping");
			return;
		}

		updateAccountSize(dir.getAccount(),file, item);
		
		if(ResourceManager.isLocked(path,dir.getAccountName())){
			logger.logTrace(path+" is locked");
			return;
		}
		onFileChange(item,file.getAbsolutePath(),member.getKind());
		 
		if(item==null&&member.getKind()!=ENTRY_DELETE||item!=null&&item.hasBeenUpdated())
			logger.logTrace("Detected change in file "+file+" event: "+member.getKind()+" "+item);
		else if(member.getKind()!=ENTRY_DELETE)
			logger.logTrace("Detected change in file "+file+" event: "+member.getKind()+" but no diff "+file);
		
		if(member.getKind()==ENTRY_CREATE)
			onCreate(dir, path, file, item);
		else if(member.getKind()==ENTRY_MODIFY)
			onModify(dir, path, file, item);
		else if(member.getKind()==ENTRY_DELETE)
			onDelete(dir, path, file, item,member.timeStamp);
		else logger.logWarning("Unexpected kind: "+member.getKind());				
	
	}

	void onModify(WatchedDir dir,String path,File file,SyncropItem item){
		if(item==null)
			item=SyncropItem.getInstance(path, dir.getAccountName(), file);
	
		if(item.hasBeenUpdated()){//item.getDateModified()!=item.getFile().lastModified()){
			if(Syncrop.isInstanceOfCloud())
				daemon.setPropperPermissions(item);
			addToSendQueue(item);
			item.save();
		}
		else logger.logTrace("File not modifed");
	}
	void onCreate(WatchedDir dir,String path,File file,SyncropItem item){
		String owner=dir.getAccountName();
		if(item==null)
			item=SyncropItem.getInstance(path, owner, file);
	
		addToSendQueue(item);
		item.save();
		
		if(file.isDirectory()){
			logger.log("New dir detected "+path);
			checkFiles(dir.getAccount(), path, dir.isRemovable());
		}
	
	}
	
	
	
	

	void onDelete(WatchedDir dir,String relativePath,File file,SyncropItem item,final long timestamp) throws IOException{
		if(file.exists()){
			logger.logTrace("file exists; deletion canceled");
			return;
		}
		else if(item==null){
			logger.logAll("no metadata found for"+relativePath);
			return;
		}
		String owner=dir.getAccountName();
		logger.log("dir deleted "+relativePath);
		if(item.knownToExists()){
			Iterable<SyncropItem>items=FileMetadataManager.getFilesStartingWith(relativePath, owner);
			for(SyncropItem itemKids:items)
				recordFileDeletion(itemKids, timestamp-1);
			//recordFileDeletion(item, timestamp);
			if(item instanceof SyncropDir)
				if(keyMap.containsKey(item.getFile().toPath())){
					logger.log("canceling key for "+item);
					keys.remove(keyMap.get(item.getFile().toPath()));
					keyMap.remove(item.getFile().toPath()).cancel();
				}
		}
		//TODO rename
	}
	
	
	private void recordFileDeletion(SyncropItem item,final long timeOfDelete){
		if(item==null)return;
		if(item.exists())
			return;
		if(item.knownToExists()){
			logger.log("fileListerner deemed this file to not exists "
					+item.getFile(),SyncropLogger.LOG_LEVEL_DEBUG);
			item.setDateModified(timeOfDelete);	
		}
		if(item.hasBeenUpdated()){
			addToSendQueue(item);
			item.save();
		}
		
	}
	
	public void onFileChange(SyncropItem item,String path){onFileChange(item,path,StandardWatchEventKinds.ENTRY_MODIFY);}
	public void onFileChange(SyncropItem item,String absPath,WatchEvent.Kind<?>  kind){
		try {
			if(Settings.allowScripts())
				for(Command c:commands)
					if(c.isCommandFor(absPath)&&c.isWaitingFor(kind)){
						logger.log("running command for"+absPath);
						c.execute();
					}
				
		} catch (NullPointerException| IOException e1) {
			Settings.setAllowScripts(false);
			logger.logError(e1, "occured while executing command");
		}
	}
	
	public void updateAccountSize(Account account, File file,SyncropItem item){
		if(item==null)
			account.setRecordedSize(account.getRecordedSize()+file.length());
		else 
			account.setRecordedSize(account.getRecordedSize()-item.getLastKnownSize()+item.getSize());
		
	}
	
	public boolean loadCommandsToRunOnFileModification() {
		
		File commandFile=new File(ResourceManager.getConfigFilesHome(),"syncrop-commands");
		if(!commandFile.exists())return false;
		
		try {
			Scanner sc=new Scanner(new FileReader(commandFile));
			String files[]=null;
			String listeners[]=null;
			String scripts[]=null;
			String workingDirectory=null;
			while(sc.hasNextLine()){
				String line=sc.nextLine().trim();
				if(line.startsWith("{")){
					files=listeners=scripts=null;
					workingDirectory=null;
					line=line.substring(1).trim();
				}
				else if(line.startsWith("}")){
					commands.add(new Command(files, listeners, scripts, workingDirectory));
				}
				if(line.isEmpty())continue;
				String []parts=line.split(":",1);
				if (parts[0].equalsIgnoreCase("FILE"))
					files=parts[1].split("\t");
				else if (parts[0].equalsIgnoreCase("listener"))
					listeners=parts[1].split("\t");
				else if (parts[0].equalsIgnoreCase("run"))
					scripts=parts[1].split("\t");
				else if (parts[0].equalsIgnoreCase("workingDirectory"))
					workingDirectory=parts[1];
			}
			sc.close();
			return true;
		} catch (FileNotFoundException e) {
			logger.logError(e);
		}
		return false;
	}
	
	void addToSendQueue(SyncropItem item){
		if(daemon!=null)
			daemon.addToSendQueue(item);
	}
	



}

