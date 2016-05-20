package gui.sycrop.status;

import static daemon.client.SyncropCommunication.STATUS;
import gui.SyncropGUI;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.util.NoSuchElementException;
import java.util.Scanner;

import settings.Settings;
import syncrop.Syncrop;
import daemon.client.SyncropCommunication;

public class SyncropCommunicationThread extends Thread{
	final int NOT_RUNNING=0;
	final int RUNNING=1;
	private boolean running=false;
	private String status;
	
	
	volatile Socket socket;
	PrintWriter out;
	Scanner in;
	public SyncropCommunicationThread() throws IOException{
		super("Syncrop Status Monitor");
		
		connect();
	}
	private void connect() throws IOException{
		try {
			socket = new Socket((String)null, Settings.getPort());
			out = new PrintWriter(socket.getOutputStream());
			out.flush();
			in = new Scanner(socket.getInputStream());
		} catch (ConnectException e) {}
	}
	public void close() throws IOException{
		in.close();
		socket.close();
	}
	
	public boolean isConnected(){
		return socket!=null&&!socket.isClosed();
	}
	private synchronized void print(String... args){
		for(String s:args)
			out.println(s);
		out.flush();
	}
	public void clean(){
		print(SyncropCommunication.CLEAN);
	}
	public void share(String path){
		SyncropGUI.logger.log("Sharing public file:"+path);
		print(SyncropCommunication.SHARE,path);
	}
	
	public void requestFileSharing(String absPath,boolean sharePublic){
		
	}
	public void run(){
		setPriority(Thread.MIN_PRIORITY);
		while(!SyncropGUI.isShuttingDown())
			try {
				if(socket==null)
					connect();
				else if(socket.isBound()){
					if(in.hasNextLine())
						switch(in.nextLine()){
							case SyncropCommunication.UPDATE:SyncropGUI.update();
						}
					if(socket.isClosed()){
						setStatus(false,null);
						connect();
					}
					else {
						out.println(STATUS);
						out.flush();
						setStatus(true, in.nextLine());
					}
				}
				else System.out.println("Socket not bound");		
			} 
			catch (NoSuchElementException e){}
			catch (ConnectException e){}
			catch (IOException e) {
				Syncrop.logger.logError(e);
			}
			finally{
				if(!SyncropGUI.isShuttingDown())SyncropGUI.update();
				try {Thread.sleep(10000);} catch (InterruptedException e) {}
			}
	}
	
	private void setStatus(boolean running,String status){
		if(this.running==running&&this.status.equals(status))
			return;
		this.running=running;
		this.status=status;
		SyncropGUI.update();
	}
	public boolean isSyncropRunning() {return running;}
	public String getStatus() {return status;}
	
}
