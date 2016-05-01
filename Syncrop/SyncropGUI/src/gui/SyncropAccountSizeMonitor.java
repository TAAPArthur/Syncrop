package gui;

import syncrop.ResourceManager;

public class SyncropAccountSizeMonitor extends Thread{
	
	
	public SyncropAccountSizeMonitor(){
		super("Syncrop Account Size Monitor");
	}
	@Override
	public void run(){
		setPriority(Thread.MIN_PRIORITY);
		while(!SyncropGUI.isShuttingDown()){
			ResourceManager.getAccount().calculateSize();
			SyncropGUI.update();
			try {Thread.sleep(1000*60);} catch (InterruptedException e) {}
		}	
	}
}
