package file;


import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static syncrop.ResourceManager.getAbsolutePath;
import static syncrop.Syncrop.isNotWindows;
import static syncrop.Syncrop.logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.swing.JOptionPane;

import account.Account;
import daemon.SyncDaemon;
import daemon.client.SyncropClientDaemon;
import daemon.cloud.SyncropCloud;
import settings.Settings;
import syncrop.FileMetadataManager;
import syncrop.ResourceManager;
import syncrop.Syncrop;
import syncrop.SyncropLogger;

public abstract class SyncropItem 
{
	/**
	 * An array of illegal chars
	 */
	public static final String illegalChars[]={"<",">","\\","/",":","\"","|","?","*"};
	
	public final static int INDEX_PATH=0,INDEX_OWNER=1,INDEX_DATE_MODIFIED=2,INDEX_KEY=3,
			INDEX_FILE_PERMISSIONS=4,INDEX_EXISTS=5,INDEX_MODIFIED_SINCE_LAST_KEY_UPDATE=6,
			INDEX_SYMBOLIC_LINK_TARGET=7,
					INDEX_SIZE=8,INDEX_BYTES=9;
	
	public final static int INDEX_LENGTH=10;
	
	public static final String CONFLICT_ENDING=".SYNCROPconflict";
		
	//String illegalCharsRegex="<|>|\\\\|/|:|\"|\\||\\?|\\*";
	protected final String path;
	protected final File file;
	
	protected final String owner;
	protected final boolean removable;
	protected volatile long dateModified=-2;
	/**
	 * the Key of the file; It is used to determine if a conflict should be made;
	 * If this key and the key of another file do not match then the older file will be made 
	 * into a conflict
	 */
	protected int key = 0;
	protected boolean modifiedSinceLastKeyUpdate;
	
	private boolean knownToExists=false;
	private boolean hasBeenUpdated=false;
	private int filePermissions;
	
	private final static PosixFilePermission orderedPermissions[]={OWNER_READ,OWNER_WRITE,OWNER_EXECUTE,    
            GROUP_READ,GROUP_WRITE,GROUP_EXECUTE,
          OTHERS_READ,OTHERS_WRITE,OTHERS_EXECUTE,
	};
	
public static SyncropItem getInstance(Object[] syncData){
		
		final String path=(String)syncData[INDEX_PATH];
		String owner=(String)syncData[INDEX_OWNER];
		Long remoteDateMod=(Long)syncData[INDEX_DATE_MODIFIED];
		int remoteKey=(int)syncData[INDEX_KEY];
		boolean remoteFileExists=(Boolean)(syncData[INDEX_EXISTS]);
		String linkTarget=(String)(syncData[INDEX_SYMBOLIC_LINK_TARGET]);
		boolean remoteUpdatedSinceLastUpdate=(boolean)syncData[INDEX_MODIFIED_SINCE_LAST_KEY_UPDATE];
		long remoteLength=(long)syncData[INDEX_SIZE];
		int remoteFilePermissions=(int)syncData[INDEX_FILE_PERMISSIONS];
		
		return getInstance(path, owner, remoteDateMod, remoteKey, remoteUpdatedSinceLastUpdate, remoteLength, remoteFilePermissions, remoteFileExists, linkTarget);				
	}

