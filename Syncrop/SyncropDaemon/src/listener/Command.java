package listener;

import java.io.File;
import java.io.IOException;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent.Kind;

import syncrop.ResourceManager;
import syncrop.Syncrop;

public class Command {
	
	
	private final String files[];
	private final String listeners[];
	private final String scripts[];
	private final String workingDirectory;
	
	public Command(String[] files, String[] listeners, String[] scripts, String workingDirectory) {
		this.files = files;
		this.listeners = listeners;
		this.scripts = scripts;
		this.workingDirectory = workingDirectory;
	}
	public boolean isCommandFor(String path){
		for(String file:files){
			if(path.matches(ResourceManager.convertToPattern(file)))
				return true;
		}
		return false;
	}
	public boolean isWaitingFor(Kind<?> kind){
		String s;
		if(kind.equals(StandardWatchEventKinds.ENTRY_CREATE))
			s="onCreate";
		else if(kind.equals(StandardWatchEventKinds.ENTRY_MODIFY))
			s="onModify";
		else if(kind.equals(StandardWatchEventKinds.ENTRY_DELETE))
			s="onDelete";
		else return false;
		
		for (String a:listeners)
			if(a.equals(s))
				return true;
		return false;
	}
	
	public void execute() throws IOException{
		
		for(String script:scripts){
			try {
				Syncrop.logger.logDebug("Executing command "+script);
				Process p=Runtime.getRuntime().exec(script.split(" "),null,
						workingDirectory==null?null:new File(workingDirectory));
				p.waitFor();
				
			} catch (InterruptedException e) {
				Syncrop.logger.log("Command was innterrupted "+ script);
			}
		}
		
	}
	

}
