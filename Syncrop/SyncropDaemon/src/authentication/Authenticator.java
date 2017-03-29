package authentication;

import static syncrop.Syncrop.logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import settings.Settings;

/**
 * 
 * Provides methods to authenticate the User
 *
 */
public class Authenticator {
	
	/**
	 * Queries the server to authenticate the user.
	 * @param username the name of the user to authenticate
	 * @param email the email of the user to authenticate
	 * @param refreshToken the token of the user
	 * @return true if the user has been authenticated
	 */
	public static boolean authenticateUser(String username,String email,String refreshToken){
		logger.logTrace("Authenticating User:"+username);
		String file="Syncrop/authenticateUser.php";
		String parameters="?username="+username+"&email="+email+"&token="+refreshToken;
		String protocal="http://";
		try {
	        // Create a URL for the desired page
	        URL url = new URL(protocal+Settings.getHost()+"/"+file+parameters);
	        logger.logTrace("Connecting to "+protocal+Settings.getHost());

	        // Read all the text returned by the server
	        BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
	        String response= in.readLine();
	        logger.logTrace("Authenticating response: "+response);
	        if(response==null)return false;
	        if(response.equals("1"))return true;

	        in.close();
	    } catch (IOException e) {
	    	logger.logError(e, "occured while trying to authenticate "+username);
	    }
		return false;	
	}
}