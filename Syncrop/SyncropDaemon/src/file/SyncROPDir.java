package file;

import java.nio.file.Files;
import java.nio.file.LinkOption;


public class SyncROPDir extends SyncROPItem
{
	//TODO update readfile in readFile
	
	public SyncROPDir(String path,String owner)
	{
		this(path, owner, -1);
	}
	public SyncROPDir(String path,String owner,long modificicationDate){
		this(path, owner, modificicationDate, false);
	}
	public SyncROPDir(String path,String owner,long modificicationDate,boolean knownToExists){
		super(path, owner, modificicationDate,knownToExists);
		if(exists()&&!Files.isDirectory(file.toPath(),LinkOption.NOFOLLOW_LINKS)){
			throw new IllegalArgumentException("path "+path+"  does not denote"
					+ " a directory so it cannot be a SyncROPDir");
		}
	}
	
	@Override
	public boolean createFile() {
		return file.mkdirs();
	}
	@Override
	public String toString(){
		return super.toString()+" isEmpty:"+isEmpty();
	}
}
