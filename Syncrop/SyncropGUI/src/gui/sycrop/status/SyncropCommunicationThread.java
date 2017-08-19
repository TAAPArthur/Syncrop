package gui.sycrop.status;

import static daemon.client.SyncropCommunication.STATE_OFFLINE;
import static daemon.client.SyncropCommunication.STATUS;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;

import daemon.client.SyncropCommunication;
import gui.SyncropGUI;
import settings.Settings;
import syncrop.ResourceManager;
import syncrop.Syncrop;

public class SyncropCommunicationThread extends Thread{
	
	private String status;
	
	
	volatile Socket socket;
	DataOutputStream out;
	DataInputStream in=null;
	public SyncropCommunicationThread() throws IOException{
		super("Syncrop Status Monitor");
		
	}
	private void connect() throws IOException{		
		socket = new Socket((String)null, Settings.getSyncropCommunicationPort());
		out = new DataOutputStream(socket.getOutputStream());
		out.flush();
		in = new DataInputStream(socket.getInputStream());
	}
	public void close() throws IOException{
		in.close();
		socket.close();
	}
	
	public boolean isConnected(){
		return socket!=null&&!socket.isClosed();
	}
	private synchronized boolean print(int... args){
		boolean success = true;
		for(int s:args) {
			try {
				System.out.println(s);
				out.writeInt(s);
				out.flush();
				if(s==SyncropCommunication.GET_ACCOUNT_SIZE)
					SyncropGUI.updateAccountSize(in.readLong());
				else if(s== STATUS)
					setStatus(in.readUTF());
			} catch (IOException e) {
				success = false;
			}
		}
		return success;
	}
	public void clean(){
		print(SyncropCommunication.CLEAN);
	}
	
	
	public void requestFileSharing(String absPath,boolean sharePublic){
		
	}
	public void run(){
		setPriority(Thread.MIN_PRIORITY);
		int count=0;
		while(!SyncropGUI.isShuttingDown())
			try {
				if(socket==null)
					connect();
				else if(socket.isBound()){
					print(STATUS);
					if(count%120==0) {
						print(SyncropCommunication.GET_ACCOUNT_SIZE);
					}
				}
			} 
			catch (ConnectException e) {
				if(count%120==0) {
					calculateAccountSize();
					System.out.println("manually updated account size");
				}
				setStatus(STATE_OFFLINE);
			}
			catch (IOException e) {
				Syncrop.logger.logError(e);
			}
			finally{
				try {Thread.sleep(5000);} catch (InterruptedException e) {}
				count++;
			}
	}
	void calculateAccountSize() {
		new Thread() {
			public void run() {
				System.out.println("calcuating");
				ResourceManager.getAccount().calculateSize();
				System.out.println(ResourceManager.getAccount().getRecordedSize());
				SyncropGUI.updateAccountSize(ResourceManager.getAccount().getRecordedSize());
			}
		}.start();
		
	}
	public boolean isSyncropRunning() {
		return !status.equals(SyncropCommunication.STATE_OFFLINE)&&
				!status.equals(SyncropCommunication.STATE_INITIALIZING)&&
				!status.equals(SyncropCommunication.STATE_DISCONNECTED);
	}
	private void setStatus(String status){
		System.out.println(status);
		if(status.equals(this.status))return;
		this.status=status;
		SyncropGUI.updateStatus(status);
	}
	
}
