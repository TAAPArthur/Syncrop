package account;

import static notification.Notification.displayNotification;
import static syncrop.Syncrop.GIGABYTE;
import static syncrop.Syncrop.isInstanceOfCloud;
import static syncrop.Syncrop.isNotWindows;
import static syncrop.Syncrop.logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;

import javax.swing.JOptionPane;

import syncrop.RecursiveDeletionFileVisitor;
import syncrop.ResourceManager;
import syncrop.Syncrop;
import file.Directory;
import file.RemovableDirectory;
import file.Restriction;
import file.SyncROPItem;
/**
 * An account includes all the connection info needed to get files from the server.
 * An Account is uniquely identified by its username, so if two accounts have the
 * same name, they are the same account.
 * <br/><br/>
 * Account contains three major sets:
 * <ul>
 * <li>Directories -the paths of directives to sync relative to the user's home directory
 * <li>Removable Directories - the absolute path of directories. 
 * If the Removable Directory does not exists its childern are not considered deleted.
 * This allows removable media like flash drives to be synced.
 * <li>Restrictions -the abs path of directories that should not be synced. Accepts wildcards
 * </ul>
 *
 */
public class Account
{
	/**
	 * The name of the account
	 */
	private String name="";
	/**
	 * The email of the account
	 */
	private String email;
	/**
	 * The token of the account; it is used for authentication instead of a password 
	 */
	private String token;
	
	
	/**
	 * The last recored size of the account measured in GB
	 */
	private long recordedSize;
	/**
	 * The maxium size of an account measured in bytes
	 */
	private static long maximumAccountSize=4L*GIGABYTE;
	
	/**
	 * warns teh user about the 
	 */
	private short warning=0;
	
	/**
	 * if enabled the files of this account will be synced with the cloud. File of 
	 * this account may still be synced even if this class is disabled if an 
	 * enabled class also has those files
	 */
	private boolean enabled=true;
	
	/**
	 * If the account has been authenticated by cloud. If it has not, then it will not be synced 
	 */
	private boolean authenticated=true;
	
	
	/**
	 * directories that can be removed and will not be deleted 
	 * if the parent directory is not found. The absolute path should be given
	 */
	HashSet<RemovableDirectory>removableDirectories=new HashSet<RemovableDirectory>();
	
	/**
	 * no directories should include System.getProperty("user.home"); 
	 * This is implied and will be added dynamically
	 */
	HashSet<Directory> directories = new HashSet<Directory>();
	/**
	 * Dirs that should not be included in {@link #directories}. 
	 * The path given should be the absolute path
	 */
	HashSet<Restriction> restrictions = new HashSet<Restriction>();
	
	/**
	 * A tab separated list of the default restrictions. The default resticts aren't
	 * added when using the default constructor. 
	 */
	private static final String defaultRestrictions=
			"*.metadata*\t*.~lock*\t*.gvfs\t*.thumbnails*"
	+ "\t*.backup*\t*~\t*.git*\t*.dropbox*\t*/proc/*\t*/sys/*\t*/dev/*\t*/run/*"
	+ "\r*.*outputstream*\t*appcompat*\t*/.recommenders/*\t*.attach_pid*\t*.tmp"
	+ "\t*.settings*\t*/sys/*\t*.bash_history\t*.classpath\t*.project";

	/**
	 * Creates an empty account; Username, email and token need to be specified manually
	 */
	public Account(){}
	
