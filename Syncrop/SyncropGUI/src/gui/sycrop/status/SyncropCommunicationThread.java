package gui.sycrop.status;

import static daemon.SyncropLocalServer.STATE_OFFLINE;

import java.io.IOException;
import java.net.ConnectException;

import daemon.SyncropLocalClient;
import daemon.SyncropLocalServer;
import gui.SyncropGUI;
import syncrop.ResourceManager;
import syncrop.Syncrop;

public class SyncropCommunicationThread extends SyncropLocalClient {
	
	private String status;
	
	public SyncropCommunicationThread() throws IOException{
		super("Syncrop Status Monitor");	
	}
		
	public void requestFileSharing(String absPath,boolean sharePublic){
		
	}
	
	public void run(){
		setPriority(Thread.MIN_PRIORITY);
		int count=0;
		while(!SyncropGUI.isShuttingDown())
			try {
				if(isConnected()) {
					setStatus(getStatus());
					if(count%120==0) 
						SyncropGUI.updateAccountSize(getAccountSize());
				}
				else
					connect();
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
		return !status.equals(SyncropLocalServer.STATE_OFFLINE)&&
				!status.equals(SyncropLocalServer.STATE_INITIALIZING)&&
				!status.equals(SyncropLocalServer.STATE_DISCONNECTED);
	}
	private void setStatus(String status){
		System.out.println(status);
		if(status.equals(this.status))return;
		this.status=status;
		SyncropGUI.updateStatus(status);
	}
	
}
