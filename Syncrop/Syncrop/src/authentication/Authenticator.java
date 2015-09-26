package authentication;

import static syncrop.Syncrop.logger;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import settings.Settings;

public class Authenticator {
	
	public static boolean authenticateUser(String username,String email,String refreshToken){
		logger.logTrace("Authenticating User");
		String file="syncrop/authenticateUser.php";
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
	
	public static String getAuthenticationToken(String username,String password) throws IOException{
		String urlParameters  = "username="+username+"&password="+password;
		byte[] postData       = urlParameters.getBytes( StandardCharsets.UTF_8 );
		int    postDataLength = postData.length;
		String request        = Settings.getHost()+"/"+"getRefreshToken.php";
		URL    url            = new URL( request );
		HttpURLConnection conn= (HttpURLConnection) url.openConnection();           
		conn.setDoOutput( true );
		conn.setInstanceFollowRedirects( false );
		conn.setRequestMethod( "POST" );
		conn.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded"); 
		conn.setRequestProperty( "charset", "utf-8");
		conn.setRequestProperty( "Content-Length", Integer.toString( postDataLength ));
		conn.setUseCaches( false );
		try( DataOutputStream wr = new DataOutputStream( conn.getOutputStream())) {
		   wr.write(postData);
		   wr.close();
		}
		BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
        String response= in.readLine();
        return response;
	}

}