	/**
	 * @param username the name of the Account
	 * @param email the email associated with the Account
	 * @param enabled true if the account is enabled
	 */
	public Account(String username,String email,boolean enabled){
		this.name=username;
		this.email=email;
		setEnabled(enabled);
		addDirs((String)null);
		
		addRemoveableDirs((String)null);
		
		for(String restriction:defaultRestrictions.split("\t"))
			addRestrictions(restriction);
		
		//create home dir if on Cloud
		if(isInstanceOfCloud())
			createFolder();
	}
	/**
	 * 
	 * @param username the username of this account
	 * @param password the password of this account
	 * @param enabled if this account is enabled
	 * @param restrictions directories that this account should not sync
	 * @param directories directories that this account should be sync if they are 
	 * not in restricted
	 * @param removableDirectories directories that can be removed and will not be deleted 
	 * if the parent directory is not found
	 */
	public Account(String username,String email,String refreshToken,boolean enabled,String[] restrictions,
			String[] directories,String[]removableDirectories)
	{
		this.name=username;
		this.email=email;
		this.token=refreshToken;
		setEnabled(enabled);
		addRestrictions(restrictions);
		addDirs(directories);
		addRemoveableDirs(removableDirectories);
		if(isInstanceOfCloud())
			createFolder();
	}
	/**
	 * Returns the home directory of the Account
	 * @param removable if true the home for removable files is returned 
	 * @return the home directory of the Account (either removable or non removable)
	 * @see ResourceManager#getHome(String, boolean)
	 */
	public String getHome(boolean removable){
		return ResourceManager.getHome(getName(), removable);
	}
	/**
	 * Recursively deletes the home folders (both removable and non removable).
	 * This deletion is permante and should only be run when a user account has been removed
	 * on Cloud. 
	 * @throws IOException if an IO error occured while deleting
	 */
	public void deleteFolder() throws IOException{
		boolean removable=false;
		logger.log("Deleting Account folder for "+getName());
		do
		{
			File metadatahome=ResourceManager.getMetadataHome(getName(), removable);
			if(metadatahome.exists()){
				Files.walkFileTree(metadatahome.toPath(), new RecursiveDeletionFileVisitor());
			}
			File home=new File(ResourceManager.getHome(getName(), removable=!removable));
			if(home.exists())
				Files.walkFileTree(home.toPath(), new RecursiveDeletionFileVisitor());
		}
		while(removable);
	}
	/**
	 * Creates the home folders (both removable and non removable).
	 * This method should only be run on Cloud
	 */
	public void createFolder(){
		File f;
		boolean removable=false;
		do 
		{
			f=new File(ResourceManager.getHome(getName(), removable=!removable));
			if(!f.exists()){
				logger.log("creating parent dir for account " +
						getName()+" removable:"+removable);
				if(!f.mkdirs())
					logger.log("Account folder failed to be created"+f);
			}
		}
		while(removable);
		
		
	}
	
	@Override
	public String toString()
	{
		return "name: '"+name+"' enabled:'"+enabled+
				"' restrictions: '"+restrictions+"' dirs: '"+directories+
				"' removable dirs: '"+removableDirectories+"'";
	}
	
	
	/**
	 * 
	 * @return a String[] of all the restrictions
	 */
	public String[] getRestrictionsList(){
		String list[]=new String[restrictions.size()];
		int i=0;
		for(Directory restriction:restrictions)
			list[i++]=restriction.getLiteralDir();
		return list;
	}
	/**
	 * 
	 * @param dirsToAdd adds a restriction
	 */
	public void addRestrictions(String... dirsToAdd)
	{
		for(int i=0;i<dirsToAdd.length;i++)
			if(dirsToAdd[i]==null||dirsToAdd[i].isEmpty())continue;
			else if(Directory.containsMatchingChars(dirsToAdd[i]))
				restrictions.add(new Restriction(dirsToAdd[i], false));
			else if(!SyncROPItem.isValidFileName(dirsToAdd[i]))
			{
				dirsToAdd[i]=removeIllegalChars(dirsToAdd[i], "Restriction");
				i--;
			}
			else restrictions.add(new Restriction(dirsToAdd[i], true));
	}
	/**
	 * Adds a non-removable directory.
	 * @param dirsToAdd the directory to add
	 */
	public void addDirs(String... dirsToAdd)
	{
		if(isInstanceOfCloud())
			directories.add(new Directory("*",false));
		else 
			for(int i=0;i<dirsToAdd.length;i++)
				if(dirsToAdd[i]==null||dirsToAdd[i].isEmpty())continue;
				else if(Directory.containsMatchingChars(dirsToAdd[i]))
					directories.add(new Directory(dirsToAdd[i], false));
				else if(!SyncROPItem.isValidFileName(dirsToAdd[i]))
				{
					dirsToAdd[i]=removeIllegalChars(dirsToAdd[i], "Directory");
					i--;
				}
				else directories.add(new Directory(dirsToAdd[i], true));
	}
	
	/**
	 * Adds a removable directory
	 * @param dirsToAdd the removable directory to add
	 */
	public void addRemoveableDirs(String... dirsToAdd)
	{
		if(isInstanceOfCloud())
			removableDirectories.add(new RemovableDirectory(
					ResourceManager.getHome(getName(), true),this));
		else
			for(int i=0;i<dirsToAdd.length;i++)
				if(dirsToAdd[i]==null||dirsToAdd[i].isEmpty())continue;
				else if(Directory.containsMatchingChars(dirsToAdd[i]))
				{
					dirsToAdd[i]=removeIllegalChars(dirsToAdd[i], "Removable Directory");
					i--;
					//removableDirectories.add(new Directory(dirsToAdd[i], false));
				}
				else if(!SyncROPItem.isValidFileName(dirsToAdd[i]))
				{
					dirsToAdd[i]=removeIllegalChars(dirsToAdd[i], "Removable Directory");
					i--;
				}
				else removableDirectories.add(new RemovableDirectory(dirsToAdd[i],this));
	}
	/**
	 * Prompts the user to change the name of dir to make it legal. 
	 * The user is continully prompted until the change has been made.
	 * @param dir the path of the dir containing the illegal dir
	 * @param title a description of the dir
	 * @return
	 */
	static String removeIllegalChars(String dir,String title)
	{
		Matcher m;
		while((m=ResourceManager.illegalCharsPattern.matcher(
				!isNotWindows()&&ResourceManager.isFileRemovable(dir)?
						dir.substring(2):
						dir
				)).find())
			dir=removeIllegalChars(title, dir, m.start());
		return dir;
	}
	/**
	 * Prompts the user to change the name of dir to make it legal. 
	 * The user is continually prompted until the change has been made.
	 * @param pre a description of the dir
	 * @param path the path of the dir containing the illegal dir
	 * @param indec the position of the illegal char
	 * @return
	 */
	static String removeIllegalChars(String pre,String path,int index)
	{
		return JOptionPane.showInputDialog(pre+" cannot contain illegal char '"+path.charAt(index)+
				"'\nrename file: "+path,path);
	}
	
