package server;

import java.io.FileNotFoundException;
import java.io.IOException;

import logger.GenericLogger;
import logger.Logger;



/**
 * 
 * @author taaparthur
 *
 */
public class ExternalServer extends Server
{
	public static void main (String args[]) throws IOException{
		new ExternalServer(new GenericLogger());
	}
	public ExternalServer() {
		super();
		addShutDownHook();
	}
	
	public ExternalServer(Logger logger)throws IOException  {
		super(new GenericLogger());
		addShutDownHook();
	}
	public ExternalServer(int maxConnections,int port,Logger logger){
		super(maxConnections,port,logger);
	}
	private void addShutDownHook()
	{
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
	        public void run() 
	        {
	        	log("Server is shutting down");
	    		try {
					close();
				} catch (IOException e) {
					logger.logError(e);
				}
	        }
	    }, "Shutdown-thread"));
	}
}