package file;

import static syncrop.Syncrop.logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;

public class SyncROPFile extends SyncROPItem 
{	
	
	/**
	 * the Key of the file; It is used to determine if a conflict should be made;
	 * If this key and the key of another file do not match then the older file will be made 
	 * into a conflict
	 */
	private long key;
	private long lastRecordedSize;
	
	public SyncROPFile(String path,String owner){
		this(path, owner, 0);
	}
		
	public SyncROPFile(String path, String owner,long key)
	{
		this(path,owner,0, key,false,-1,false,"");
	}
	
	public SyncROPFile(String path,String owner,long modificicationDate,long key,long lastRecordedSize){
		this(path, owner, modificicationDate, key,false,lastRecordedSize, false,"");
	}
	public SyncROPFile(String path,String owner,long modificicationDate,long key,boolean modifedSinceLastKeyUpdate,long lastRecordedSize,boolean knownToExists,String filePermisions)
	{
		super(path, owner, modificicationDate,modifedSinceLastKeyUpdate,knownToExists,filePermisions);
		
		if(key<=0)
			key=dateModified;
		this.key=key;
		if(exists()&&Files.isDirectory(file.toPath(),LinkOption.NOFOLLOW_LINKS)){
			throw new IllegalArgumentException("path "+path+" denotes a directory so "
					+ "it cannot be a SyncROPFile");
		}
	}
	
	/**
	 * Read all the bytes from a file. The method ensures that the file is closed when all 
	 * bytes have been read or an I/O error, or other runtime exception, is thrown.<br/>
	 * Note that this method is intended for simple cases where it is convenient to read all bytes into a byte array. It is not intended for reading in large files.
	 * @return a byte array containing the bytes read from the file
	 * @throws IOException  if an I/O error occurs reading from the stream
	 * @throws OutOfMemoryError  if an array of the required size cannot be allocated, for example the file 
	 * is larger that 2GB
	 * @throws SecurityException  In the case of the default provider, and a security manager is 
	 * installed, the checkRead method is invoked to check read access to the file.
	 */
	public byte[] readAllBytesFromFile() throws IOException,OutOfMemoryError,SecurityException
	{
		return Files.readAllBytes(file.toPath());
	}
	public MappedByteBuffer mapAllBytesFromFile() throws IOException,OutOfMemoryError,SecurityException
	{
		FileInputStream stream=new FileInputStream(file);
		FileChannel fc = stream.getChannel();
		MappedByteBuffer mbb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
		
		fc.close();
		stream.close();
		return mbb;
	}
	
	/**
	 * Atomically creates a new, empty file named by this path if and only if a file with this name does not yet exist. 
	 * The check for the existence of the file and the creation of the file if it does not exist are a single operation that 
	 * is atomic with respect to all other filesystem activities that might affect the file.
	 *
	 * @return true if the named file does not exist and was successfully created; false if the named file already exists
	 * @throws IOException If an I/O error occurred
	 * @throws SecurityException If a security manager exists and its SecurityManager.checkWrite(java.lang.String) method denies write access to the file
	 */
	@Override
	public boolean createFile() {	
		file.getParentFile().mkdirs();
		if(!file.getParentFile().exists())
			logger.log("Some of these dirs failed to be created:"+file.getParent());
		try {
			return file.createNewFile();
		} catch (IOException e) {
			logger.log(e.toString()+"; File could not be created "+file);
		}
		return false;
	}
	
	/**
	 * Changes the key and the date modified and, if on cloud, updates all parallel files
	 * @param key the new key of the file
	 * @param dateModified the new dteModified date
	 */
	public void updateFile(long key,long dateModified)
	{
		setKey(key);
		setDateModified(dateModified);
	}
	public void updateKey(){
		setKey(key+1);//UPDATE KEY
	}
	//public void updateKey(){setKey(dateModified);}
	public void setKey(long key)
	{		
		logger.logTrace("key "+this.key+" is being changed to "+key+" path="+path);
		modifiedSinceLastKeyUpdate=false;
		this.key=key;
		
	}
	
	/**
	 * Returns the length of the file denoted by path.
	 */
	public long getSize(){return file.length();}
	
	@Override
	public long getKey()
	{
		return key;
	}
		
	/**
	 * Handles conflicts. The name of the file is renamed to file.getName()+".SYNCROPconflict"+i
	 * where is i is an integer that denotes the number of conflicts for this file<br/>
	 * 
	 * After every call, {@link daemon.SyncropCloud#updateAllClients(SyncROPFile, String, String[], String)}
	 * should be called to update other Clients
	 * @throws IOException
	 */
	public void makeConflict() throws IOException
	{
		logger.log("Conflict occurred path="+path);
		String baseName=file.getAbsolutePath()+CONFLICT_ENDING;
		
		int conflictNumber;
		try {
			conflictNumber = getConflictNumber();
		} catch (ConflictException e) {
			return;
		}
		
		rename(new File(baseName+conflictNumber));
	}
	private int getConflictNumber() throws ConflictException
	{
		String baseName=file.getAbsolutePath()+CONFLICT_ENDING;
		int i=1;
		for(;;i++)
		{
			if(!(new File(baseName+i)).exists())
				break;
		}
		return i;
	}
	public boolean shouldMakeConflict(long dateMod,long key,boolean modifiedSinceLastUpdate,long fileSize,String targetOfLink){
		if(!exists())return false;
				
		if(targetOfLink!=null&&this instanceof SyncROPSymbolicLink)
			return !targetOfLink.equals(((SyncROPSymbolicLink)this).getTargetPath());
		if(targetOfLink!=null&&!(this instanceof SyncROPSymbolicLink)||targetOfLink==null&&(this instanceof SyncROPSymbolicLink))
			return true;
			
		if(isDiffrentVersionsOfSameFile(key, modifiedSinceLastUpdate)){
			logger.logDebug("diffrent versions of same file");
			return false;
		}
		//if(Math.abs(dateMod-this.dateModified)>1000||getSize()!=fileSize)return true;
				
		logger.logDebug("Conflict avoided because file metadata is the same");
		return true;
	}
	@Override
	public boolean isEmpty(){return true;}
	public boolean updateSize(){
		long size=file.length();
		if(size!=lastRecordedSize){
			lastRecordedSize=file.length();
			setHasBeenUpdated();
			return true;
		}
		else return false;
	}
	public long getLastKnownSize(){
		return lastRecordedSize;
	}
}