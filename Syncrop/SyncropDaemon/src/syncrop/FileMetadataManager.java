package syncrop;

import static syncrop.ResourceManager.getAbsolutePath;
import static syncrop.Syncrop.logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;

import file.SyncropDir;
import file.SyncropFile;
import file.SyncropItem;
import file.SyncropSymbolicLink;
import settings.Settings;

public class FileMetadataManager {
	static final String TABLE_NAME= "FileInfo";
	
	private FileMetadataManager(){}
	static Connection conn;
	/*
	private static Connection getNewReadOnlyConnectionInstance() throws SQLException{
		return getNewConnectionInstance(true);
	}
	private static Connection getNewConnectionInstance(boolean readonly) throws SQLException{
		Properties config = new Properties();
		config.setProperty(Pragma.JOURNAL_MODE.pragmaName, JournalMode.TRUNCATE.name());
		if(readonly)
			config.setProperty("open_mode", "1");
		return DriverManager.getConnection("jdbc:sqlite:"+getDatabasePath()+"?journal_mode=WAL",config);
	}
	*/
	public static void startConnectionSession() throws SQLException{				
		conn=DriverManager.getConnection("jdbc:sqlite:"+Settings.getDatabasePath()+"?journal_mode=WAL",Settings.getDatabaseUsername(),Settings.getDatabasePassword());
	}
	public static void endSession(){
		try {conn.close();} catch (SQLException e) {}
	}
	
	
	public static void recreateDatabase(){
		deleteDatabase();
		createDatabase();
	}
	private static void deleteDatabase(){
		try {
			//Connection conn=getNewReadOnlyConnectionInstance();
			Statement stat = conn.createStatement();
			stat.executeUpdate("DROP TABLE IF EXISTS "+TABLE_NAME);
			//conn.close();
		} catch (SQLException e) {
			logger.logFatalError(e, "could not delete table: "+TABLE_NAME);
			System.exit(0);
		}
	}
	static void createDatabase(){
		try {
			//Connection conn=getNewReadOnlyConnectionInstance();
			Statement stat = conn.createStatement();
			
	        stat.executeUpdate("CREATE TABLE IF NOT EXISTS "+TABLE_NAME+
	        		" (Path String PRIMARY KEY,Owner String, DateModified Long, "
	        		+ "Key Long, ModifiedSinceLastKeyUpdate Boolean, LastRecordedSize Long,"
	        		+ "FilePermissions String) ;");
	        stat.close();
	        
	        //conn.close();
		} catch (SQLException e) {
			logger.logFatalError(e, "could not create table: "+TABLE_NAME);
			System.exit(0);
		}
	}
	static boolean deleteFileMetadata(SyncropItem item){
		return deleteFileMetadata(item.getPath(), item.getOwner());
	}
	static boolean deleteFileMetadata(String path,String owner){
		try {
			//Connection conn=getNewReadOnlyConnectionInstance();
			PreparedStatement prep = conn.prepareStatement(
			        "DELETE FROM "+TABLE_NAME+" WHERE Path=? AND Owner=? ;");
			prep.setString(1,path);
			prep.setString(2, owner);
			prep.addBatch();
			prep.executeBatch();
			prep.close();
			//conn.close();
			return true;
		} catch (SQLException e) {
			logger.logError(e, "could not delete info on "+owner+"'s file "+path);
		}
		return false;
	}
	static synchronized boolean updateFileMetadata(SyncropItem item){
		try {					
			//Connection conn=getNewConnectionInstance(false);
			PreparedStatement prep = conn.prepareStatement(
		            "REPLACE into "+TABLE_NAME+" values (?,?,?,?,?,?,?);");
			prep.setString(1, item.getPath());
			prep.setString(2, item.getOwner());
			prep.setLong(3, item.getDateModified());
			prep.setLong(4, item.getKey());
			prep.setBoolean(5, item.modifiedSinceLastKeyUpdate());
			prep.setLong(6, item.getSize());
			prep.setString(7, item.getFilePermissions());
			prep.addBatch();
			prep.executeBatch();
			prep.close();
			//conn.close();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			//logger.logError(e, "occured while trying to Update File Metadata");
		}
		return false;
	}
	
	
	public static Iterable<SyncropItem> iterateThroughAllFileMetadata(String owner){
		String query="SELECT * FROM "+TABLE_NAME+" "+
				(owner!=null&&Syncrop.isInstanceOfCloud()?"WHERE Owner='Owner'":"")
				+"ORDER BY Path DESC;";
		try {
			//Connection conn=getNewReadOnlyConnectionInstance();
			Statement statement = conn.createStatement();

			ResultSet rs = statement.executeQuery(query);
			
			LinkedList<SyncropItem>items=new LinkedList<>();
			int count=0;
			while (rs.next()){
				if(count++%100==0)Syncrop.sleepShort();
				items.add(getFile(rs));
			}
			
			statement.close();
			//conn.close();
			return items;
		} catch (SQLException e) {
			logger.logError(e, "could not query database; query="+query);
		}
		return null;
	}
	public static LinkedList<SyncropItem> getFilesStartingWith(String relativePath,String owner) {
		ResultSet rs=null;
		String query="SELECT * FROM "+TABLE_NAME
				+ " WHERE Path LIKE ? "
				+(Syncrop.isInstanceOfCloud()?"AND Owner=?":"")+
				"ORDER BY PATH DESC;";
		try {
			//Connection conn=getNewReadOnlyConnectionInstance();
			PreparedStatement preparedStatement=conn.prepareStatement(query);
			preparedStatement.setString(1, relativePath+"%");
			if(Syncrop.isInstanceOfCloud())
				preparedStatement.setString(2, owner);
			rs = preparedStatement.executeQuery();
			LinkedList<SyncropItem>items=new LinkedList<>();
			int count=0;
			while (rs.next()){
				if(count++%100==0)Syncrop.sleepShort();
				items.add(getFile(rs));
			}
			preparedStatement.close();
			//conn.close();
			return items;
		} catch (SQLException e) {
			logger.logError(e, "could not read from database; query="+query);
		}
        return null;
		
	}
	public static SyncropItem getFile(String relativePath,String owner) {
		ResultSet rs=null;
		
		SyncropItem item=null;
		String query="SELECT * FROM "+TABLE_NAME
				+ " WHERE Path=? "
				+(Syncrop.isInstanceOfCloud()?"AND Owner=?":"")+";";
		try {
			//conn=getNewReadOnlyConnectionInstance();
			PreparedStatement preparedStatement=conn.prepareStatement(query);
			preparedStatement.setString(1, relativePath);
			if(Syncrop.isInstanceOfCloud())
				preparedStatement.setString(2, owner);
			rs = preparedStatement.executeQuery();
			
			if(rs.next())
				item= getFile(rs);
			preparedStatement.close();
			//conn.close();
			return item;
		} catch (SQLException e) {
			logger.logFatalError(e, "could not read from database; query"+query);
			System.exit(0);
		}
        return null;
	}
	private static SyncropItem getFile(ResultSet rs) throws SQLException{
		String path=rs.getString(1);
		String owner=rs.getString(2);
		long dateModified=rs.getLong(3);
		long key=rs.getLong(4);
		boolean isDir=key==-1;
		boolean modifedSinceLastKeyUpdate=rs.getBoolean(5);
		long lastRecordedSize=rs.getLong(6);
		String filePermissions=	rs.getString(7);
		File f=new File(getAbsolutePath(path, owner));
		SyncropItem file=null;
		try {
			if(Files.isSymbolicLink(f.toPath())){
				try {
					file = SyncropSymbolicLink.getInstance(path,owner,dateModified,key,modifedSinceLastKeyUpdate,lastRecordedSize,filePermissions,f);
				} catch (IOException e) {logger.logError(e);}
			}
			else if(isDir)
				file = new SyncropDir(path, owner,dateModified,lastRecordedSize,filePermissions);
			else 
				file=new SyncropFile(path, owner,dateModified,key,modifedSinceLastKeyUpdate,lastRecordedSize,filePermissions);

			return file;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

}
