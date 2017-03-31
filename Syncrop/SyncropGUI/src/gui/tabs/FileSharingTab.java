package gui.tabs;

import gui.components.FileSharingFrame;
import gui.components.SyncropPanel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import daemon.cloud.filesharing.SharedFile;

import syncrop.ResourceManager;

public class FileSharingTab  extends JPanel implements SyncropTab,ActionListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	SyncropPanel mySharedFiles=new SyncropPanel("My Shared Files",true);
	
	SyncropPanel filesSharedWithMe=new SyncropPanel("Files Shared With Me",true);
	
	JButton share=new JButton("Share File");
	public FileSharingTab(){
		super();
		
		initilize();
		setName("File Sharing");	
	}
	void initilize(){
		JTabbedPane pane= new JTabbedPane();
		
		pane.add(mySharedFiles.getName(), mySharedFiles);
		pane.add(filesSharedWithMe.getName(), filesSharedWithMe);
				
		add(pane);
		share.addActionListener(this);
		add(share);
		reload();
	}
	@Override
	public void actionPerformed(ActionEvent e) {
		if(e.getSource().equals(share))
			new FileSharingFrame();
		
	}

	
	@Override
	public void reload() {
		for(SharedFile file:ResourceManager.sharedFiles){
			mySharedFiles.addLine(file.getPath());
			//todo deteced shared files; wuery cloud
			//filesSharedWithMe.addLines(file.get)
		}		
	}

}