	public static SyncropItem getInstance(String path, String owner, long dateModified, int key,
			boolean modifedSinceLastKeyUpdate, long lastRecordedSize, int filePermissions,
			boolean knownToExists,String linkTarget){
		File file=new File(getAbsolutePath(path, owner));
		boolean isDir=SyncropItem.represetsDir(key);
		
		try{
			return getInstance(path, owner,dateModified,key,modifedSinceLastKeyUpdate,
					lastRecordedSize,filePermissions,knownToExists,linkTarget, isDir, file);
		} catch (IllegalArgumentException e) {
			logger.log(e.toString()+"; type of file changed; "+key);
			return getInstance(path, owner,dateModified,key,modifedSinceLastKeyUpdate,
					lastRecordedSize,filePermissions,knownToExists,
					Files.isSymbolicLink(file.toPath())?"":null, file.isDirectory(), file);
		}
	}
	public static SyncropItem getInstance(String path,String accountName,boolean removable){
		File file=new File(ResourceManager.getHome(accountName, removable),path);
		return getInstance(path, accountName, file);
	}
	/**
	 * Creates a SyncropItem isntance with a given file; teh file has to exist
	 * @param path
	 * @param owner
	 * @param file
	 * @return
	 */
	public static SyncropItem getInstance(String path,String owner,File file){
		return getInstance(path, owner,-1,-1,false,-1,0,false,Files.isSymbolicLink(file.toPath())?"":null, file.isDirectory(), file);
	}
	private static SyncropItem getInstance(String path, String owner, long dateModified, int key,
			boolean modifedSinceLastKeyUpdate, long lastRecordedSize, int filePermissions,
			boolean knownToExists,String linkTarget,boolean isDir,File file){
		try{
			if(linkTarget!=null){
				Path absTargetPath;					
				if(file.exists()){
					Path targetPath=Files.readSymbolicLink(file.toPath());
					absTargetPath=targetPath.toAbsolutePath();
				}
				else
					absTargetPath=new File(file,linkTarget).toPath().toAbsolutePath();
				
				if(ResourceManager.getAccount(owner).isAbsPathEnabled(absTargetPath))
					return new SyncropSymbolicLink(path, owner, dateModified,key,modifedSinceLastKeyUpdate,linkTarget,lastRecordedSize,false,filePermissions);
				else {
					if(Files.isDirectory(absTargetPath))
						return new SyncropDir(path, owner,dateModified,knownToExists,filePermissions);
					else 
						return new SyncropFile(path, owner,dateModified,key,modifedSinceLastKeyUpdate,lastRecordedSize,knownToExists,filePermissions);
				}
			}
			else if(isDir)
				return new SyncropDir(path, owner,dateModified,knownToExists,filePermissions);							
			else 
				return new SyncropFile(path, owner,dateModified,key,modifedSinceLastKeyUpdate,lastRecordedSize,knownToExists,filePermissions);
		} catch (IOException e){
			logger.logError(e,"trying to load file metadata");
		}
		return null;		
	}
	
	
	
	public SyncropItem(String path,String owner,long modificicationDate,boolean modifedSinceLastKeyUpdate,long lastRecordedSize,boolean knownToExists,int filePermissions) 
	{
		if(path==null)
			throw new NullPointerException("path cannot be null");
		for(String c:illegalChars)
			if(path.contains(c)&&!File.separator.equals(c))
				path=removeIllegalCharFromFile(path, 
						"path '"+path+"' cannot contain illegal char '"+c+"'",c);
		
		this.removable=ResourceManager.isFileRemovable(path);
		
		this.path=path;
		this.owner=owner;
		this.file=new File(ResourceManager.getHome(owner, removable),path);
		
		this.modifiedSinceLastKeyUpdate=modifedSinceLastKeyUpdate;
		dateModified=modificicationDate;
		this.knownToExists=knownToExists;
		
		this.filePermissions=filePermissions;
		
		
		if(Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)){
			
			updateFilePermissions();
			updateDateModified();
			if(!knownToExists)
				setHasBeenUpdated();
		}
		else if(knownToExists)//if used to exist and doesn't currently
			setHasBeenUpdated();
		
