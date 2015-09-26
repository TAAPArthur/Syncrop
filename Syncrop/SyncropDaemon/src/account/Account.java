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

import file.Directory;
import file.RemovableDirectory;
import file.SyncROPItem;
import syncrop.ResourceManager;
import syncrop.Syncrop;
/**
 * An account includes all the connection info needed to get files from the server.
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
	private String token;
	
	
	/**
	 * The last recored size of the account measured in GB
	 */
	private long recordedSize;
	/**
	 * The maxium size of an account measured in GB
	 */
	private final static long maximumAccountSize=4L*GIGABYTE;
	
	short warning=0;
	
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
	HashSet<Directory> restrictions = new HashSet<Directory>();

	
	public Account(){}
	public Account(String username,String email,boolean enabled){
		this.name=username;
		this.email=email;
		setEnabled(enabled);
		addDirs((String)null);
		
		addRemoveableDirs((String)null);
		
		String restrinctions=
				"*.metadata*\t*.~lock*\t*.gvfs\t*.thumbnails*"
		+ "\t*.backup*\t*~\t*.git*\t*.dropbox*\t*/proc/*\t*/sys/*\t*/dev/*\t*/run/*\t"
		+ "*.*outputstream*\t*appcompat*\t*/.recommenders/*\t*.attach_pid*";
		for(String restriction:restrinctions.split("\t"))
			addRestrictions(restriction);
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
	public String getHome(boolean removable){
		return ResourceManager.getHome(getName(), removable);
	}
	private void createFolder(){
		File f;
		boolean removable=false;
		do 
		{
			f=new File(ResourceManager.getHome(getName(), removable=!removable));
			if(!f.exists())
			{
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
	
	
	
	public String[] getRestrictionsList(){
		String list[]=new String[restrictions.size()];
		int i=0;
		for(Directory restriction:restrictions)
			list[i++]=restriction.getLiteralDir();
		return list;
	}
	public void addRestrictions(String... dirsToAdd)
	{
		for(int i=0;i<dirsToAdd.length;i++)
			if(dirsToAdd[i]==null||dirsToAdd[i].isEmpty())continue;
			else if(Directory.containsMatchingChars(dirsToAdd[i]))
			{
				restrictions.add(new Directory(dirsToAdd[i], false));
			}
			else if(!SyncROPItem.isValidFileName(dirsToAdd[i]))
			{
				dirsToAdd[i]=removeIllegalChars(dirsToAdd[i], "Restriction");
				i--;
			}
			else restrictions.add(new Directory(dirsToAdd[i], true));
	}
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
	static String removeIllegalChars(String pre,String path,int index)
	{
		return JOptionPane.showInputDialog(pre+" cannot contain illegal char '"+path.charAt(index)+
				"'\nrename file: "+path,path);
	}
	/*
	public boolean enableDir(String dirToEnable){return restrictions.remove(dirToEnable);}
	public boolean disableDir(String dirToDisable){return restrictions.add(dirToDisable);}
	*/
	
	public boolean isPathContainedInDirectory(final String path,boolean removable){
		if(!removable)
			for(Directory dir:directories)
			{
				if(dir.isPathContainedInDirectory(path))
				{
					return true;
				}
			}
		else for(Directory dir:removableDirectories)
		{
			if(dir.isPathContainedInDirectory(path))
			{
				if(new File(ResourceManager.getHome(getName(),removable),dir.getDir()).exists())
				{
					return true;
				}
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
	
	public HashSet<Directory> getDirectories(){return directories;}
	public HashSet<RemovableDirectory> getRemovableDirectories(){return removableDirectories;}
	public HashSet<String> getRemovableDirectoriesThatExists()
	{
		HashSet<String>dirsThatExists=new HashSet<String>();
		for(Directory dir:removableDirectories)
			if(new File(ResourceManager.getHome(getName(),true),dir.getDir()).exists())
				dirsThatExists.add(dir.getDir());
		return dirsThatExists;
	}
	public HashSet<Directory> getRestrictions(){return restrictions;}
		
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
	
	public long getRecordedSize() {
		return recordedSize;
	}
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
	
	public boolean isFull()
	{
		return willBeFull(0);
	}
	public boolean willBeFull(long length)
	{
		return recordedSize+length>maximumAccountSize;
	}
	
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
	 * @return the maximum 
	 */
	public static long getMaximumAccountSize() {
		return maximumAccountSize;
	}
	public static void setMaximumAccountSize(long maximumAccountSize) {
		//Account.maximumAccountSize = maximumAccountSize;
	}
	public short getWarning() {
		return warning;
	}
	public void setWarning(short warning) {
		this.warning = warning;
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
	@Override
	public int hashCode(){
		return name.hashCode();
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
	public void setRestrictions(HashSet<Directory> restrictions) {
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