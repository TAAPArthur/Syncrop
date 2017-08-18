package syncrop;

import static syncrop.Syncrop.logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
	public static Connection getConnectionInstance(boolean readOnly) throws SQLException{
		logger.log("Connecting to: "+"jdbc:"+Settings.getDatabasePath());
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
		String query="SELECT * FROM "+TABLE_NAME+" "+
				(owner!=null&&Syncrop.isInstanceOfCloud()?"WHERE Owner='Owner'":"")
				+"ORDER BY Path DESC;";
		try {
			Connection conn = getConnectionInstance(true);
			Statement statement = conn.createStatement();

			ResultSet rs = statement.executeQuery(query);
			
			LinkedList<SyncropItem>items=new LinkedList<>();
			while (rs.next())
				items.add(getFile(rs));
			
			
			statement.close();
			conn.close();
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
			Connection conn = getConnectionInstance(true);
			PreparedStatement preparedStatement=conn.prepareStatement(query);
			preparedStatement.setString(1, relativePath+"%");
			if(Syncrop.isInstanceOfCloud())
				preparedStatement.setString(2, owner);
			
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
