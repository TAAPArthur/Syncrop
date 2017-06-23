package file;

import java.nio.file.Files;


public class SyncROPDir extends SyncROPItem
{
	//TODO update readfile in readFile
	
	public SyncROPDir(String path,String owner)
	{
		this(path, owner, -1);
	}
	public SyncROPDir(String path,String owner,long modificicationDate){
		this(path, owner, modificicationDate, -1,"");
	}
	public SyncROPDir(String path,String owner,long modificicationDate,long lastRecordedSize,String filePermisions){
		super(path, owner, modificicationDate,false,lastRecordedSize,filePermisions);
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
