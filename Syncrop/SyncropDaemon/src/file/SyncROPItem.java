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
import static syncrop.Syncrop.isNotMac;
import static syncrop.Syncrop.isNotWindows;
import static syncrop.Syncrop.logger;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.swing.JOptionPane;

import settings.Settings;
import syncrop.ResourceManager;
import syncrop.Syncrop;
import syncrop.SyncropLogger;
import account.Account;

import com.sun.jna.platform.FileUtils;

import daemon.client.SyncropClientDaemon;

public abstract class SyncROPItem 
{
	/**
	 * An array of illegal chars
	 */
	public static final String illegalChars[]={"<",">","\\","/",":","\"","|","?","*"};
	
	public final static int INDEX_PATH=0,INDEX_OWNER=1,INDEX_DATE_MODIFIED=2,INDEX_KEY=3,
			INDEX_FILE_PERMISSIONS=4,INDEX_EXISTS=5,INDEX_MODIFIED_SINCE_LAST_KEY_UPDATE=6,
			INDEX_SYMBOLIC_LINK_TARGET=7,
					INDEX_BYTES=7,INDEX_SIZE=8;
	
	public final static int INDEX_LENGTH=8;
	public final static int INDEX_LENGTH_EXTENDED=9;
	
	public static final String CONFLICT_ENDING=".SYNCROPconflict";
		
	//String illegalCharsRegex="<|>|\\\\|/|:|\"|\\||\\?|\\*";
	String path;
	File file;
	
	String owner;
	boolean removable;
	volatile long dateModified=-2;
	boolean modifiedSinceLastKeyUpdate;
	
	private static FileUtils fileUtils=null;
	private boolean deletionRecorded=false;
	private boolean hasBeenUpdated=false;
	private String filePermisions;
	
	private final static PosixFilePermission orderedPermissions[]={OWNER_READ,OWNER_WRITE,OWNER_EXECUTE,    
            GROUP_READ,GROUP_WRITE,GROUP_EXECUTE,
          OTHERS_READ,OTHERS_WRITE,OTHERS_EXECUTE,
	};
	
