package syncrop;

import static syncrop.Syncrop.logger;
import static syncrop.ResourceManager.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;

import file.SyncROPDir;
import file.SyncROPFile;
import file.SyncROPItem;
import file.SyncROPSymbolicLink;

public class FileMetadataManager {
	static final String TABLE_NAME= "FileInfo";
	
	private FileMetadataManager(){}

	private static Connection getNewConnectionInstance() throws SQLException{
		return DriverManager.getConnection("jdbc:sqlite:"+ResourceManager.getConfigFilesHome()+File.separator+"metadata.db");
	}
	static void deleteDatabase(){
		try {
			Connection conn=getNewConnectionInstance();
			Statement stat = conn.createStatement();
			stat.executeUpdate("DROP TABLE IF EXISTS "+TABLE_NAME);
			conn.close();
		} catch (SQLException e) {
			logger.logFatalError(e, "could not delete table: "+TABLE_NAME);
			System.exit(0);
		}
	}
	public static void createDatabase() throws SQLException{
		Connection conn=getNewConnectionInstance();
		
		Statement stat = conn.createStatement();
        stat.executeUpdate("CREATE TABLE IF NOT EXISTS "+TABLE_NAME+
        		" (Path String PRIMARY KEY,Owner String PRIMARY KEY, DateModified Long, "
        		+ "Key Long, ModifiedSinceLastKeyUpdate Boolean, LastRecordedSize Long,"
        		+ "FilePermissions String) ;");
        
        conn.close();
	}
	static boolean deleteFileMetadata(SyncROPItem item){
		return deleteFileMetadata(item.getPath(), item.getOwner());
	}
	static boolean deleteFileMetadata(String path,String owner){
		try {
			Connection conn=getNewConnectionInstance();
			PreparedStatement prep = conn.prepareStatement(
			        "DELETE FROM "+TABLE_NAME+" WHERE Path=? AND Owner=?);");
			prep.setString(1,path);
			prep.setString(2, owner);
			prep.addBatch();
			prep.executeBatch();
			conn.close();
			return true;
		} catch (SQLException e) {
			logger.logError(e, "could not delete info on "+owner+"'s file "+path);
		}
		return false;
	}
	static boolean updateFileMetadata(SyncROPItem item){
		try {					
			Connection conn=getNewConnectionInstance();
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
			conn.close();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			//logger.logError(e, "occured while trying to Update File Metadata");
		}
		return false;
	}
	
	
	public static Iterable<SyncROPItem> iterateThroughAllFileMetadata(String owner){
		try {
			Connection conn=getNewConnectionInstance();
			Statement statement = conn.createStatement();

			ResultSet rs = statement.executeQuery("SELECT * FROM "+TABLE_NAME+" "+
					(owner!=null&&Syncrop.isInstanceOfCloud()?"WHERE Owner='Owner'":"")
					+"ORDER BY Path DESC;");
			
			LinkedList<SyncROPItem>items=new LinkedList<>();
			int count=0;
			while (rs.next()){
				if(count++%100==0)Syncrop.sleepShort();
				items.add(getFile(rs));
			}
			
			rs.close();
			conn.close();
			return items;
		} catch (SQLException e) {
			logger.logError(e, "could not query database");
		}
		return null;
	}
	public static LinkedList<SyncROPItem> getFilesStartingWith(String relativePath,String owner) {
		ResultSet rs=null;
		try {
			Connection conn=getNewConnectionInstance();
			PreparedStatement preparedStatement=conn.prepareStatement("SELECT * FROM "+TABLE_NAME
					+ " WHERE Path LIKE ? "
					+(owner!=null&&Syncrop.isInstanceOfCloud()?"AND Owner="+owner:"")+";");
			preparedStatement.setString(1, relativePath+"%");
			
			rs = preparedStatement.executeQuery();
			LinkedList<SyncROPItem>items=new LinkedList<>();
			int count=0;
			while (rs.next()){
				if(count++%100==0)Syncrop.sleepShort();
				items.add(getFile(rs));
			}
			rs.close();
			conn.close();
			return items;
		} catch (SQLException e) {
			logger.logError(e, "could not read from database");
		}
        return null;
		
	}
	public static SyncROPItem getFile(String relativePath,String owner) {
		ResultSet rs=null;
		Connection conn=null;
		SyncROPItem item=null;
		try {
			conn=getNewConnectionInstance();
			PreparedStatement preparedStatement=conn.prepareStatement("SELECT * FROM "+TABLE_NAME
					+ " WHERE Path=? "
					+(owner!=null&&Syncrop.isInstanceOfCloud()?"AND Owner="+owner:"")+";");
			preparedStatement.setString(1, relativePath);
						
			rs = preparedStatement.executeQuery();
			
			if(rs.next())
				item= getFile(rs);
			rs.close();
			conn.close();
			return item;
		} catch (SQLException e) {
			logger.logFatalError(e, "could not read from database");
			System.exit(0);
		}
        return null;
		
	}
	private static SyncROPItem getFile(ResultSet rs) throws SQLException{
		String path=rs.getString(1);
		String owner=rs.getString(2);
		long dateModified=rs.getLong(3);
		long key=rs.getLong(4);
		boolean isDir=key==-1;
		boolean modifedSinceLastKeyUpdate=rs.getBoolean(5);
		long lastRecordedSize=rs.getLong(6);
		String filePermissions=	rs.getString(7);
		File f=new File(getAbsolutePath(path, owner));
		SyncROPItem file=null;
		try {
			if(Files.isSymbolicLink(f.toPath())){
				String target=null;
				try {
					Path targetPath=Files.readSymbolicLink(f.toPath());
					String targetOfLink = targetPath.toString().replace(
							ResourceManager.getHome(owner, isFileRemovable(path)), "");
					//if(getAccount(owner).isPathEnabled(target))
						target=targetOfLink;
				} catch (IOException e) {logger.logError(e);}
			
				file = new SyncROPSymbolicLink(path,owner,dateModified,key,modifedSinceLastKeyUpdate,target,lastRecordedSize,filePermissions);
			}
			else if(isDir)
				file = new SyncROPDir(path, owner,dateModified,lastRecordedSize,filePermissions);
			else 
				file=new SyncROPFile(path, owner,dateModified,key,modifedSinceLastKeyUpdate,lastRecordedSize,filePermissions);

			return file;
		} catch (IllegalArgumentException e) {
			return null;
		}
	
	}
}
