package file;

import static syncrop.Syncrop.logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;

import syncrop.Syncrop;

public class SyncropFile extends SyncropItem 
{	
	
	/**
	 * the Key of the file; It is used to determine if a conflict should be made;
	 * If this key and the key of another file do not match then the older file will be made 
	 * into a conflict
	 */
	private int key;
	private long lastRecordedSize;
	
	public SyncropFile(String path,String owner){
		this(path, owner, 0);
	}
		
	public SyncropFile(String path, String owner,int key)
	{
		this(path,owner,0, key,false,-1,false,0);
	}
	
	
	public SyncropFile(String path,String owner,long modificicationDate,int key,boolean modifedSinceLastKeyUpdate,long lastRecordedSize,boolean deletionRecorded,int filePermisions)
	{
		super(path, owner, modificicationDate,modifedSinceLastKeyUpdate,lastRecordedSize,deletionRecorded,filePermisions);
		
		if(key<=0)
			key=0;
		this.key=key;
		if(getSize()!=lastRecordedSize)
			setHasBeenUpdated();
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
	
	
	public void updateKey(){
		setKey(key+1);//UPDATE KEY
	}
	public void setKey(int key)
	{		
		logger.logTrace("key "+this.key+" is being changed to "+key+" path="+path);
		modifiedSinceLastKeyUpdate=false;
		this.key=key;
		
	}
	
	
	@Override
	public long getKey()
	{
		return key;
	}
		
	/**
	 * Handles conflicts. The name of the file is renamed to file.getName()+".SYNCROPconflict"+i
	 * where is i is an integer that denotes the number of conflicts for this file<br/>
	 * 
	 * After every call, {@link daemon.cloud.SyncropCloud#updateAllClients(SyncropFile, String, String[], String)}
	 * should be called to update other Clients
	 * @throws IOException
	 */
	public void makeConflict() throws IOException
	{
		if(!exists())return;
		
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
	void mergeMetadata(long remoteDateMod,int remoteKey){
		setDateModified(remoteDateMod);
		if(Syncrop.isInstanceOfCloud())
			updateKey();
		else 
			setKey(remoteKey);
	}
}