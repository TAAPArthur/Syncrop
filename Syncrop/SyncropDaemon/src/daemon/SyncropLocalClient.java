package daemon;

import static daemon.SyncropLocalServer.*;
import static daemon.SyncropLocalServer.GET_ACCOUNT_SIZE;
import static daemon.SyncropLocalServer.SHUTDOWN;
import static daemon.SyncropLocalServer.STATUS;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import settings.Settings;

public class SyncropLocalClient extends Thread{

	Socket socket=null;
	DataOutputStream out;
	DataInputStream in=null;
	public SyncropLocalClient() {
		super("");
	}
	public SyncropLocalClient(String string) {
		super(string);
	}
	public boolean isConnected(){
		return socket!=null&&!socket.isClosed();
	}
	public void connect() throws IOException{		
		socket = new Socket((String)null, Settings.getSyncropCommunicationPort());
		out = new DataOutputStream(socket.getOutputStream());
		out.flush();
		in = new DataInputStream(socket.getInputStream());		
	}
	public void close() throws IOException{
		in.close();
		socket.close();
	}
	public long getAccountSize() throws IOException{
		write(GET_ACCOUNT_SIZE);
		return in.readLong();
	}
	public String getStatus() throws IOException{
		write(STATUS);
		return in.readUTF();
	}
	public void shutDown() throws IOException{
		write(SHUTDOWN);
	}
	public void clean() throws IOException{
		write(CLEAN);
	}
	public void sync(boolean force) throws IOException{
		if(force)
			write(FORCE_SYNC);
		else 
			write(SYNC);
	}
	
	private synchronized void write(int request) throws IOException {
		out.writeInt(request);
		out.flush();
	}
}