	/**
	 * 
	 * @param path the path to check
	 * @param removable if the path is removable or not
	 * @return true if and only if the path is a child of a removable or non removable directory
	 */
	public boolean isPathContainedInDirectory(final String path,boolean removable){
		if(!removable)
			for(Directory dir:directories){
				if(dir.isPathContainedInDirectory(path))
					return true;
			}
		else for(Directory dir:removableDirectories){
			if(dir.isPathContainedInDirectory(path))
				if(new File(ResourceManager.getHome(getName(),removable),dir.getDir()).exists())
				{
					return true;
				}
		}
		return false;
	}
	/**
	 * A files is considered disabled if the account is disabled, 
	 * its path is not included in directories or its path 
	 * included in the restrictions.
	 *  
	 * @param path the path to check
	 * @param removable if the path is removable or not
	 * @return
	 */
	public boolean isPathEnabled(final String path)
	{
		boolean removable=ResourceManager.isFileRemovable(path);
		if(!isEnabled())
		{
			logger.logDebug("path "+path+
				" is not considered enabled by Account"+getName()+" because the account is not enabled");
			return false;
		}
		boolean dirIsOwned=isPathContainedInDirectory(path,removable);
		
		if(!dirIsOwned)
		{	
			//logger.logDebug(directories.toString()+" "+removableDirectories.toString());
			logger.logDebug("file "+path+" is not enabled because it is not included in " +
					(!removable?"dir":"removable dir or the " +
				"removable dir it is contained in does not exists"));
			return false;
		}
		
		if(!Syncrop.isInstanceOfCloud())//cloud does not have restrictions
			for(Directory dir:this.restrictions){
				if(dir.isPathContainedInDirectory(path))
					return false;
			}
		
		return true;
	}
	/**
	 * @return a set or non-removable directories
	 */
	public HashSet<Directory> getDirectories(){return directories;}
	/**
	 * @return a set or removable directories
	 */
	public HashSet<RemovableDirectory> getRemovableDirectories(){return removableDirectories;}
	/**
	 * @return a set or removable directories that exist
	 */
	public HashSet<String> getRemovableDirectoriesThatExists()
	{
		HashSet<String>dirsThatExists=new HashSet<String>();
		for(Directory dir:removableDirectories)
			if(new File(ResourceManager.getHome(getName(),true),dir.getDir()).exists())
				dirsThatExists.add(dir.getDir());
		return dirsThatExists;
	}
	/**
	 * 
	 * @return a set of restrictions
	 */
	public HashSet<Restriction> getRestrictions(){return restrictions;}
		
	/**
	 * Calculates the size of the account by summing the size of every file.
	 */
	public void calculateSize(){
		try {
			long size=0;
			//checks regular files
			String home=ResourceManager.getHome(getName(), false);
			for (Directory parentDir : getDirectories())
				size+=getSize(home+File.separator+(parentDir.isLiteral()?parentDir.getDir():""),false);
			//checks removable files
			for (RemovableDirectory parentDir : getRemovableDirectories())
				if(parentDir.exists())
					size+=getSize(parentDir.getDir(),true);
			setRecordedSize(size);
			
		} catch (IOException e) {
			logger.logError(e, "occured while trying to measure account size");
		}
	}
	
