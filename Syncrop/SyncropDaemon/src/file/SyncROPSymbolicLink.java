package file;

import static syncrop.ResourceManager.isFileRemovable;
import static syncrop.Syncrop.logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import syncrop.ResourceManager;

public class SyncROPSymbolicLink extends SyncROPFile{

	private String targetPath;
	private File targetFile;
	
	public static boolean isSyncropSymbolicLink(String owner,String target) throws IOException{
		return ResourceManager.getAccount(owner).isPathEnabled(target);
	}
	public static SyncROPItem getInstance(String path,String owner,File f) throws IOException{
		Path targetPath=Files.readSymbolicLink(f.toPath()).toAbsolutePath();
		String target = targetPath.toString().replace(
				ResourceManager.getHome(owner, isFileRemovable(path)), "");
		if(isSyncropSymbolicLink(owner,target)||!Files.exists(targetPath))
			return new SyncROPSymbolicLink(path, owner, target);
		else {
			if(Files.isDirectory(targetPath))
				return new SyncROPDir(path, owner);
			else 
				return new SyncROPFile(path, owner);
		}
	}
	public static SyncROPItem getInstance(String path,String owner,
			long dateModified,long key,boolean modifedSinceLastKeyUpdate,long lastRecordedSize,String filePermissions,
			File f) throws IOException{
		Path targetPath=Files.readSymbolicLink(f.toPath()).toAbsolutePath();
		String target = targetPath.toString().replace(
				ResourceManager.getHome(owner, isFileRemovable(path)), "");
		if(isSyncropSymbolicLink(owner,target)||!Files.exists(targetPath))
			return new SyncROPSymbolicLink(path, owner, dateModified,key,modifedSinceLastKeyUpdate,target,lastRecordedSize,filePermissions);
		else {
			if(Files.isDirectory(targetPath))
				return new SyncROPDir(path, owner,dateModified,lastRecordedSize,filePermissions);
			else 
				return new SyncROPFile(path, owner,dateModified,key,modifedSinceLastKeyUpdate,lastRecordedSize,filePermissions);
		}
	}
	private SyncROPSymbolicLink(String path, String owner,
			String targetPath){
		this(path, owner, -1, -1,false, targetPath,-1,"");
	}
	
	
	public SyncROPSymbolicLink(String path, String owner,
			long modificicationDate,long key,boolean modifedSinceLastKeyUpdate,String targetPath,long lastRecordedSize,String filePermissions){
		super(path, owner, modificicationDate,key,modifedSinceLastKeyUpdate,lastRecordedSize,filePermissions);
		this.targetPath=targetPath;
		targetFile=new File(ResourceManager.getHome(getOwner(), isRemovable()),targetPath);
		
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
		return file.exists()?4:-1;
	}
	@Override
	public boolean createFile(){
		try {
			Files.createSymbolicLink(file.toPath(), targetFile.toPath());
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