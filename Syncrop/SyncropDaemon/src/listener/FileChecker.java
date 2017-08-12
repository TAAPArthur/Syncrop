package listener;

import static syncrop.Syncrop.logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Stack;

import account.Account;
import file.SyncropItem;
import listener.actions.SyncROPFileAction;
import syncrop.ResourceManager;

public class FileChecker extends SimpleFileVisitor<Path>{
	private Account account;
	private boolean removable;
	private SyncROPFileAction[] fileActions;
	protected Stack<SyncropItem>stack=new Stack<>();
	private FileWatcher watcher;
	private String startingFile;
	
	public void setFileWatcher(FileWatcher watcher) {
		this.watcher=watcher;
	}
	
	void setFileActions(SyncROPFileAction... fileActions) {
		this.fileActions = fileActions;
	}
	void setDir(Account a,boolean removable,SyncropItem baseDir) {
		this.account=a;
		this.removable=removable;
		stack.clear();
		startingFile=baseDir.getAbsPath();
		stack.add(baseDir);
		//stack.add(getItem(baseDir,new File(ResourceManager.getAbsolutePath(baseDir, a.getName()))));
	}
	protected String getPath(Path dir) {
		return stack.peek().getChildPath(dir.getFileName().toString());
	}
	SyncropItem getItem(Path dir) {
		return getItem(getPath(dir),dir.toFile());
	}
	protected SyncropItem getItem(String path,File file) {
		if(ResourceManager.isLocked(path,account.getName()))
			return null;
		SyncropItem item=SyncropItem.getInstance(path, account.getName(),file);
		
		return item;
	}
	@Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
		//if(Settings.isLimitingCPU()&&Math.random()>.7)
		//	Syncrop.sleepShort();
		
		
		SyncropItem item=getItem(dir);
		
		if(startingFile.equals(dir.toString()))
			return FileVisitResult.CONTINUE;
		
		if(item==null||!item.isEnabled()) 
			return FileVisitResult.SKIP_SUBTREE;
		
		stack.push(item);
		
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
    	SyncropItem item=stack.pop();
    	save(item);
    	if(watcher!=null)
    		watcher.register(dir,item.getPath(),account, removable);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

    	SyncropItem item=getItem(file);
    	
		if(item==null||!item.isEnabled())
			return FileVisitResult.CONTINUE;
    	if(fileActions!=null&&item!=null){
			for(SyncROPFileAction fileAction:fileActions)
				if(fileAction!=null)
					fileAction.performOn(item);
			if(!Files.exists(file, LinkOption.NOFOLLOW_LINKS)){
				logger.logTrace("file action caused file to no longer exists; skipping");
				return FileVisitResult.CONTINUE;
			}
		}
    	save(item);

        return FileVisitResult.CONTINUE;
    }
    void save(SyncropItem item){
    	if(item.hasBeenUpdated()){
			item.save();
			if(watcher!=null) {
				watcher.onFileChange(item,item.getAbsPath());
				watcher.addToSendQueue(item);
				if(item.exists())
					watcher.updateAccountSize(account, item.getFile(), item);
			}
		}
    }
}