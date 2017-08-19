package gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
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
import daemon.SyncropLocalServer;
import gui.menu.SyncropMenuBar;
import gui.sycrop.status.SyncropCommunicationThread;
import gui.tabs.AccountTab;
import gui.tabs.FileSharingTab;
import gui.tabs.FilesTab;
import gui.tabs.Optimization;
import gui.tabs.ScriptsTab;
import gui.tabs.SettingsTab;
import gui.tabs.SyncropTab;
import settings.Settings;
import syncrop.ResourceManager;
import syncrop.Syncrop;

public class SyncropGUI extends Syncrop implements ActionListener{
	private static JFrame frame;
	
	private static JLabel spaceLeftInAccount=new JLabel();
		
	
	private static SyncropTab tabs[]=null;
	
	private static final JPanel headerPanel=new JPanel();
	
	private static final JPanel fileSyncStatusPanel=new JPanel();
	private static final  JLabel statusDescription =new JLabel();
	private static SyncropCommunicationThread syncropCommunicationThread;
		
	static final String START_SYNCROP="Start Syncrop";
	static final String STOP_SYNCROP="Stop Syncrop";
	private static final JButton syncropClientButton=new JButton();
	
	public static final int WIDTH=600;
	public static final int HEIGHT=600;
			
	
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
	
	public SyncropGUI() throws IOException{
		this("");
	}
	public SyncropGUI(String instance) throws IOException{
		this(instance,false);
	}
	public SyncropGUI(String instance,boolean runAsCloud) throws IOException{
		super(instance,runAsCloud);
		
		SyncropGUI.tabs=new SyncropTab[]{new AccountTab(),new FileSharingTab(),new FilesTab(),new ScriptsTab(),new SettingsTab(),new Optimization()};
		SyncropGUI.syncropCommunicationThread=new SyncropCommunicationThread();
		logger.logTrace("Starting GUI");
		frame=new JFrame("Syncrop"+instance+" Settings");
		frame.setResizable(true);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		frame.setVisible(true);
		frame.setSize(WIDTH, HEIGHT);
		frame.setJMenuBar(new SyncropMenuBar());
		//frame.getContentPane().setLayout(new BorderLayout());
		
		loadHeaderPanel();
		updateStatus(SyncropLocalServer.STATE_OFFLINE);
		syncropClientButton.addActionListener(this);
		JTabbedPane tabbedPane= new JTabbedPane();
		
		for(SyncropTab tab:tabs)
			tabbedPane.add(tab.getName(), (Component) tab);
		
		frame.setPreferredSize(new Dimension(WIDTH,HEIGHT));	
		//frame.add(new FileTree(ResourceManager.getAccount()));
		
		frame.add(tabbedPane,BorderLayout.CENTER);
		
		
		frame.revalidate();
		frame.pack();
		syncropCommunicationThread.start();
	}
	
	public static void loadHeaderPanel(){
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
			fileSyncStatusPanel.add(statusDescription);
			fileSyncStatusPanel.add(syncropClientButton);
			
			headerPanel.add(fileSyncStatusPanel,c);
		}
		headerPanel.revalidate();
		frame.add(headerPanel, BorderLayout.NORTH);
	}
	protected String getLogFileName(){
		return "syncropGUI.log";
	}
	

	public static void reload(){
		for(SyncropTab tab:tabs)
			tab.reload();
	}
	
	public static void updateStatus(String status) {
		System.out.println(status);;
		syncropClientButton.setText(
				SyncropLocalServer.STATE_OFFLINE.equals(status)?
						START_SYNCROP:STOP_SYNCROP
							);
	
		statusDescription.setText(status);
		fileSyncStatusPanel.revalidate();
	}
	public static void updateAccountSize(long size) {
		double maxSize=Settings.getMaxAccountSize();
		String percentUsed=String.format("%.2f",size/maxSize*100);

		
		String spaceUsed=
				size>GIGABYTE?
						String.format("%.2fGiB",((double)size)/GIGABYTE):
						String.format("%.2fMiB",((double)size)/MEGABYTE);
						
		spaceLeftInAccount.setText(
				"<html>"+percentUsed+"% used;<br/>"+
						spaceUsed+" of "+maxSize/GIGABYTE+" GiB</html>");
				
		
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		try {
			if(e.getSource().equals(syncropClientButton))
				if(syncropClientButton.getText().equals(START_SYNCROP)) {
					Runtime.getRuntime().exec("syncrop start");
					logger.log("Tried to start Syncrop");
				}
				else {
					Runtime.getRuntime().exec("syncrop stop");
					logger.log("Tried to stop Syncrop");
				}
				
		} catch (IOException e1) {
			logger.logError(e1);
			
		}
	}
	public static SyncropCommunicationThread getSyncropCommunicationThread(){
		return syncropCommunicationThread;
	}
	public static void shareFile(String absPath,boolean sharePublic){
		syncropCommunicationThread.requestFileSharing(absPath, sharePublic);
	}
}