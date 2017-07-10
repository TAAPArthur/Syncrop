package file;

import static syncrop.ResourceManager.isFileRemovable;
import static syncrop.Syncrop.logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import syncrop.ResourceManager;
import syncrop.Syncrop;

public class SyncropSymbolicLink extends SyncropFile{

	private String targetPath;
	private File targetFile;
	
	private boolean disabled=false;
	private final static Path PLACE_HOLDER=new File("").toPath();
			
	
	public static boolean isSyncropSymbolicLink(String owner,String target) throws IOException{
		return ResourceManager.getAccount(owner).isPathEnabled(target);
	}
	public static SyncropItem getInstance(String path,String owner,File f) throws IOException{
		Path targetPath=Files.readSymbolicLink(f.toPath()).toAbsolutePath();
		String target = targetPath.toString().replace(
				ResourceManager.getHome(owner, isFileRemovable(path)), "");
		if(isSyncropSymbolicLink(owner,target)||!Files.exists(targetPath))
			return new SyncropSymbolicLink(path, owner, target);
		else {
			if(Files.isDirectory(targetPath))
				return new SyncropDir(path, owner);
			else 
				return new SyncropFile(path, owner);
		}
	}
	public static SyncropItem getInstance(String path,String owner,
			long dateModified,int key,boolean modifedSinceLastKeyUpdate,long lastRecordedSize,int filePermissions,
			File f) throws IOException{
		Path targetPath=Files.readSymbolicLink(f.toPath()).toAbsolutePath();
		String target = targetPath.toString().replace(
				ResourceManager.getHome(owner, isFileRemovable(path)), "");
		if(isSyncropSymbolicLink(owner,target)||!Files.exists(targetPath))
			return new SyncropSymbolicLink(path, owner, dateModified,key,modifedSinceLastKeyUpdate,target,lastRecordedSize,false,filePermissions);
		else {
			if(Files.isDirectory(targetPath))
				return new SyncropDir(path, owner,dateModified,false,filePermissions);
			else 
				return new SyncropFile(path, owner,dateModified,key,modifedSinceLastKeyUpdate,lastRecordedSize,false,filePermissions);
		}

	}
	private SyncropSymbolicLink(String path, String owner,
			String targetPath){
		this(path, owner, -1, 0,false, targetPath,-1,false,0);
	}
	
	
	public SyncropSymbolicLink(String path, String owner,
			long modificicationDate,int key,boolean modifedSinceLastKeyUpdate,String targetPath,long lastRecordedSize,boolean deletionRecorded,int filePermissions){
		super(path, owner, modificicationDate,key,modifedSinceLastKeyUpdate,lastRecordedSize,deletionRecorded,filePermissions);
		this.targetPath=targetPath;
		targetFile=new File(ResourceManager.getHome(getOwner(), isRemovable()),targetPath);
		
		if(exists()&&!Files.isSymbolicLink(file.toPath())){
			throw new IllegalArgumentException("path "+path+" is not a symbolic link so "
					+ "it cannot be a SyncROPSymbolicLink");
		}
		if(!ResourceManager.getAccount(getOwner()).isPathEnabled(path))
			throw new IllegalArgumentException("path "+path+" is a symbolic link so "
					+ "to a non-synced file so it should be treated as a regular file");
		try {
			if(Syncrop.isInstanceOfCloud()&&!isSyncropSymbolicLink(owner,targetPath))
				disabled=true;
		} catch (IOException e) {
			logger.logError(e);
			disabled=true;
		}

	}
	@Override
	public long getSize(){
		//TODO get actaul size of sym link
		return file.exists()?4:100;
	}
	@Override
	public boolean createFile(){
		try {
			Files.createSymbolicLink(file.toPath(), disabled?PLACE_HOLDER:targetFile.toPath());
			return Files.isSymbolicLink(file.toPath());
		} catch (IOException|SecurityException e) {
			logger.log(e.toString()+"; File could not be created "+file);
		}
		return false;
	}
	@Override
	public String getTargetPath(){return targetPath;}
	
	public File getTargetFile(){return targetFile;}
	public boolean isTargetEmpty(){
		return targetFile.list()==null||targetFile.list().length==0;
	}
	
	@Override
	public boolean exists() {
		return Files.exists(file.toPath(),LinkOption.NOFOLLOW_LINKS);
	}
	public boolean isTargetDir(){
		logger.log("checking if target of "+this+" target:"+targetFile+" isDir="+Files.isDirectory(targetFile.toPath(), LinkOption.NOFOLLOW_LINKS));
		return Files.isDirectory(targetFile.toPath(), LinkOption.NOFOLLOW_LINKS);
	}
	@Override
	public String toString(){
		return super.toString()+" target:"+targetPath;
	}
}