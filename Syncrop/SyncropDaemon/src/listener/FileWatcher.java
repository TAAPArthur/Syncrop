package listener;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static syncrop.Syncrop.logger;
import static transferManager.FileTransferManager.HEADER_DELETE_MANY_FILES;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import account.Account;
import daemon.SyncDaemon;
import daemon.SyncropClientDaemon;
import file.Directory;
import file.RemovableDirectory;
import file.SyncROPDir;
import file.SyncROPFile;
import file.SyncROPItem;
import file.SyncROPSymbolicLink;
import settings.Settings;
import syncrop.ResourceManager;
import syncrop.Syncrop;
import syncrop.SyncropLogger;

public class FileWatcher extends Thread{
	
    private final WatchService watcher;
    private final Map<WatchKey,WatchedDir> keys;
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
    	
    	WatchedDir dir=new WatchedDir(path,accessPath,account, removable);
        WatchKey key = dir.registerDir(watcher);
        if(keys.containsKey(key)){
        	logger.log("Dir not registered because it is already registered "+path);
        	key.reset();
        }
        else keys.put(key, dir);
    }
    public static void checkAllMetadataFiles(){
    	checkAllMetadataFiles(true);
    }
    public static void checkAllMetadataFiles(boolean perserveMetadataForDeletedFiles){
    	File parent=ResourceManager.getMetadataDirectory();
    	logger.logTrace("checking metadata");
		File[] files=parent.listFiles();
		
		for(File metaDataFile:files)
			if(!metaDataFile.equals(ResourceManager.getMetadataVersionFile()))
				checkMetadata(metaDataFile,Syncrop.getStartTime(),perserveMetadataForDeletedFiles);
		logger.logTrace("finished checking metadata");

    }
    static void checkMetadata(File metaDataFile,final long dateMod,final boolean perserveMetadataForDeletedFiles){
		if(metaDataFile.isDirectory()){
			Syncrop.sleepShort();
			for(File file:metaDataFile.listFiles())
				checkMetadata(file,dateMod,perserveMetadataForDeletedFiles);
			if(metaDataFile.list().length==0)
				metaDataFile.delete();
		}
		else {
			SyncROPItem file=ResourceManager.readFile(metaDataFile);
			if(file==null)return;
			if(!file.isEnabled()){
				if(Syncrop.isInstanceOfCloud())
					file.delete(0);
				ResourceManager.deleteFile(file);
			}
			else if(!file.exists())
				if(!perserveMetadataForDeletedFiles){
					ResourceManager.deleteFile(file);
				}
				else if(!file.isDeletionRecorded()){
					logger.logTrace("File was deleted while server was off path="+file.getPath());
					file.setDateModified(dateMod);
					file.save();
					file.tryToCreateParentSyncropDir();
				}
		}
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
			logger.log("checking account size");
			a.setRecordedSize(size);
			logger.log("done");
		}
	}

	long checkFiles(Account a,final String path,boolean removable){
		if(SyncropClientDaemon.isShuttingDown())
			return 0;

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
				else {
					item=ResourceManager.getFile(path, a.getName());
				}
				
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
		 		if(files.length==0){
					if(metadataFile.exists())
						item=ResourceManager.getFile(path, a.getName());
					if(item==null){
						if(metadataFile.exists())
							metadataFile.delete();
						item=new SyncROPDir(path,a.getName(),file.lastModified());
					}
					
				}
				else if(metadataFile.exists()&&files.length>0){
					ResourceManager.deleteFile(ResourceManager.getFile(path, a.getName()));
				}
				//else daemon.setPropperPermissions(AccountManager.getDirByPath(path, a.getName()));
				logger.log("Registering "+file,SyncropLogger.LOG_LEVEL_ALL);
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
			item=new SyncROPFile(path,a.getName(),-1);
			if(!item.isEnabled())return 0;
			logger.logTrace("detected new file "+path);
			
		}
		else {
			item=ResourceManager.getFile(path, a.getName());
			if(item==null){
				logger.logWarning("error occured when reading file. path="+path);
			}
			
		}
		//else daemon.setPropperPermissions(AccountManager.getFileByPath(path, a.getName()));
		
		if(item!=null&&item.hasBeenUpdated()){
			onFileChange(item.getAbsPath());
			item.save();
			
			if(daemon!=null){
				daemon.addToSendQueue(item);
			}
		}

		if(item==null||item.hasBeenUpdated())
			updateAccountSize(a, file, item);
		if(item!=null)
			return count+item.getSize();
		else return count;
		
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
				listenForDirectoryChanges();
			} catch (InterruptedException x) {
				continue;
			}
			catch (ClosedWatchServiceException e){
				logger.log("Watch service has been closed");
				SyncropClientDaemon.sleepLong();
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
	
	void listenForDirectoryChanges() throws InterruptedException{
		
		WatchKey key = watcher.take();
		//watcher.p
		
		SyncropClientDaemon.sleep(100);//used to eliminate duplicate events
		WatchedDir dir = keys.get(key);
		
		List<WatchEvent<?> >events=key.pollEvents();
		
		for (WatchEvent<?>e :events)
			try
			{
				String path=dir.getPath()+File.separator+e.context();
				File file=new File(ResourceManager.getHome(dir.getAccountName(), dir.isRemovable()),path);
							
				SyncROPItem item=ResourceManager.getFile(path, dir.getAccountName());
				//if(!(file.isDirectory()&&e.kind()==ENTRY_MODIFY)&&logger.isLogging(Logger.LOG_LEVEL_ALL))
				//	logger.log("Detected change in file "+file+" "+e.kind(),Logger.LOG_LEVEL_ALL);
				if(!dir.getAccount().isPathEnabled(path))continue;
				if(item!=null&&!item.isEnabled())continue;
				
				onFileChange(file.getAbsolutePath(),e.kind());
				
				updateAccountSize(dir.getAccount(),file, item);
				if(ResourceManager.isLocked(path,dir.getAccountName())){
					logger.logTrace(path+" is locked");
					continue;
				}
				
				if(!(file.isDirectory()&&e.kind()==ENTRY_MODIFY)&&!logger.isLogging(SyncropLogger.LOG_LEVEL_ALL))
					logger.logTrace("Detected change in file "+file+" "+e.kind());
				if(e.kind()==ENTRY_CREATE)
					onCreate(dir, path, file, item);
				else if(e.kind()==ENTRY_MODIFY)
					onModify(dir, path, file, item);
				else if(e.kind()==ENTRY_DELETE)
					onDelete(dir, path, file, item);
				else logger.log("Unexpected kind: "+e.kind());				
			}
			catch (IllegalArgumentException e1){}
			catch (Exception e1){
				logger.logError(e1, "occured while listening for dir changes");
			}
        // reset key and remove from set if directory no longer accessible
		key.reset();
		/*
        boolean valid = key.reset();
        if (!valid) {
        	logger.logTrace("removing key "+key);
            keys.remove(key);
            // all directories are inaccessible
            if (keys.isEmpty()) {
               logger.log("no directories are enabled");
            }
        }*/
	}
	

	void onModify(WatchedDir dir,String path,File file,SyncROPItem item){
		if(item==null)
			onCreate(dir, path, file, item);
		else
			if(item.hasBeenUpdated()){//item.getDateModified()!=item.getFile().lastModified()){
				tryToSendFile(item);
				ResourceManager.writeFile(item);
			}
	}
	void onCreate(WatchedDir dir,String path,File file,SyncROPItem item){
		String owner=dir.getAccountName();
		if(item==null){
			if(Files.isSymbolicLink(file.toPath())){
				item=tryToCreateSyncropLink(file, path, dir.getAccount(),dir.isRemovable());
			}
			else if(file.isDirectory()){
				if(file.list().length==0)
					item=new SyncROPDir(path, owner);						
			}
			else item=new SyncROPFile(path, owner);
			if(item!=null&&item.isEmpty()){
				tryToSendFile(item);
				ResourceManager.writeFile(item);
			}
		}
		else {
			logger.log("Deleted file has been recreated; path="+path,SyncropLogger.LOG_LEVEL_TRACE);
			onModify(dir, path, file, item);
		}
		if(file.isDirectory()){
			logger.logTrace("New dir detected "+path);
			checkFiles(dir.getAccount(), path, dir.isRemovable());
		}
	
	}
	
	
	private void tryToSendFile(SyncROPItem item){
		if(SyncropClientDaemon.isConnectionActive())
			if(item instanceof SyncROPFile||((SyncROPDir) item).isEmpty()){
				logger.logDebug("Listener is adding files to queue");
				daemon.addToSendQueue(item);
			}
	}
	
	void onDelete(WatchedDir dir,String relativePath,File file,SyncROPItem item){
		if(file.exists()){
			logger.log("file exists; deletion canceled");
			return;
		}
		String owner=dir.getAccountName();
		if(item==null||!item.isDeletionRecorded()){

			File metadataFile=
					item!=null?
							item.getMetadataFile():
							new File(ResourceManager.getMetadataFile(relativePath, owner).getParent(),file.getName());
//			File metadataFile=new File(
//					ResourceManager.getMetaDataDirectory(),
//					(SyncropClientDaemon.isInstanceOfCloud()?owner+File.separator:"")+
//					relativePath).getParentFile();
//			
			
			
			long timeOfDelete=System.currentTimeMillis();
			
			ArrayList<Object[]> message=new ArrayList<Object[]>();
			
			removeDeletedFileFromRecord(metadataFile,relativePath,owner,timeOfDelete,message);
			if(message.size()!=0)
				SyncDaemon.mainClient.printMessage(message.toArray(new Object[message.size()][5]), HEADER_DELETE_MANY_FILES);
			if(item instanceof SyncROPDir)
				ResourceManager.writeFile(item);
			Syncrop.sleepVeryShort();
		}
		
		//TODO rename
	}
	private void removeDeletedFileFromRecord(File metadataFile,String relativePath,String owner,final long timeOfDelete,final ArrayList<Object[]> message){
		final int maxTransferSize=128;
		SyncROPItem item;
		if(metadataFile.isDirectory()){
			for(File f:metadataFile.listFiles())
					removeDeletedFileFromRecord(
						f,relativePath+File.separator+f.getName(),owner,timeOfDelete,message);
			item=new SyncROPDir(relativePath, owner);
		}
		else {
			item=ResourceManager.readFile(metadataFile);
		}
		if(item==null){
			logger.log(ResourceManager.getAbsolutePath(relativePath, owner)+
					"cannot be removed from records because it is not in records");
			return;
		}
		if(!item.isDeletionRecorded()){
			logger.log("fileListerner deemed this file to " +
					"not exists "+item.getFile().toString(),SyncropLogger.LOG_LEVEL_DEBUG);
			item.setDateModified(timeOfDelete);
			ResourceManager.writeFile(item);
			message.add(item.formatFileIntoSyncData());
			if(message.size()==maxTransferSize){	
				SyncDaemon.mainClient.printMessage(message.toArray(new Object[message.size()][5]), HEADER_DELETE_MANY_FILES);
				message.clear();
				Syncrop.sleep();
			}
		}
	}
	
	public void onFileChange(String path){onFileChange(path,StandardWatchEventKinds.ENTRY_MODIFY);}
	public void onFileChange(String absPath,WatchEvent.Kind<?>  kind){
		try {
			if(Settings.allowScripts())
				for(Command c:commands)
					if(c.isCommandFor(absPath)&&c.canExecute(kind)){
						logger.logTrace("running command for"+absPath);
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
		logger.log("loading commands ("+jsonArray.size()+")");
		for(int i=0;i<jsonArray.size();i++)
			commands.add(new Command((JSONObject) jsonArray.get(i)));
	}
}