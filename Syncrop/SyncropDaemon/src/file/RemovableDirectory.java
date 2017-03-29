package file;

import java.io.File;

import syncrop.ResourceManager;
import account.Account;

public class RemovableDirectory extends Directory{

	private final File removableDir;
	Account account;
	public RemovableDirectory(String dir,Account a) {
		super(dir);
		account=a;
		removableDir=new File(ResourceManager.getHome(a.getName(), true),getDir());
	}
	@Override
	public String getDir(){
		return ResourceManager.getHome(account.getName(), true)+File.separator+super.getDir();
	}
	public File getRemovableDir(){
		return removableDir;
	}
	
	public boolean exists(){
		return removableDir.exists();
	}
}