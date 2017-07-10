package file;

import java.nio.file.Files;


public class SyncropDir extends SyncropItem
{
	//TODO update readfile in readFile
	
	public SyncropDir(String path,String owner)
	{
		this(path, owner, -1);
	}
	public SyncropDir(String path,String owner,long modificicationDate){
		this(path, owner, modificicationDate,false,0);
	}
	public SyncropDir(String path,String owner,long modificicationDate,boolean deletionRecorded,int filePermisions){
		super(path, owner, modificicationDate,false,-1,deletionRecorded,filePermisions);
		if(exists()&&!Files.isDirectory(file.toPath())){
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
