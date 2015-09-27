package gui;

import syncrop.ResourceManager;

public class SyncropAccountSizeMonitor extends Thread{
	SyncropGUI gui;
	
	public SyncropAccountSizeMonitor(SyncropGUI gui){
		super("Syncrop Account Size Monitor");
		this.gui=gui;
	}
	@Override
	public void run(){
		setPriority(Thread.MIN_PRIORITY);
		while(!SyncropGUI.isShuttingDown()){
			ResourceManager.getAccount().calculateSize();
			gui.update();
			try {Thread.sleep(1000*60);} catch (InterruptedException e) {}
		}	
	}
}
