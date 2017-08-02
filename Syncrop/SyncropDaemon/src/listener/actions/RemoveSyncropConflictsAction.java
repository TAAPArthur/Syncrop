package listener.actions;

import static syncrop.Syncrop.logger;

import java.io.File;

import file.SyncropFile;
import file.SyncropItem;

public class RemoveSyncropConflictsAction implements SyncROPFileAction{

	@Override
	public boolean performOn(SyncropItem item) {
		File file=item.getFile();
		if(file.isFile()&&file.getName().matches(".*"+SyncropFile.CONFLICT_ENDING+"\\d+")){
			logger.log("deleting file path="+file.getAbsolutePath());
			file.delete();
			item.deleteMetadata();
			return true;
		}
		else return false;
	}
}
