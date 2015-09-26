package logger;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class GenericLogger implements Logger{

	static private PrintWriter out;
	private String logName="/syncrop/server.log";
	public GenericLogger() throws IOException {
		File f=new File(logName);
		if(!f.exists())f.createNewFile();
		out=new PrintWriter(f);
	}
	public GenericLogger(OutputStream out) {
		GenericLogger.out=new PrintWriter(out);
	}
	@Override
	public void log(String message) {
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Calendar cal = Calendar.getInstance();
		out.println(dateFormat.format(cal.getTime())+": "+message);
		out.flush();
		
	}

	@Override
	public void logError(Throwable e) {
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Calendar cal = Calendar.getInstance();
		out.println(dateFormat.format(cal.getTime())+": ");
		e.printStackTrace(out);
		out.flush();
		
	}

	@Override
	public void logError(Throwable t, String s) {
		log(s);
		logError(t);
	}
	@Override
	public void log(String message, int logLevel) {
		log(message);
	}
}