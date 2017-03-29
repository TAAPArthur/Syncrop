package listener;

import java.io.File;
import java.io.IOException;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent.Kind;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import syncrop.ResourceManager;
import syncrop.Syncrop;

public class Command {
	
	private JSONObject jsonObject;
	public Command (JSONObject jsonObject){
		this.jsonObject=jsonObject;
	}
	public boolean isCommandFor(String path){
		JSONArray files=((JSONArray)jsonObject.get("file"));
		for(int i=0;i<files.size();i++){
			if(path.matches(ResourceManager.convertToPattern((String)files.get(i))))
				return true;
		}
		return false;
	}
	public boolean canExecute(Kind<?> kind){
		JSONArray array=((JSONArray)jsonObject.get("listener"));
		if(kind.equals(StandardWatchEventKinds.ENTRY_CREATE))
			return array.contains("onCreate");
		else if(kind.equals(StandardWatchEventKinds.ENTRY_MODIFY))
			return array.contains("onModify");
		else if(kind.equals(StandardWatchEventKinds.ENTRY_DELETE))
			return array.contains("onDelete");
		else return false;
	}
	public void execute() throws IOException{
		
		JSONArray array=((JSONArray)jsonObject.get("execute"));
		for(int i=0;i<array.size();i++){
			JSONObject object=(JSONObject) array.get(i);
			String workingDir=(String) object.get("workingDirectory");
			JSONArray commands=(JSONArray) object.get("commands");
			for(int n=0;n<commands.size();n++){
				String command=((String)commands.get(n)).trim();
				try {
					Syncrop.logger.logDebug("Executing command "+command);
					Process p=Runtime.getRuntime().exec(command.split(" "),null,
							workingDir==null?null:new File(workingDir));
					p.waitFor();
					
				} catch (InterruptedException e) {
					Syncrop.logger.log("Command was innterrupted "+ command);
				}
			}
		}
		
	}
	@Override
	public String toString(){
		return jsonObject.toString();
	}

}
