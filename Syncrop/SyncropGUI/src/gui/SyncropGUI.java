package gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import account.Account;
import gui.menu.SyncropMenuBar;
import gui.sycrop.status.SyncropCommunicationThread;
import gui.tabs.AccountTab;
import gui.tabs.FilesTab;
import gui.tabs.Optimization;
import gui.tabs.SettingsTab;
import gui.tabs.SyncropTab;
import interfaces.Updateable;
import syncrop.ResourceManager;
import syncrop.Syncrop;

public class SyncropGUI extends Syncrop implements Updateable,ActionListener{
	JFrame frame;
	JTabbedPane tabbedPane= new JTabbedPane();
	JLabel spaceLeftInAccount=new JLabel();
		
	
	SyncropTab tabs[]={new AccountTab(this),new FilesTab(this),new SettingsTab(),new Optimization(this)};
	
	JPanel headerPanel=new JPanel();
	
	final int ACCOUNT=1;
	final int Settings=2;
	
	static boolean shuttingDown;
	
	JPanel fileSyncStatusPanel=new JPanel();
	SyncropCommunicationThread syncropCommunicationThread=new SyncropCommunicationThread(this);
	
	
	
	JButton startSyncropClient=new JButton("Start Syncrop");
	JButton stopSyncropClient=new JButton("Stop Syncrop");
	
	public static final int WIDTH=430;
	public static final int HEIGHT=520;
			
	
	public static void main (String args[]) throws IOException
	{
		//handles parameters
		String instance="";
		if(args.length>0)
			for(String s:args)
				if(s.startsWith("-i"))
					instance=s.substring(2).trim();
		new SyncropGUI(instance);
	}
	public static void stop(){
		System.exit(0);
	}
	public SyncropGUI() throws IOException{
		this("");
	}
	public SyncropGUI(String instance) throws IOException{
		super(instance);
		logger.logTrace("Starting GUI");
		frame=new JFrame("Syncrop"+instance);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		frame.setVisible(true);
		frame.setSize(WIDTH, HEIGHT);
		frame.setJMenuBar(new SyncropMenuBar());
		//frame.getContentPane().setLayout(new BorderLayout());
		syncropCommunicationThread.start();
		loadHeaderPanel();
		
		startSyncropClient.addActionListener(this);
		stopSyncropClient.addActionListener(this);
		for(SyncropTab tab:tabs)
			tabbedPane.add(tab.getName(), (Component) tab);
		
			
		//frame.add(new FileTree(ResourceManager.getAccount()));
		
		frame.add(tabbedPane,BorderLayout.CENTER);
		frame.revalidate();
		new SyncropAccountSizeMonitor(this).start();
	}
	
	public void loadHeaderPanel(){
		Account account=ResourceManager.getAccount();
		headerPanel.removeAll();
		if(account==null||account.getName().isEmpty()){
			headerPanel.add(new JLabel("Please signin"));
		}
		else {
			headerPanel.setLayout(new GridBagLayout());
			GridBagConstraints c=new GridBagConstraints();
			
			JLabel greeting=new JLabel("<html>Welcome<br/>"+account.getName()+"</html>");
			
			
			c.fill = GridBagConstraints.HORIZONTAL;
			c.gridx=0;
			c.weightx = 0.5;
			headerPanel.add(greeting,c);
			c.gridx = 1;
			headerPanel.add(spaceLeftInAccount,c);
			c.gridx = 2;
			update();
			headerPanel.add(fileSyncStatusPanel,c);
		}
		headerPanel.revalidate();
		frame.add(headerPanel, BorderLayout.NORTH);
	}
	
	
	/**
	 * The ClI is shutting down if shutDown is called; This method is called to let
	 * the various threads know to stop so that the SYNCROP can safly shut down
	 * @return true if the SyncropDaemon is shutting down; false otherwise
	 */
	public static boolean isShuttingDown(){return shuttingDown;}
	/**
	 * creates the shutdown hook that will safely kill Syncrop when asked to shutdown or
	 * when an unexpected error occurred.
	 */
	protected void addShutdownHook()
	{
		logger.logTrace("Creating shutdown hook");
		Runtime.getRuntime().addShutdownHook(
				new Thread("Shutdown-thread") {
	        public void run() 
	        {
        		shutdown();
	        }
	        
		});
	}

	public void reload(){
		for(SyncropTab tab:tabs)
			tab.reload();
	}
	
	@Override
	public void update() {
		
		double size=ResourceManager.getAccount().getRecordedSize();
		double maxSize=Account.getMaximumAccountSize();
		int percentUsed=(int)(size/maxSize*100);

		String spaceUsed=
				size>GIGABYTE?
						String.format("%.2fGiB",size/GIGABYTE):
						String.format("%.2fMiB",size/MEGABYTE);
		spaceLeftInAccount.setText(
				"<html>"+percentUsed+"% used;<br/>"+
						spaceUsed+" of "+maxSize/GIGABYTE+" GiB</html>");
				
		fileSyncStatusPanel.removeAll();
		
		if(syncropCommunicationThread.isSyncropRunning()){
			fileSyncStatusPanel.add(new JLabel(syncropCommunicationThread.getStatus()));
			fileSyncStatusPanel.add(stopSyncropClient);
		}
		else fileSyncStatusPanel.add(startSyncropClient);
		fileSyncStatusPanel.revalidate();
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		try {
			if(e.getSource().equals(startSyncropClient)){
				Runtime.getRuntime().exec("syncrop-daemon start");
				logger.log("Tried to start Syncrop");
			}
			else if(e.getSource().equals(stopSyncropClient)){
				Runtime.getRuntime().exec("syncrop-daemon stop");
			}
		} catch (IOException e1) {
			logger.logError(e1);
		}
	}
	public SyncropCommunicationThread getSyncropCommunicationThread(){
		return syncropCommunicationThread;
	}
}