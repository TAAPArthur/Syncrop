package syncrop;

import static syncrop.Syncrop.logger;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedList;

import file.SyncropItem;
import settings.Settings;

public class FileMetadataManager {
	static final String TABLE_NAME= "FileInfo";
	
	private FileMetadataManager(){}
	
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
	public static synchronized Connection getConnectionInstance(boolean readOnly) throws SQLException{
		Connection conn = DriverManager.getConnection("jdbc:"+Settings.getDatabasePath(),Settings.getDatabaseUsername(),Settings.getDatabasePassword());
		conn.setReadOnly(readOnly);
		return conn;
		
	}
	
	
	
	public static void recreateDatabase() throws SQLException{
		deleteDatabase();
		createDatabase();
	}
	private static void deleteDatabase() throws SQLException{
		
		Connection conn = getConnectionInstance(false);
		Statement stat = conn.createStatement();
		stat.executeUpdate("DROP TABLE IF EXISTS "+TABLE_NAME);
		conn.close();
	
	}
	static void createDatabase() throws SQLException{
		Connection conn = getConnectionInstance(false);
		Statement stat = conn.createStatement();
		
        stat.executeUpdate("CREATE TABLE IF NOT EXISTS "+TABLE_NAME+
        		" ( Path Varchar (255) ,Owner Varchar(25), DateModified INT UNSIGNED, "
        		+ "`SyncropKey` INT UNSIGNED, ModifiedSinceLastKeyUpdate Boolean, "
        		+ "LastRecordedSize INT UNSIGNED,FilePermissions SMALLINT,"
        		+ "`FileExists` Boolean, `LinkTarget` Varchar (255) NULL,"
        		+ " PRIMARY KEY (Path, Owner));");
        stat.close();
        
        conn.close();
	
	}
	public static boolean deleteFileMetadata(SyncropItem item){
		return deleteFileMetadata(item.getPath(), item.getOwner());
	}
	private static boolean deleteFileMetadata(String path,String owner){
		try {
			Connection conn = getConnectionInstance(false);
			PreparedStatement prep = conn.prepareStatement(
			        "DELETE FROM "+TABLE_NAME+" WHERE Path=? AND Owner=? ;");
			prep.setString(1,path);
			prep.setString(2, owner);
			prep.addBatch();
			prep.executeBatch();
			prep.close();
			conn.close();
			return true;
		} catch (SQLException e) {
			logger.logError(e, "could not delete info on "+owner+"'s file "+path);
		}
		return false;
	}
	public static synchronized boolean updateFileMetadata(SyncropItem item){
		try {					
			Connection conn = getConnectionInstance(false);
			PreparedStatement prep = conn.prepareStatement(
		            "REPLACE INTO "+TABLE_NAME+" (`Path`, `Owner`, `DateModified`, `SyncropKey`,"
		            		+ " `ModifiedSinceLastKeyUpdate`, `LastRecordedSize`, "
		            		+ "`FilePermissions`, `FileExists`, `LinkTarget`) "
		            		+ "VALUES (?,?,?,?,?,?,?,?,?);");
			prep.setString(1, item.getPath());
			prep.setString(2, item.getOwner());
			prep.setLong(3, item.getDateModified()/1000);
			prep.setLong(4, item.getKey());
			prep.setBoolean(5, item.modifiedSinceLastKeyUpdate());
			prep.setLong(6, item.getSize());
			prep.setInt(7, item.getFilePermissions());
			prep.setBoolean(8, item.exists());
			prep.setString(9, item.getLinkTarget());
			prep.addBatch();
			prep.executeBatch();
			prep.close();
			conn.close();
			return true;
		} catch (SQLException e) {
			logger.logError(e, "occured while trying to Update File Metadata");
		}
		return false;
	}
	
	
	public static Iterable<SyncropItem> iterateThroughAllFileMetadata(String owner){
		return getFilesStartingWith(null,owner);
	}
	public static LinkedList<SyncropItem> getChildFilesOf(String relativePath,String owner) {
		return getFilesStartingWith(relativePath+File.separatorChar,owner);
	}
	public static LinkedList<SyncropItem> getFilesStartingWith(String relativePath,String owner) {
		ResultSet rs=null;
		String query="SELECT * FROM "+TABLE_NAME;
		ArrayList<String >params=new ArrayList<>();
		boolean isPathPresent=relativePath!=null;
		boolean isOwnerPresent=Syncrop.isInstanceOfCloud()&& owner!=null;
		if (isPathPresent || isOwnerPresent) {
			query+=" WHERE ";
			if (isPathPresent) {
				query+="Path LIKE ? ";
				params.add(relativePath+"%");
			}
			if(isOwnerPresent) {
				if (params.size()>0) 
					query+="AND ";
				query+="Owner=? ";
				params.add(owner);
			}
		}
		query+=" ORDER BY PATH DESC;";
	
		try {		
			Connection conn = getConnectionInstance(true);
			PreparedStatement preparedStatement=conn.prepareStatement(query);
			for(int i=0;i<params.size();i++)
				preparedStatement.setString(i+1, params.get(i));			
						
			rs = preparedStatement.executeQuery();
			
			LinkedList<SyncropItem>items=new LinkedList<>();
			
			while (rs.next())
				items.add(getFile(rs));
			
			preparedStatement.close();
			conn.close();
			return items;
		} catch (SQLException e) {
			logger.logError(e, "could not read from database; query="+query);
		}
        return null;
		
	}
	/**
	 * Gets SyncropItem instance stored in the database denoted by the parameters
	 * @param relativePath
	 * @param owner
	 * @return
	 */
	public static SyncropItem getFile(String relativePath,String owner) {
		try {
			Connection conn = getConnectionInstance(true);
			SyncropItem file = getFile(relativePath, owner, conn);
			conn.close();
			return file;
		} catch (SQLException e) {
			logger.logFatalError(e, "could not read from database;");
			System.exit(0);
		}
        return null;
			
	}
	public static SyncropItem getFile(String relativePath,String owner,Connection conn) throws SQLException {
		ResultSet rs=null;
		
		SyncropItem item=null;
		String query="SELECT * FROM "+TABLE_NAME
				+ " WHERE Path=? "
				+(Syncrop.isInstanceOfCloud()?"AND Owner=?":"")+";";
		PreparedStatement preparedStatement;
		try {
			preparedStatement=conn.prepareStatement(query);
			preparedStatement.setString(1, relativePath);
			if(Syncrop.isInstanceOfCloud())
				preparedStatement.setString(2, owner);
			rs = preparedStatement.executeQuery();
		}
		catch(SQLException e) {
			logger.log("Failed to execute query: "+query);
			logger.log("path:"+relativePath+" owner:"+owner);
			throw e;
		}
		
		if(rs.next())
			item= getFile(rs);
		preparedStatement.close();
		conn.close();
		return item;
		
	}
	
	private static SyncropItem getFile(ResultSet rs) throws SQLException{
		String path=rs.getString(1);
		String owner=rs.getString(2);
		long dateModified=rs.getLong(3)*1000;
		int key=rs.getInt(4);
		boolean modifedSinceLastKeyUpdate=rs.getBoolean(5);
		long lastRecordedSize=rs.getLong(6);
		int filePermissions=rs.getInt(7);
		boolean knownToExists=rs.getBoolean(8);
		String linkTarget=rs.getString(9);
		return SyncropItem.getInstance(path, owner, dateModified, key, modifedSinceLastKeyUpdate, lastRecordedSize, filePermissions, knownToExists, linkTarget);

	}

}
