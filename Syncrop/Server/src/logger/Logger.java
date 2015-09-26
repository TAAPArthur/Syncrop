package logger;

public interface Logger {
	
	/**
	 * Logs all events-- Provides the most detail and leads to clutter in logs.
	 * Use when testing a specific problem and {@link #LOG_LEVEL_TRACE} does not 
	 * provide enough info
	 */
	public static final int LOG_LEVEL_ALL  = 0;
	/** "Trace" level logging--
     * more detailed information. Expect these to be written to logs only. 
     * */
    public static final int LOG_LEVEL_TRACE  = 1;
    /** "Debug" level logging.
     * detailed information on the flow through the system. 
     * These are only written to logs. 
     * */
    public static final int LOG_LEVEL_DEBUG  = 2;
    /** "Info" level logging--
     * Interesting runtime events (startup/shutdown).
     * Except them to be visible on a console
     * */
    public static final int LOG_LEVEL_INFO   = 3;
    /** "Warn" level logging--
     * other runtime situations that are undesirable or 
     * unexpected but don't significantly interfere with program
     * Except them to be visible on a console. 
     * */
    public static final int LOG_LEVEL_WARN   = 4;
    /** "Error" level logging.
     *  Other runtime errors or unexpected conditions.
     *	Except the user to be explicitly notified  
     *  
     */
    public static final int LOG_LEVEL_ERROR  = 5;
    /** "Fatal" level logging--
     * Severe errors that cause premature termination.
     * Except the user to be explicitly notified. 
     */
    public static final int LOG_LEVEL_FATAL  = 6;
    
    /** Enable no logging levels */
    public static final int LOG_LEVEL_OFF    = 7;

    /**
     * Logs a message with {@link #LOG_LEVEL_INFO}
     * @param s -the message to log
     * @see #log(String, int) 
     * @see #LOG_LEVEL_INFO
     */
	public void log(String s);
	/**
	 * Logs an error message with {@link #LOG_LEVEL_ERROR}. 
	 * @param t the Error/Exception to log
	 */
	public void logError(Throwable t);
	/**
	 * Logs an error message with {@link #LOG_LEVEL_ERROR}. 
	 * @param t -the Error/Exception to log
	 * @param s -a description of the error
	 */
	public void logError(Throwable t, String s);
	/**
	 *Logs the specified message 
	 * @param message -error message
	 * @param logLevel -logLevel
	 */
	public void log(String message,int logLevel);
}
