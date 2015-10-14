package listener;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import account.Account;

public class WatchedDir{
	Path dir;
	String path;
	boolean removable;
	Account account;
	public WatchedDir(Path dir,String path,Account account, boolean removable){
		this.dir=dir;
		this.path=path;
		this.account=account;
		this.removable=removable;
	}
	Path getDir(){return dir;}
	String getPath(){return path;}
	Account getAccount(){return account;}
	String getAccountName(){return account.getName();}
	boolean isRemovable(){return removable;}
	WatchKey registerDir(WatchService watcher) throws IOException{
		return dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
	}
}