package daemon;

import java.util.HashSet;

import file.Directory;
import file.SyncROPItem;

public class SyncropUser implements Comparable<SyncropUser> {

	String id;
	String accountName;
	HashSet<Directory> restrictions = new HashSet<Directory>();
	public SyncropUser(String id,String accountName){
		this.id=id;
		this.accountName=accountName;
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
		for(int i=0;i<dirsToAdd.length;i++)
			if(dirsToAdd[i]==null||dirsToAdd[i].isEmpty())continue;
			else if(Directory.containsMatchingChars(dirsToAdd[i]))
			{
				restrictions.add(new Directory(dirsToAdd[i], false));
			}
			else if(!SyncROPItem.isValidFileName(dirsToAdd[i]))continue;
			else restrictions.add(new Directory(dirsToAdd[i], true));
	}
	public HashSet<Directory> getRestrictions(){return restrictions;}
	public boolean isPathRestricted(final String path)
	{	
		for(Directory dir:this.restrictions){
			if(dir.isPathContainedInDirectory(path))
				return true;
		}	
		return false;
	}

}