		/*else if(Syncrop.isInstanceOfCloud())
			if(!recordDeletedFile(this))
				logger.log("File can no longer be stored because the list of deleted files is full");
				*/
	}
	
	public int getFilePermissions(){
		return filePermissions;
	}
	public void setFilePermissions(int filePermissions){
		this.filePermissions=filePermissions;
	}
	public static Set<PosixFilePermission> getPosixFilePermissions(int filePermissions){
		String s=Integer.toBinaryString(filePermissions);
		Set<PosixFilePermission> permissions=new HashSet<>();
		for(int i=0;i<orderedPermissions.length;i++)
			if(s.charAt(i)=='1')
				permissions.add(orderedPermissions[i]);
		return permissions;
	}
	public void updateFilePermissions(){
		if(Syncrop.isInstanceOfCloud())return;
		String s="";
		try {
			Set<PosixFilePermission> permissions=Files.getPosixFilePermissions(file.toPath(), LinkOption.NOFOLLOW_LINKS);
			
			for(int i=0;i<orderedPermissions.length;i++)
				if(permissions.contains(orderedPermissions[i]))
					s+="1";
				else s+="0";
			int currentPermissions=Integer.parseInt(s, 2);
			if(filePermissions!=currentPermissions){
				setHasBeenUpdated();
				filePermissions=currentPermissions;
			}
		} catch (IOException e) {
		}
	}
	
	
	
	/**
	 * 
	 * Moves the file denoted by the path to the 1st file denoted by the path with all
	 *  instances of illegalChar removed. If that the file denoted by this new path
	 *  already exists then the path is changed following the conflict rules<br/>
	 *  If an I/O error occurs then the file is disabled;
	 *  NOTE: If this method is called on cloud, the file will just be deleted  
	 * @param path the path of the file
	 * @param message the message to display to the user
	 * @param illegalChar the char that has been deemed illegal
	 * @return the file with the illegalChar removed 
	 * @throws IllegalArgumentException if this method was called on Cloud; 
	 * the file representing this path is then deleted
	 */
	protected String removeIllegalCharFromFile(String path,String message,String illegalChar)throws IllegalArgumentException 
	{
		String home=ResourceManager.getHome(getOwner(), removable);
		String s=null;
		File originalFile=new File(home+path);
		if(Syncrop.isInstanceOfCloud())
			throw new IllegalArgumentException("Error: This file contains illegal chars on Cloud path="+path+". File is being deleted.");
		//TODO prevent naming file to file that already exists
		try {
			s = JOptionPane.showInputDialog(message+"\nrename file: "+path,path);
			if(s!=null&&s.contains(illegalChar))
				s = JOptionPane.showInputDialog(message+"\nIf you do not remove the illegal char, it will be deleted\nrename file: "+path,path);
		} catch (java.awt.HeadlessException e) {
			originalFile.delete();
			throw new IllegalArgumentException("Error: This file contains illegal chars and has no GUI path= "+path+". File is being deleted.");
		}
		if(Files.notExists(originalFile.toPath(), LinkOption.NOFOLLOW_LINKS))
			throw new IllegalArgumentException("The file "+originalFile+" contained illegal charaters, but it no longer exists");
		if(s==null||s.contains(illegalChar))s=path.replace(illegalChar, "");
		if(path.isEmpty())
		{
			path="This_File_HAD_ILLEGAL_CHAR";
		}
		File newFile=new File(home+s);
		int i=0;
		while(newFile.exists())
		{
			newFile=new File(home+s+CONFLICT_ENDING+(++i));
		}
		try {
			Files.move(new File(home+path).toPath(), newFile.toPath());
				logger.log(path+" has been renamed to path "+s);
		} catch (IOException e) {
			logger.logError(e, "An I/O error occured when renaming the file:"+path+
					" file will disabled");
			throw new IllegalArgumentException("Error: This file contains illegal chars and could not be renamed path="+path);
		}
		return s;
	}
	/**
	 * Tests whether the file denoted by this abstract pathname is a directory.
	 * @return true if and only if the file denoted by this abstract pathname exists and is a directory; false otherwise
	 */
	public boolean isDir(){
		return Files.isDirectory(file.toPath());
		//return file.isDirectory();
	}
	public static boolean represetsDir(int key){
		return key<1;
	}
	public boolean isSmallFile(){
		return this.getSize()<=Settings.getMaxTransferSize();
	}
	
	public boolean syncOnFileModification(){
		return !isDir()||isEmpty();
	}
	/**
	 * Checks to see if the file exists
	 * @return true if and only if the file denoted by this SyncROPItem exists; false otherwise
	 */
	public boolean exists() {
		return Files.exists(file.toPath(),LinkOption.NOFOLLOW_LINKS);
	}
	/**
	 * 
	 * @return true if this file does not have any sub directories
	 */
	public boolean isEmpty()
	{
		return file.list()==null||file.list().length==0;
	}
	
	/**
	 * Checks to see if the file is enabled. A file is considered enabled if 
	 * <li>It has not been disabled
	 * <li>It has at least one owner
	 * <li>At least one enabled Account that owns this file considers the file enabled .
	 * <br/><br/>
	 * *Note: While a file is considered enabled if its file size is greater than
	 * {@link SyncropClientDaemon#MAX_FILE_SIZE} in the sense that the file meta will be updated on
	 * change, it will not be synced with Cloud
	 * @see {@link Account#isPathEnabled(String, boolean)} for more detail
	 * @return true if this file is enabled
	 */
	public boolean isEnabled()
	{
		if(file.exists()&&!file.canRead())return false;
		if(path.isEmpty())return true;
		//TODO enabled; ie max size
		return isFileEnabled(file)&&isPathEnabled(path,owner);
	}
	public static boolean isFileEnabled(File file){
		//TODO test hidden symbolic links
		if(!Settings.canSyncHiddenFiles()&&file.isHidden())return false;
		return true;
	}
	
	public static boolean isPathEnabled(String path,String owner){
		return ResourceManager.getAccount(owner).isPathEnabled(path);
	}
	public String getChildPath(String childPath) {
		return getChildPath(path,childPath, removable);
	}
	public static String getChildPath(String parentPath,String childPath,boolean removable) {
		if(parentPath.isEmpty())
			return childPath;
		else if(parentPath.equals(File.separator))
			return parentPath+childPath;
		else 
			return parentPath+File.separator+childPath;
	}

	/**
	 * Returns an owner of the file
	 * @return the first owner of this item alphabetically
	 * @throws NoSuchElementException if there are no owners
	 */
	public String getOwner(){return owner;}
	
	/**
	 * Checks to see if this item is removable. An item is removable if the path is an absolute 
	 * path as opposed to a relative path.
	 * @return true if this file is removable
	 */
	public boolean isRemovable() {
		return removable;
	}
	
	
	public String getName(){return file.getName();}
	/**
	 * 
	 * @return the relative path
	 */
	public String getPath(){return path;}
	public String getHome(){return ResourceManager.getHome(getOwner(), isRemovable());}
	/**
	 * Returns the absolute path of this file. If {@link isInstanceOfCloud()}, then the path 
	 * of the file for the first owner is returned
	 * @return the absolute path of this file
	 */
	public String getAbsPath(){return file.getAbsolutePath();}
	/**
	 * Checks to see if the parent directory exists
	 * @return true if and only if the parent directory exists, false otherwise
	 * @throws NullPointerException if this path does not name a parent directory.
	 */
	public boolean parentDirExists(){return file.getParentFile().exists();}
	
	
	/**
	 * 
	 * @return true if this file is a symbolic link
	 */
	public boolean isSymbolicLink()
	{
		return Files.isSymbolicLink(file.toPath());
	}
	
		
	
	/**
	 * converts the windows-path to a linux supported path
	 * @param path
	 * @return
	 */
	public static String toLinuxPath(String path)
	{	
		path=path.replace(File.separator, "/");
		if(ResourceManager.isFileRemovable(path))
			path=path.replace(":", "");
		return path;
	}
	public static String toWindowsPath(String path)
	{
		path=path.replace("/", File.separator);
		if(ResourceManager.isFileRemovable(path))
			path=path.charAt(0)+":"+path.substring(1);
		return path;
		
	}
		
	public final void save(){
		logger.logTrace("saving "+this);
		FileMetadataManager.updateFileMetadata(this);
	}
	
	/**
	 * deletes the files; file should be check to make sure it is enabled before calling
	 */
	public boolean delete(long dateOfDelection)
	{
		
		if(exists())	
			logger.log("Deleting file: "+this);
		else return false;
		
		dateModified=dateOfDelection;
		if(exists()&&!
				((this instanceof SyncropFile&&file.isFile())||
				(this instanceof SyncropDir&&file.isDirectory()))&&
				!Files.isSymbolicLink(file.toPath()))
		{
			logger.logError(new IllegalArgumentException("Type of SyncropItem does not match file type"),"");
			return false;
		}
		setDateModified(dateOfDelection,false);
		if(file.exists()){
			if(Syncrop.isInstanceOfCloud())
				file.delete();
			else SyncropClientDaemon.sendToTrash(file);
		}
		
		if(file.exists()){
			logger.log("file still exists");
			int count=0;
			do try {count++;Thread.sleep(5);} catch (InterruptedException e) {}
			while(file.exists()&&count<10);
		}
				
		return file.exists();
	}
	
	
		
	public abstract boolean createFile();
	public boolean createFile(long dateModified){
		boolean b=createFile();
		if(exists())
			setDateModified(dateModified);
		return b;
	}
	public boolean createFileWithRecordedTime(){
		return createFile(dateModified);
	}
	

	/**
	 * 
	 * @param path the path to check
	 * @return true if the path does not contain any illegal chars and is not empty
	 */
	public static boolean isValidFileName(String path)
	{
		if(path.isEmpty())return true;
		if(!isNotWindows()&&ResourceManager.isFileRemovable(path))//if windows and removable
			path=path.substring(2);//removes the [A-Z]:
		for(String c:illegalChars)
			if(path.contains(c)&&!File.separator.equals(c))
				return false;
		return true;
	}
	/**
	 * 
	 * @return the last known modification date of the file
	 */
	public long getDateModified(){return dateModified;}
	/**
	 * 
	 * @param l the new length of the file 
	 * measured in milliseconds since the epoch (00:00:00 GMT, January 1, 1970)
	 * @throws IllegalArgumentException if l<0
	 */
	public void setDateModified(long l){
		setDateModified(l,true);
	}
	public void setDateModified(Long l,boolean updateActualFile)
	{
		//l=(l/1000)*1000;
		
		if(Math.abs(dateModified-l)>1000){
			dateModified=l/1000*1000;
			setHasBeenUpdated();
		}
		if(updateActualFile)
			if(file.exists()){
				try {
					Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(l));
				} catch (IOException e) {
					logger.logError(e, "occured while trying to change the modified time of "+file);
				}
			}
		
	}
	/**
	 * updates dateModified to match file.lastModified 
	 */
	private boolean updateDateModified(){
		try {
			if(exists()){
				long currentDateMod=getLastModifiedTime()/1000*1000;
				
				if(hasSameDateModifiedAs(currentDateMod))
					return false;
				if(syncOnFileModification()){
					logger.log("Updating modification date of "+path+": "+
						getDateModified()+" to "+currentDateMod,
						SyncropLogger.LOG_LEVEL_TRACE);
					setHasBeenUpdated();
				}
				dateModified=currentDateMod;
				
				return true;
			}
			else logger.logTrace("update failed");
		} catch (IOException e) {
			logger.logError(e," occured while trying to update modification time of "+this);
		}
		return false;
	}

	protected long getLastModifiedTime() throws IOException{
		return Files.getLastModifiedTime(file.toPath()).toMillis();
	}
	
	
	public void setKey(int key)
	{		
		modifiedSinceLastKeyUpdate=false;
		this.key=key;
		setHasBeenUpdated();
	}
	
	public int getKey()
	{
		return key;
	}
	
	public long getSize(){
		try {
			
			return exists()?Files.size(file.toPath()):0;
		} catch (IOException e) {
			logger.logError(e,"Cannot get size of file "+path);
			return 0;
		}
	}
	public long getLastKnownSize(){
		return 0;
	}

	public File getFile() {return file;}
	public byte[] readAllBytesFile() throws IOException {return Files.readAllBytes(file.toPath());}
	
	public boolean deleteMetadata(){
		return FileMetadataManager.deleteFileMetadata(this);
	}
	
		
	public String getLinkTarget(){return null;}
	/**
	 * Saves key information of file to an object array
	 * @param file the SyncROPItem to format
	 * @return an object array with defining information of the file
	 */
	public Object[] toSyncData()
	{
		Object[] syncData=new Object[INDEX_LENGTH];
		setSyncData(syncData);
		return syncData;
	}
	/**
	 * Saves key information of file to an object array.
	 * This array also includes bytes of the file. Note the bytes may not exceed {@value SyncDaemon#transferSize}
	 * @param file the SyncROPItem to format
	 * @return an object array with defining information of the file
	 */
	public Object[] toSyncData(byte[] bytes)
	{
		Object[] syncData=new Object[INDEX_LENGTH];
		setSyncData(syncData);
		syncData[INDEX_BYTES]=bytes;
		return syncData;
	}
	private void setSyncData(Object[] syncData){
		syncData[INDEX_PATH]=isNotWindows()?
				getPath():
				SyncropItem.toLinuxPath(getPath());
		syncData[INDEX_OWNER]=owner;
		syncData[INDEX_DATE_MODIFIED]=getDateModified();
		syncData[INDEX_KEY]=getKey();
		syncData[INDEX_FILE_PERMISSIONS]=getFilePermissions();
		syncData[INDEX_EXISTS]=exists();
		syncData[INDEX_MODIFIED_SINCE_LAST_KEY_UPDATE]=modifiedSinceLastKeyUpdate();
		syncData[INDEX_SYMBOLIC_LINK_TARGET]=getLinkTarget();
		syncData[INDEX_SIZE]=getSize();
	}
	
	
	
	
	public boolean knownToExists(){return knownToExists;}
	
	
	protected void setHasBeenUpdated(){
		
		hasBeenUpdated=true;
		modifiedSinceLastKeyUpdate=true;
		
	}
	public boolean hasBeenUpdated(){
		return hasBeenUpdated;
	}
	public boolean modifiedSinceLastKeyUpdate(){return modifiedSinceLastKeyUpdate;}
	public void setModifiedSinceLastKeyUpdate(boolean  b){modifiedSinceLastKeyUpdate=b;}
	@Override
	public String toString()
	{
		String dateModified=logger.getDateTimeFormat().format(this.dateModified);
		return getClass().getName()+" path:"+path+", owner:"+owner+" dateMod:"+dateModified+", key: "+getKey()+
				", modifiedSinceLastKeyUpdate: "+modifiedSinceLastKeyUpdate()+
				" exits:"+file.exists()+" deletion recorded: "+!knownToExists()+
				" removeable:"+isRemovable()+" isDir:"+file.isDirectory();
	}
	public boolean isInConflictWith(int key,long size,boolean modifiedSinceLastKeyUpdate){
		if(modifiedSinceLastKeyUpdate||this.modifiedSinceLastKeyUpdate)
			return this.getKey()!=key;
		else 
			return false;
	}
	
	public boolean isNewerThan(long dateModified){
		return (this.dateModified-dateModified)>2000;
	}
	public boolean isOlderThan(long dateModified){
		return (dateModified-this.dateModified)>2000;
	}
	public boolean hasSameDateModifiedAs(long dateModified){
		return Math.abs(dateModified-this.dateModified)<2000;
	}
	
	public boolean isLocked(){
		return ResourceManager.isLocked(path,getOwner());
	}
	public static enum SyncropPostCompare{
		SKIP,SYNCED,DOWNLOAD_REMOTE_FILE,SEND_LOCAL_FILE,SYNC_METADATA,CREATE_NEW_FILE;
	};
	public void mergeMetadata(Object[] syncData) {
		mergeMetadata((long)syncData[INDEX_DATE_MODIFIED],(int)syncData[INDEX_KEY]);
	}
	public void mergeMetadata(long remoteDateMod,int remoteKey) {
		this.setDateModified(remoteDateMod);
		this.setKey(remoteKey);
	}
	public SyncropPostCompare compare(SyncropItem remoteFile) throws IOException{
		return compare(null,this,remoteFile.toSyncData());
	}
	public static SyncropPostCompare compare(String id,SyncropItem localFile,Object[] syncData) throws IOException{
		
		final String path=(String)syncData[INDEX_PATH];
		//SyncropItem localFile=ResourceManager.getFile(path,owner);
		
		logger.log("Comparing "+path,SyncropLogger.LOG_LEVEL_ALL);
		String owner=(String)syncData[INDEX_OWNER];
		Long remoteDateMod=(Long)syncData[INDEX_DATE_MODIFIED];
		int remoteKey=(int)syncData[INDEX_KEY];
		boolean remoteFileExists=(Boolean)(syncData[INDEX_EXISTS]);
		String linkTarget=(String)(syncData[INDEX_SYMBOLIC_LINK_TARGET]);
		boolean remoteUpdatedSinceLastUpdate=(boolean)syncData[INDEX_MODIFIED_SINCE_LAST_KEY_UPDATE];
		long remoteLength=(long)syncData[INDEX_SIZE];
		int remoteFilePermissions=(int)syncData[INDEX_FILE_PERMISSIONS];
		
		byte[]bytes=null;
		if(syncData.length>INDEX_BYTES)
			bytes=(byte[])syncData[INDEX_BYTES];
		
		return SyncropItem.compare(id,localFile, path, owner, remoteDateMod, remoteKey, remoteUpdatedSinceLastUpdate, 
				remoteFilePermissions, remoteFileExists, remoteLength,linkTarget,bytes);
				
	}
	
	public static SyncropPostCompare compare(String id,SyncropItem localFile,String path,String owner,long remoteDateMod,int remoteKey,
			boolean remoteUpdatedSinceLastUpdate,int remoteFilePermissions,boolean remoteFileExists,long remoteLength,String linkTarget,byte[]bytes) throws IOException{
		boolean remoteDir=represetsDir(remoteKey);
		if(localFile==null)
			if(remoteFileExists)
				if (linkTarget==null && !remoteDir) {
					logger.logTrace("Downloading new file"+path);
					return SyncropPostCompare.DOWNLOAD_REMOTE_FILE;
				}
				else 
					return SyncropPostCompare.CREATE_NEW_FILE;
			else 
				return SyncropPostCompare.SKIP;
		
		boolean isLocalFileOlderVersion=localFile.isOlderThan(remoteDateMod);
		boolean isLocalFileNewerVersion=localFile.isNewerThan(remoteDateMod);
		
		if(!localFile.exists()&&!remoteFileExists	//both files don't exist
				||localFile.exists()&&remoteFileExists&&localFile.isSymbolicLink()&&
				linkTarget!=null&&linkTarget.equals(localFile.getLinkTarget())){
			if(isLocalFileOlderVersion){
				if(localFile instanceof SyncropFile)
					((SyncropFile) localFile).mergeMetadata(remoteDateMod, remoteKey);
				return SyncropPostCompare.SYNCED;
			}
			else if(isLocalFileNewerVersion)
				return SyncropPostCompare.SYNC_METADATA;
			else return SyncropPostCompare.SKIP;
		}
		
		else if(localFile.isDir()!=remoteDir){
			logger.logTrace("local and remote file are not of the same type: localFile "+localFile);
			if(localFile.exists()&&remoteFileExists)
				if(remoteDir){//local file is non dir
					((SyncropFile)localFile).makeConflict();
					return SyncropPostCompare.DOWNLOAD_REMOTE_FILE;
				}
				else return SyncropPostCompare.SEND_LOCAL_FILE;
			else if(localFile.exists())
				return SyncropPostCompare.SEND_LOCAL_FILE;
			else 
				return SyncropPostCompare.DOWNLOAD_REMOTE_FILE;
		}
		if(remoteDir)//if both files are dirs
			if(localFile.exists()&&remoteFileExists)
				if (isLocalFileNewerVersion)
					return SyncropPostCompare.SYNC_METADATA;
				else if(isLocalFileOlderVersion){
					localFile.setDateModified(remoteDateMod);
					return SyncropPostCompare.SYNCED;
				}
				else 
					return SyncropPostCompare.SKIP;
			else if(localFile.exists()&&!isLocalFileOlderVersion||!localFile.exists()&&!isLocalFileOlderVersion)
				return SyncropPostCompare.SYNC_METADATA;
			else {
				logger.logTrace("dis are in diffrent states of existance");
				if (remoteFileExists)//local doesn't
					return SyncropPostCompare.DOWNLOAD_REMOTE_FILE;
				else {
					localFile.delete(remoteDateMod);
					return SyncropPostCompare.SYNCED;
				}
			}
		else if(remoteFileExists&&localFile.exists())
			if(localFile.isInConflictWith(remoteKey,remoteLength,remoteUpdatedSinceLastUpdate))
				if(replaceLocalFileOnConflict(id,isLocalFileNewerVersion)){
					
					if(bytes!=null&&localFile.exists()&&Arrays.equals(localFile.readAllBytesFile(),bytes)
							||linkTarget!=null &&linkTarget.equals(localFile.getLinkTarget())){
						if(localFile instanceof SyncropFile)
							((SyncropFile) localFile).mergeMetadata(remoteDateMod, remoteKey);
						return SyncropPostCompare.SYNCED;
					}
					((SyncropFile)localFile).makeConflict();
					return SyncropPostCompare.DOWNLOAD_REMOTE_FILE;
				}
				else return SyncropPostCompare.SEND_LOCAL_FILE;
			else
				if(isLocalFileOlderVersion) {//remote is "newer" copy
					logger.logTrace("Remove file is newer");
					return SyncropPostCompare.DOWNLOAD_REMOTE_FILE;
				}
				else if (isLocalFileNewerVersion) {
					logger.logTrace("local file is newer: "+localFile.getDateModified()+" > "+remoteDateMod);
					return SyncropPostCompare.SEND_LOCAL_FILE;
				}
				else return SyncropPostCompare.SKIP;				
		
		else { //only one exists
			logger.logTrace("only one exists");
			if(remoteFileExists)
				if(!isLocalFileNewerVersion)//remote is "newer" copy
					return SyncropPostCompare.DOWNLOAD_REMOTE_FILE;
				else return SyncropPostCompare.SEND_LOCAL_FILE;
			else //local file exists
				if(!isLocalFileOlderVersion)//local is "newer" copy
					return SyncropPostCompare.SEND_LOCAL_FILE;
				else {
					localFile.delete(remoteDateMod);
					return SyncropPostCompare.DOWNLOAD_REMOTE_FILE;
				}
		}
	}
	private static boolean replaceLocalFileOnConflict(String id,boolean isLocalFileNewerVersion){
		if(Syncrop.isInstanceOfCloud()){
			int remoteConflictResolution=SyncropCloud.getSyncropUser(id).getConflictResolution();
			if(remoteConflictResolution==Settings.LOCAL_FILE_ALWAYS_WINS)
				return true;
			if(remoteConflictResolution==Settings.LOCAL_FILE_ALWAYS_LOSES)
				return false;
		}
		if(Settings.getConflictResolution()==Settings.LOCAL_FILE_ALWAYS_WINS)
			return false;
		if(Settings.getConflictResolution()==Settings.LOCAL_FILE_ALWAYS_LOSES)
			return true;
		return !isLocalFileNewerVersion;
	}
	
}