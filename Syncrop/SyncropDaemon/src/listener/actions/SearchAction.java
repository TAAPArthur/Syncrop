package listener.actions;

import java.io.File;
import java.util.HashSet;

import syncrop.ResourceManager;

public class SearchAction implements SyncROPFileAction{
	String regex;
	HashSet<String>fileNames=new HashSet<String>();
	public SearchAction(String textToSearchFor){
		regex=ResourceManager.convertToPattern(textToSearchFor);
	}
	@Override
	public boolean performOn(File file) {
		if(file.getName().matches(regex)){
			fileNames.add(file.getAbsolutePath());
			return true;
		}
		else return false;
	}

}