	public SyncROPItem(String path,String owner,long modificicationDate,boolean modifedSinceLastKeyUpdate,boolean deletionRecorded,String filePermissions) 
	{
		for(String c:illegalChars)
			if(path.contains(c)&&!File.separator.equals(c))
				path=removeIllegalCharFromFile(path, 
						"path '"+path+"' cannot contain illegal char '"+c+"'",c);
		this.removable=ResourceManager.isFileRemovable(path);
		
		if(path==null)
			throw new NullPointerException("path cannot be null");
		this.path=path;
		this.owner=owner;
		this.file=new File(ResourceManager.getHome(owner, removable),path);
		
		this.modifiedSinceLastKeyUpdate=modifedSinceLastKeyUpdate;
		dateModified=modificicationDate;
		this.deletionRecorded=deletionRecorded;
		
		if(filePermissions.isEmpty())
			updateFilePermissions();
		else this.filePermisions=filePermissions;
		
		
		if(Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)){
			updateDateModified();
			if(deletionRecorded){
				setHasBeenUpdated();
			}
		}
		else if(!deletionRecorded){//if used to exist and doesn't exist
			setHasBeenUpdated();
		}
		
		
		/*else if(Syncrop.isInstanceOfCloud())
			if(!recordDeletedFile(this))
				logger.log("File can no longer be stored because the list of deleted files is full");
				*/
	}
	
	
	public String getFilePermissions(){
		return filePermisions;
	}
	public static Set<PosixFilePermission> getPosixFilePermissions(String filePermisions){
		Set<PosixFilePermission> permissions=new HashSet<>();
		for(int i=0;i<orderedPermissions.length;i++)
			if(filePermisions.charAt(i)!='-')
				permissions.add(orderedPermissions[i]);
		return permissions;
	}
	public void updateFilePermissions(){
		String s="";
		try {
			Set<PosixFilePermission> permissions=Files.getPosixFilePermissions(file.toPath(), LinkOption.NOFOLLOW_LINKS);
			
			
			for(int i=0;i<orderedPermissions.length;i++)
				if(permissions.contains(orderedPermissions[i])){
					if(i%3==0)s+="r";
					else if(i%3==1)s+="w";
					else s+="x";
				}
				else s+="-";
		} catch (IOException e) {
		}
		filePermisions=s;
	}
	/**
	 * Used to initialize {@link #fileUtils} which is used to send files to trash. Only call if os is Mac or Windows
	 */
	public static void initializeFileUtils(){
		fileUtils = FileUtils.getInstance();
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
		return Files.isDirectory(file.toPath(),LinkOption.NOFOLLOW_LINKS);
		//return file.isDirectory();
	}
	
	/**
	 * Checks to see if the file exists
	 * @return true if and only if the file denoted by this SyncROPItem exists; false otherwise
	 */
	public boolean exists() {
		return Files.exists(file.toPath());
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
	public String getAbsPath(){return ResourceManager.getHome(getOwner(), isRemovable())+path;}
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
	
	/**
	 * Sends a file to trash bin
	 * TODO Windows and Mac support
	 * @param f the file to send to trash
	 */
	void sendToTrash(File f)
	{
		if(!file.exists())return;
		if(logger.isDebugging())
			logger.log("Sending file to trash path="+f);
		
		try {
			if(isNotWindows()&&isNotMac())
				sendToLinuxTrash(f);
			else fileUtils.moveToTrash( new File[] {file});
			/*else if(AccountManager.notWindows)
				sendToWindowsTrash(f);
			else sendToMacTrash(f);*/
		}
		
		catch (IOException e) {
			logger.logError(e, "occured while trying to send file to trash path="+path);
		}
		
	}
	
	private void sendToLinuxTrash(File f) throws IOException
	{
		String baseName=file.getName(),name=baseName;
		File trashInfoFile=new File(System.getProperty("user.home")+"/.local/share/Trash/info",name+".trashinfo");
		for(int i=2;trashInfoFile.exists();i++)
		{
			name=baseName+="."+i;
			trashInfoFile=new File(System.getProperty("user.home")+"/.local/share/Trash/info",name+".trashinfo");
		}			
		File trashFile=new File(System.getProperty("user.home")+"/.local/share/Trash/files",name);
		Files.move(f.toPath(), trashFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

		logger.log(f+" was sent to trash");
		//DeletionDate=2014-03-01T23:38:18
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		trashInfoFile.createNewFile();
		PrintWriter out=new PrintWriter(trashInfoFile);
		out.println("[Trash Info]");
		out.println("Path="+f.getAbsolutePath());
		out.println("DeletionDate="+dateFormat.format(System.currentTimeMillis()).replace(" ", "T"));
		out.close();
	
	}
	/*
	private void sendToWindowsTrash(File f)throws IOException
	{
		String os=System.getProperty("os.name").toLowerCase();
		File bin=new File("C:\\"+(
				os.equals("windows 2000")||os.equals("windows nt")||os.equals("windows xp")?
					"RECYCLER":
					os.contains("windows 98")||os.contains("95")?
							"RECYCLED":
							"$Recycle.Bin")
							);
		File deletedFileDir=new File(bin,bin.list()[0]);
		String split[]=f.getName().split("\\.");
		String extension=split[split.length-1];
		File deletedFile=null;
		String basename=null;
		File deletedFileInfo=null;
		do 
		{
			basename="S"+getRandomSring(5)+"."+extension;
			deletedFile=new File(deletedFileDir,"$R"+basename);
			deletedFileInfo=new File(deletedFileDir,"$I"+basename);
		}
		while (deletedFile.exists());
		deletedFileInfo.createNewFile();
		PrintWriter out=new PrintWriter(f);
		out.write(path);
		out.close();
		Files.move(f.toPath(),deletedFile.toPath(),StandardCopyOption.REPLACE_EXISTING);
	}
	
	private String getRandomSring(int length){
		String s="";
		for(int i=0;i<length;i++)
			s+=getRandomLetter();
		return s;
	}
	private char getRandomLetter()
	{
		return (char)(Math.random()*26+65);
	}
	
	private void sendToMacTrash(File f)throws IOException
	{
		throw new IllegalAccessError("Cannot access this method");
	}
	*/
	
	public final void save(){
		ResourceManager.writeFile(this);
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
				((this instanceof SyncROPFile&&file.isFile())||
				(this instanceof SyncROPDir&&file.isDirectory())))
		{
			logger.logError(new IllegalArgumentException("Type of SyncropItem does not match file type"),"");
			return false;
		}
		if(file.exists()){
			if(Syncrop.isInstanceOfCloud())
				file.delete();
			else sendToTrash(file);
			tryToCreateParentSyncropDir();
		}
		
		if(file.exists()){
			logger.log("file still exists");
			int count=0;
			do try {count++;Thread.sleep(5);} catch (InterruptedException e) {}
			while(file.exists()&&count<10);
		}
				
		return true;
	}
	public boolean tryToCreateParentSyncropDir(){
		if(!file.getParentFile().exists()||file.getParentFile().list().length==0){
			if(ResourceManager.getFile(getParentPath(), owner)==null){
				logger.logTrace(file.getParent()+" is now empty; creating metadata");
				ResourceManager.writeFile(new SyncROPDir(getParentPath(), owner));
				return true;
			}
			
		}
		return false;
	}
	private String getParentPath(){return this.path.replace(File.separatorChar+file.getName(), "");}
	
	public void rename(File destFile) throws IOException
	{
		
		//gets relative path
		String newPath=destFile.getAbsolutePath().replaceFirst(ResourceManager.getHome(owner, isRemovable()), "");
		dateModified=file.lastModified();
		
		Files.move(file.toPath(), destFile.toPath(),
				StandardCopyOption.ATOMIC_MOVE);
			
		
		file=destFile;
		logger.logTrace(logger.getDateTimeFormat().format(dateModified)
				+"Vs"+logger.getDateTimeFormat().format(file.lastModified()));
		file.setLastModified(dateModified);
		
		logger.log("file:"+path+" has been renamed to "+newPath);
		path=newPath;
		save();//add this file to record with a different path	
	}
	protected void updateFilePath(String newPath){
		path=newPath;
	}
	
	public abstract boolean createFile();
	public boolean createFile(long dateModified){
		boolean b=createFile();
		if(exists())
			setDateModified(dateModified);
		return b;
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
	public void setDateModified(Long l)
	{
		//l=(l/1000)*1000;
		dateModified=l;
		if(file.exists()){
			try {
				//System.out.println(l+" "+FileTime.fromMillis(l));
				
				Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(l));
			} catch (IOException e) {
				logger.logError(e, "occured while trying to change the modified time of "+file);
			}
		}
		setHasBeenUpdated();
	}
	/**
	 * updates dateModified to match file.lastModified 
	 */
	public boolean updateDateModified(){
		try {
			if(exists()){
				long currentDateMod=Files.getLastModifiedTime(file.toPath(), LinkOption.NOFOLLOW_LINKS).toMillis();
				if(hasSameDateModifiedAs(currentDateMod))
					return false;
				logger.log("Updating modification date of "+path+": "+
						getDateModified()+" to "+currentDateMod,
						SyncropLogger.LOG_LEVEL_TRACE);
				dateModified=currentDateMod;
				setHasBeenUpdated();
				return true;
			}
			else logger.logTrace("update failed");
		} catch (IOException e) {
			logger.logError(e," occured while trying to update modification time of "+this);
		}
		return false;
	}
	public long getKey(){return -1;}
	
	public long getSize(){
		return 0;
	}
	public long getLastKnownSize(){
		return 0;
	}

	public File getFile() {return file;}
	
	public boolean remove(){return ResourceManager.deleteFile(this);}
	
	
	
	
	public String getTargetPath(){return null;}
	/**
	 * Saves key information of file to an object array
	 * @param file the SyncROPItem to format
	 * @return an object array with defining information of the file
	 */
	public Object[] formatFileIntoSyncData()
	{
		Object[] syncData=new Object[INDEX_LENGTH];
		syncData[INDEX_PATH]=isNotWindows()?
				getPath():
				SyncROPItem.toLinuxPath(getPath());
		syncData[INDEX_OWNER]=owner;
		syncData[INDEX_DATE_MODIFIED]=getDateModified();
		syncData[INDEX_KEY]=getKey();
		syncData[INDEX_FILE_PERMISSIONS]=getFilePermissions();
		syncData[INDEX_EXISTS]=exists();
		syncData[INDEX_MODIFIED_SINCE_LAST_KEY_UPDATE]=modifiedSinceLastKeyUpdate();
		syncData[INDEX_SYMBOLIC_LINK_TARGET]=getTargetPath();
		return syncData;
	}
	
	public Object[] formatFileIntoSyncData(byte[] bytes){
		return formatFileIntoSyncData(bytes, getSize());
	}
	
	public Object[] formatFileIntoSyncData(byte[] bytes,long size)
	{
		Object[] syncData=new Object[INDEX_LENGTH_EXTENDED];
		syncData[INDEX_PATH]=isNotWindows()?
				getPath():
				toLinuxPath(getPath());
		syncData[INDEX_OWNER]=getOwner();
		syncData[INDEX_DATE_MODIFIED]=getDateModified();
		syncData[INDEX_KEY]=getKey();
		syncData[INDEX_FILE_PERMISSIONS]=getFilePermissions();
		syncData[INDEX_EXISTS]=exists();
		syncData[INDEX_MODIFIED_SINCE_LAST_KEY_UPDATE]=modifiedSinceLastKeyUpdate();
		syncData[INDEX_BYTES]=bytes;
		syncData[INDEX_SIZE]=size;
		return syncData;
	}
	
	public File getMetadataFile(){
		return ResourceManager.getMetadataFile(path, owner);
	}
	public void unrecordDeletion(){deletionRecorded=false;}
	public void recordDeletion(){deletionRecorded=true;}
	public boolean isDeletionRecorded(){return deletionRecorded;}
	
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
		return "path:"+path+", owner:"+owner+" dateMod:"+dateModified+", key: "+getKey()+
				", modifiedSinceLastKeyUpdate: "+modifiedSinceLastKeyUpdate()+
				" exits:"+file.exists()+" deletion recorded "+isDeletionRecorded()+
				" removeable:"+isRemovable()+" isDir:"+file.isDirectory();
	}
	
	public boolean isDiffrentVersionsOfSameFile(long key,boolean modifiedSinceLastKeyUpdate){
		
		if(modifiedSinceLastKeyUpdate||this.modifiedSinceLastKeyUpdate)
			return this.getKey()==key;
		else return true;
		
	}
	public boolean isNewerThan(long dateModified){
		return (this.dateModified-dateModified)>1000;
	}
	public boolean isOlderThan(long dateModified){
		return (dateModified-this.dateModified)>1000;
	}
	public boolean hasSameDateModifiedAs(long dateModified){
		return Math.abs(dateModified-this.dateModified)<1000;
	}
	
	public boolean isLocked(){
		return ResourceManager.isLocked(path,getOwner());
	}	
	public static String generateShareKey(String path,String owner){
		return Integer.toString((path+owner).hashCode(),36)+
				Long.toString(System.currentTimeMillis()%(1000*3600*24*365), 36);
	}
}