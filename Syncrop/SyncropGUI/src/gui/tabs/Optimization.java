package gui.tabs;

import static syncrop.Syncrop.logger;
import file.Directory;
import file.RemovableDirectory;
import gui.SyncropGUI;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;

import listener.FileWatcher;
import listener.actions.RemoveSyncROPConflictsAction;
import listener.actions.SyncROPFileAction;
import syncrop.ResourceManager;
import syncrop.SyncropLogger;
import account.Account;
import daemon.client.SyncropClientDaemon;

public class Optimization extends JPanel implements SyncropTab,ActionListener{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	JButton removeFileInfo=new JButton("Remove metadata for disabled and deleted files");
	JButton removeConflicts=new JButton("Remove conflicts locally ");
	JButton removeDisabledFilesOnCloud=new JButton("Remove disabled files on Clound");
	
	public Optimization(){
		setName("Optimization");
		initialize();
	}
	private void initialize(){
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		JButton[] optimizations={removeFileInfo,removeConflicts,removeDisabledFilesOnCloud};
		
		for(JButton optimization:optimizations){
			add(optimization);
			optimization.addActionListener(this);
		}	
	}
	@Override
	public void actionPerformed(ActionEvent e) {
		if(e.getSource().equals(removeFileInfo))
			try {
				FileWatcher.checkMetadataForAllFiles(false);
			} catch (IOException e1) {
				logger.logError(e1);
			}
		else if(e.getSource().equals(removeConflicts))
			checkAllFiles(new RemoveSyncROPConflictsAction());
		else if(e.getSource().equals(removeDisabledFilesOnCloud))
			SyncropGUI.getSyncropCommunicationThread().clean();
		
	}

	public void checkAllFiles(SyncROPFileAction... fileActions){
		for (Account a : ResourceManager.getAllEnabledAccounts()){
			//checks regular files
			for (Directory parentDir : a.getDirectories())
				checkFiles(a, parentDir.isLiteral()?parentDir.getDir():"",false,fileActions);
			//checks removable files
			for (RemovableDirectory parentDir : a.getRemovableDirectories())
				if(parentDir.exists())
					checkFiles(a, parentDir.getDir(),true,fileActions);
		}
	
	}
	
	void checkFiles(Account a,final String path,boolean removable,SyncROPFileAction... fileActions){
		if(SyncropClientDaemon.isShuttingDown())
			return;
		
		if(!a.isPathEnabled(path))return;
		
		File file=new File(ResourceManager.getHome(a.getName(),removable),path);
		
		if(fileActions!=null){
			for(SyncROPFileAction fileAction:fileActions)
				fileAction.performOn(file);
		}
		logger.log("Checking "+path,SyncropLogger.LOG_LEVEL_ALL);
		if(!Files.isSymbolicLink(file.toPath())&&file.isDirectory()){
				for(String f:file.list())
					checkFiles(a,path+((path.isEmpty()&&!removable)
						||path.equals(File.separator)?
						"":
							File.separator)+f,removable, fileActions);
		}
	}
	@Override
	public void reload() {
		removeDisabledFilesOnCloud.setEnabled(SyncropGUI.getSyncropCommunicationThread().isSyncropRunning());
	}
}