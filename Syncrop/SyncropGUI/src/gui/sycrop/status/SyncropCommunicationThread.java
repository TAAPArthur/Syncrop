package gui.sycrop.status;

import static daemon.SyncropCommunication.STATUS;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.util.NoSuchElementException;
import java.util.Scanner;

import gui.SyncropGUI;
import interfaces.Updateable;
import settings.Settings;
import syncrop.Syncrop;

public class SyncropCommunicationThread extends Thread{
	final int NOT_RUNNING=0;
	final int RUNNING=1;
	private boolean running=false;
	private String status;
	
	Updateable updateable;
	volatile Socket socket;
	PrintWriter out;
	Scanner in;
	public SyncropCommunicationThread(Updateable updateable) throws IOException{
		super("Syncrop Status Monitor");
		this.updateable=updateable;
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
	public void print(String s){
		out.println(s);
		out.flush();
	}
	
	public void run(){
		while(!SyncropGUI.isShuttingDown())
			try {
				if(socket==null)
					connect();
				else if(socket.isBound())
					if(socket.isClosed()){
						setStatus(false,null);
						connect();
					}
					else {
						out.println(STATUS);
						out.flush();
						setStatus(true, in.nextLine());
					}
				else System.out.println("Socket not bound");
				
				
				Thread.sleep(5000);
			} 
			catch (NoSuchElementException|InterruptedException e){}
			catch (ConnectException e){}
			catch (IOException e) {
				Syncrop.logger.logError(e);
			}
	}
	
	private void setStatus(boolean running,String status){
		this.running=running;
		this.status=status;
		this.updateable.update();
	}
	public boolean isSyncropRunning() {return running;}
	public String getStatus() {return status;}
	
}
