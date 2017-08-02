package file;

import static syncrop.Syncrop.logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import syncrop.ResourceManager;
import syncrop.Syncrop;

public class SyncropSymbolicLink extends SyncropFile{

	private String linkTarget;
	
	private final static Path PLACE_HOLDER=new File("").toPath();

	public SyncropSymbolicLink(String path, String owner,
			String targetPath){
		this(path, owner, -1, 0,false, targetPath,-1,false,0);
	}
	
	
	public SyncropSymbolicLink(String path, String owner,
			long modificicationDate,int key,boolean modifedSinceLastKeyUpdate,String targetPath,long lastRecordedSize,boolean knownToExists,int filePermissions){
		super(path, owner, modificicationDate,key,modifedSinceLastKeyUpdate,lastRecordedSize,knownToExists,filePermissions);
		this.linkTarget=targetPath;
		if(file.exists())
			try {
				String actualTarget=Files.readSymbolicLink(file.toPath()).toString();
				if(!linkTarget.equals(actualTarget)){
					linkTarget=actualTarget;
					setHasBeenUpdated();
				}
			} catch (IOException e) {
				logger.logError(e);
			}
		if(exists()&&!Files.isSymbolicLink(file.toPath())){
			throw new IllegalArgumentException("path "+path+" is not a symbolic link so "
					+ "it cannot be a SyncROPSymbolicLink");
		}
		if(!ResourceManager.getAccount(getOwner()).isPathEnabled(path))
			throw new IllegalArgumentException("path "+path+" is a symbolic link so "
					+ "to a non-synced file so it should be treated as a regular file");
		
	}
	@Override
	public long getSize(){
		//TODO get actaul size of sym link
		return file.exists()?4:100;
	}
	@Override
	protected long getLastModifiedTime() throws IOException{
		return Files.getLastModifiedTime(file.toPath(),LinkOption.NOFOLLOW_LINKS).toMillis();
	}
	@Override
	public boolean createFile(){
		try {
			Files.createSymbolicLink(file.toPath(), 
					Syncrop.isInstanceOfCloud()?PLACE_HOLDER:
					new File(file.getParentFile(),linkTarget).toPath());
			return Files.isSymbolicLink(file.toPath());
		} catch (IOException|SecurityException e) {
			logger.log(e.toString()+"; File could not be created "+file);
		}
		return false;
	}
	@Override
	public String getLinkTarget(){return linkTarget;}
	
		
	@Override
	public String toString(){
		return super.toString()+" target:"+linkTarget;
	}
}