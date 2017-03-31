package daemon.cloud.filesharing;

import java.util.Arrays;
import java.util.HashSet;

import syncrop.ResourceManager;


public class SharedFile {
	
	/**
	 * path of this symbolic link
	 */
	private String path;
	/**
	 * owner of link
	 */
	private String owner;
	
	private HashSet<String> abslinksToThisSharedFile=new HashSet<>();
	private String []peopleSharedWith;
	public SharedFile(String line){
		this(line.split("\t",2));
	}
	private SharedFile(String[] s){
		this(s[0],s[1], s[2].split("\t"));
	}
	public SharedFile(String path,String owner,String... abslinksToThisSharedFile){
		
		this.path=path;
		this.owner=owner;
		if(path.isEmpty())
			throw new IllegalArgumentException("Cannot share an empty path");
		this.abslinksToThisSharedFile.addAll(Arrays.asList(abslinksToThisSharedFile));
	}
	public boolean hasLinkToSharedFile(String linkToThisSharedFile){
		return this.abslinksToThisSharedFile.contains(linkToThisSharedFile);
	}
	public void addLinkToSharedFile(String linkToThisSharedFile){
		this.abslinksToThisSharedFile.add(linkToThisSharedFile);
	}
	
	public String getOwner(){return owner;}
	public String getPath(){return path;}
	public String getAbsolutePath(){return ResourceManager.getAbsolutePath(path, owner);}
		
	public String toString(){
		String abslinksToThisSharedFile="";
		for(String s:this.abslinksToThisSharedFile)
			abslinksToThisSharedFile+="\t"+s;
		return path+"\t"+owner+abslinksToThisSharedFile;
	}
	public int hashCode(){
		return toString().hashCode();
	}
	public boolean equals(Object o){
		if(!(o instanceof SharedFile))return false;
		SharedFile file=(SharedFile) o;
		return file.path.equals(path)&&
				file.owner.equals(owner);
	}
	
	

}
