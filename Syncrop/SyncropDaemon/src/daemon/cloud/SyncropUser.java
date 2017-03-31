package daemon.cloud;

import java.util.HashSet;

import account.Account;
import file.Directory;
import file.Restriction;
import settings.Settings;

public class SyncropUser implements Comparable<SyncropUser> {

	String id;
	String accountName;
	Account account;
	long longInTime=System.currentTimeMillis();
	public long getLongInTime(){return longInTime;}
	public SyncropUser(String id,String accountName){
		this.id=id;
		this.accountName=accountName;
		account=new Account();
	}
	@Override
	public int compareTo(SyncropUser o) {
		return getID().compareTo(o.getID());
	}
	@Override
	public boolean equals(Object o){
		if(o instanceof SyncropUser)
			return getID().equals(((SyncropUser) o).getID());
		return false;
	}
	public String getID(){return id;}
	public String getAccountName(){return accountName;}
	public void addRestrictions(String... dirsToAdd)
	{
		account.addRestrictions(dirsToAdd);
	}
	public void addEnabledDirs(String... dirsToAdd)
	{
		account.addDirs(dirsToAdd);
	}
	
	public HashSet<Restriction> getRestrictions(){return account.getRestrictions();}
	public boolean isPathEnabled(final String path){
		return !isPathRestricted(path)&&isPathContainedInAtLeastOneDirectory(path);
	}
	private boolean isPathContainedInAtLeastOneDirectory(String path){
		for(Directory dir:account.getDirectories())
			if(dir.isPathContainedInDirectory(path))
				return true;
		return false;
	}
	public boolean isPathRestricted(final String path)
	{	
		for(Directory dir:account.getRestrictions()){
			if(dir.isPathContainedInDirectory(path))
				return true;
		}	
		return false;
	}
	int conflictResolution=-Settings.getConflictResolution();
	public int getConflictResolution(){return conflictResolution;}
	public void setConflictResolution(int conflictResolution){this.conflictResolution=conflictResolution;}
	/*boolean allowConflicts=Settings.isConflictsAllowed();
	public boolean isConflictsAllowed(){return allowConflicts;}
	public void setConflictsAllowed(boolean conflictsAllowed){allowConflicts=conflictsAllowed;}
	*/
	boolean deletingFilesNotOnClient=Settings.isDeletingFilesNotOnClient();
	public boolean isDeletingFilesNotOnClient(){return deletingFilesNotOnClient;}
	public void setDeletingFilesNotOnClient(boolean b){deletingFilesNotOnClient=b;}
	
}