	/**
	 * Recursivly sums all children of startPath
	 * @param startPath the path to start with
	 * @param removable if startPath is removable
	 * @return the sum of the sizes of all children of startPath
	 * @throws IOException
	 */
	private long getSize(String startPath,final boolean removable) throws IOException {
	    final AtomicLong size = new AtomicLong(0);
	    Path path = Paths.get(startPath);
	    
	    //new SimpleFileVisitor<Path>().visitFileFailed(file, exc)
	    Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
	    	public FileVisitResult visitFileFailed(Path file,IOException e){
	    		Syncrop.logger.logTrace("Could not access file: "+e);
	    		return FileVisitResult.SKIP_SUBTREE;
	    	}
	    	@Override
	    	public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
	        	String path=removable?file.toString():
	        		file.toString().replace(ResourceManager.getHome(getName(), removable), "");
	        	if(attrs.isDirectory())
	        		Syncrop.sleepShort();
	        	if(Files.isReadable(file)&&isPathEnabled(path)){
	        		size.addAndGet(attrs.size());
		            return FileVisitResult.CONTINUE;	
	        	}
	        	else{
	        		return FileVisitResult.SKIP_SUBTREE;
	        	}
	        }
	        
	    });
	    return size.get();
	}
	/**
	 * 
	 * @return the last recorded size of the Account; may not be accurate
	 */
	public long getRecordedSize() {
		return recordedSize;
	}
	/**
	 * Sets the recorded size of the Account.
	 * This method does not actually change the size of the Account, but is used to 
	 * report to the user how much space is left.
	 * @param recordedSize the new size of the Account
	 */
	public void setRecordedSize(long recordedSize) 
	{
		this.recordedSize = recordedSize;
		if(warning!=2&&this.recordedSize/maximumAccountSize>1.0)
		{
			warning=2;
			displayNotification("Account "+getName()+
					" is can no longer be synced because it is too large");
		}
		else if(warning!=1&&this.recordedSize/maximumAccountSize>.9)
		{
			warning=1;
			displayNotification("Account "+getName()+
					" over 90% full! When it exceeds 100% the account will be disabled");
		}
		else warning=0;
	}
	
	/**
	 * 
	 * @return true if the Account has exceeded its maxium size
	 * @see #willBeFull(long)
	 */
	public boolean isFull()
	{
		return willBeFull(0);
	}
	/**
	 * 
	 * @param deltaLength the change in length of the account
	 * @return true if the Account will exceed its maxium size if deltaLength bytes are added.
	 */
	public boolean willBeFull(long deltaLength)
	{
		return recordedSize+deltaLength>maximumAccountSize;
	}
	
	/**
	 * 
	 * @return the name of the account
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Sets the name of this account to name. Because Accounts are identified by name,
	 * changing the name changes the {@link #hashCode()}. As a result, this Account should be
	 * removed from any HashMap/HashSet before this method is called and re-added after.
	 * @param name the new name of this account
	 */
	public void setName(String name) {
		this.name = name;
	}
	
	
	/**
	 * 
	 * @return the maximum Account size {@value #maximumAccountSize} bytes 
	 * @see #maximumAccountSize
	 */
	public static long getMaximumAccountSizeInBytes() {
		return maximumAccountSize;
	}
	public static long getMaximumAccountSizeInMegaBytes() {
		return maximumAccountSize/Syncrop.MEGABYTE;
	}
	/**
	 * 
	 * @param maximumAccountSize -new max size in bytes
	 */
	public static void setMaximumAccountSize(long maximumAccountSize) {
		Account.maximumAccountSize = maximumAccountSize;
	}
	
	public boolean isEnabled() {
		return enabled&&maximumAccountSize>=recordedSize;
	}
	public void setEnabled(boolean enabled) {
		if(!enabled)
			logger.logTrace("Disabling account"+getName());
		this.enabled = enabled;
	}
	public boolean isAuthenticated() {
		return authenticated;
	}
	public void setAuthenticated(boolean authenticated) {
		this.authenticated = authenticated;
	}
	@Override
	public boolean equals(Object o){
		return o instanceof Account&&name.equals(((Account)o).getName());
		
	}	
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public String getToken() {
		return token;
	}
	public void setRefreshToken(String refreshToken) {
		this.token = refreshToken;
	}
	public void setRemovableDirectories(
			HashSet<RemovableDirectory> removableDirectories) {
		this.removableDirectories = removableDirectories;
	}
	public void setDirectories(HashSet<Directory> directories) {
		this.directories = directories;
	}
	public void setRestrictions(HashSet<Restriction> restrictions) {
		this.restrictions = restrictions;
	}
	public void clear(){
		restrictions.clear();
		directories.clear();
		removableDirectories.clear();
	}
	/**
	 * 
	 * @param absPath -the absolute path to check
	 * @return true if the absPath is in at least on of the Account's directories or removable directories
	 * @see Account#isPathContainedInDirectory(String, boolean)
	 */
	public boolean isPathContainedInDirectories(String absPath){
		if(getHome(true).startsWith(absPath)||getHome(false).startsWith(absPath))
			return true;
		else
			try {
				return isPathContainedInDirectory(absPath.substring(getHome(true).length()),true)||
					isPathContainedInDirectory(absPath.substring(getHome(false).length()),false);
			} catch (StringIndexOutOfBoundsException e) {return false;}
	}
}