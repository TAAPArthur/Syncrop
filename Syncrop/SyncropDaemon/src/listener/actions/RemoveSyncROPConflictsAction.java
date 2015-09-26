package listener.actions;

import static syncrop.Syncrop.logger;

import java.io.File;

import file.SyncROPFile;

public class RemoveSyncROPConflictsAction implements SyncROPFileAction{

	@Override
	public boolean performOn(File file) {
		if(file.isFile()&&file.getName().matches(".*"+SyncROPFile.CONFLICT_ENDING+"\\d+")){
			logger.log("deleting file path="+file.getAbsolutePath());
			file.delete();
			return true;
		}
		else return false;
	}

}
