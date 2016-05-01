package syncrop;

import static notification.Notification.displayNotification;
import static syncrop.ResourceManager.getConfigFilesHome;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

import daemon.SyncropClientDaemon;

public class SyncropLogger implements logger.Logger{
    
    int logLevel=LOG_LEVEL_INFO;
    
    FileHandler handler;
	
	/**
	 * Keeps a record of various information for debugging purposes
	 */
	static File logFile;
	
	/**
	 * Handles the date format for logged messages
	 */
	private SimpleDateFormat dateTimeFormat=new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
	
	/**
	 * Constructs a Logger with log level {@link #LOG_LEVEL_INFO} and
	 * logFile whose name is "syncrop.log"
	 * @throws IOException if the logWriter cannot be created
	 * @see #initializeLogWriter()
	 */
	public SyncropLogger() throws IOException {
		this(LOG_LEVEL_INFO,"syncrop.log");
	}
	/**
	 * Constructs a Logger with log level {@link #LOG_LEVEL_INFO} and
	 * logFile whose name is designated by logFileName 
	 * @param logFileName -the name of the logFile
	 * @throws IOException if the logWriter cannot be created
	 * @see #initializeLogWriter()
	 */
	public SyncropLogger(String logFileName) throws IOException {
		this(LOG_LEVEL_INFO,logFileName);
	}
	/**
	 * Constructs a Logger with log level designated by level and
	 * logFile whose name is designated by logFileName
	 * @param level -the log Level of this Logger 
	 * @param logFileName -the name of the logFile
	 * @throws IOException if the logWriter cannot be created
	 * @see #initializeLogWriter()
	 * @see #setLogLevel
	 */
	public SyncropLogger(int level,String logFileName) throws IOException {
		setLogLevel(level);
		logFile=new File(getConfigFilesHome(),logFileName);
		
		if(!logFile.exists()){
			logFile.getParentFile().mkdirs();
			logFile.createNewFile();
		}
		handler=new FileHandler(getConfigFilesHome()+"/"+logFileName, false);
		handler.setLevel(Level.ALL);
		handler.setFormatter(new SimpleFormatter(){
			public String format(LogRecord  record){
				StringBuffer sb=new StringBuffer(record.getMessage());
				StringWriter sw =new StringWriter();
				if(record.getThrown()!=null){
					PrintWriter pw = new PrintWriter(sw);
					record.getThrown().printStackTrace(pw);
					 pw.close();
					sb.append(sw.toString());
				}
				return sb.toString()+"\n";
			}
		});
	}
	
	
	/**
	 * Sets the log level.
	 * @param level -the new log level
	 */
	public void setLogLevel(int level){
		logLevel=level;
	}
	/**
	 * 
	 * @param logLevel the log level to be checked
	 * @return true if and only if logLevel is greater than or equal to the current log level
	 */
	public boolean isLogging(int logLevel){
		return logLevel>=this.logLevel;
	}
	/**
	 * 
	 * @return true if and only if debugging messages can be logged
	 * @see #LOG_LEVEL_DEBUG
	 */
	public boolean isDebugging(){
		return isLogging(LOG_LEVEL_DEBUG);
	}
	
	/**
	 * 
	 * @param description a brief description of what was caused the error;
	 * For example "trying to create a file"
	 * @param t - the error
	 */
	public void logError(Throwable t,String description){
		log(t.toString()+" occured when "+description,LOG_LEVEL_ERROR,t);
	}
	/**
	 * 
	 * @param description a brief description of what was caused the error;
	 * For example "trying to create a file"
	 * @param t - the error
	 */
	public void logFatalError(Throwable t,String description){
		log(t.toString()+" occured when "+description,LOG_LEVEL_FATAL,t);
	}
	
	public void log(String message){
		log(message,LOG_LEVEL_INFO,null);
	}
	/**
     * Logs a message with {@link #LOG_LEVEL_WARN}
     * @param s -the message to log
     * @see #log(String, int) 
     * @see #LOG_LEVEL_WARN
     */
	public void logWarning(String message){
		log(message,LOG_LEVEL_WARN,null);
	}
	/**
     * Logs a message with {@link #LOG_LEVEL_DEBUG}
     * @param s -the message to log
     * @see #log(String, int) 
     * @see #LOG_LEVEL_DEBUG
     */
	public void logDebug(String message){
		log(message,LOG_LEVEL_DEBUG,null);
	}
	/**
     * Logs a message with {@link #LOG_LEVEL_TRACE}
     * @param s -the message to log
     * @see #log(String, int) 
     * @see #LOG_LEVEL_TRACE
     */
	public void logTrace(String message){
		log(message,LOG_LEVEL_TRACE,null);
	}
	
	public void log(String message,int logLevel){
		log(message,logLevel,null);
	}
	public synchronized boolean log(String message,int logLevel,Throwable t)
	{
		if(!isLogging(logLevel))return false;
		try {
			if(!logFile.exists())logFile.createNewFile();
		} catch (IOException e) {
			SyncropClientDaemon.closeConnection(
					"log file does not exists and cannot be created", true);
			return false;
			}
		
		
		
        String dateText= dateTimeFormat.format(new Date());
        
        /*
         * switch(type) {
            case SimpleLog.LOG_LEVEL_TRACE: buf.append("[TRACE] "); break;
            case SimpleLog.LOG_LEVEL_DEBUG: buf.append("[DEBUG] "); break;
            case SimpleLog.LOG_LEVEL_INFO:  buf.append("[INFO] ");  break;
            case SimpleLog.LOG_LEVEL_WARN:  buf.append("[WARN] ");  break;
            case SimpleLog.LOG_LEVEL_ERROR: buf.append("[ERROR] "); break;
            case SimpleLog.LOG_LEVEL_FATAL: buf.append("[FATAL] "); break;
        }
         */

        String logSting=dateText+": "+
        		(isLogging(LOG_LEVEL_TRACE)?"("+Thread.currentThread().getName()+") ":"")
        		+(t==null?"":t.toString())+message;
        
        LogRecord record=new LogRecord(Level.parse(logLevel+""), logSting);
        
        record.setThrown(t);
        
        handler.publish(record);
        if(t!=null){
        	t.printStackTrace(System.out);
        }
        
		if(logLevel==LOG_LEVEL_FATAL){
			displayNotification("An unexpected error occurred see log for details");
		}
		return true;
	}
	
	/**
	 * Closes the stream and releases any system resources associated with it. Closing a previously closed stream has no effect.
	 *  Once closed, the stream cannot be reopened.
	 */
	public void close(){
		handler.flush();
		handler.close();
	}
	
	@Override
	public void logError(Throwable t) {
		logError(t, "");		
	}
	
	public SimpleDateFormat getDateTimeFormat(){return dateTimeFormat;}
	public int getLogLevel(){return logLevel;}
}