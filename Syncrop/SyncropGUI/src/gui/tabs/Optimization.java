package gui.tabs;

import static syncrop.Syncrop.logger;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;

import gui.SyncropGUI;
import listener.FileWatcher;
import listener.actions.RemoveSyncropConflictsAction;

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
			try {
				new FileWatcher(null).checkAllFiles(new RemoveSyncropConflictsAction());
			} catch (IOException e1) {
				logger.logError(e1);
			}
		else if(e.getSource().equals(removeDisabledFilesOnCloud))
			SyncropGUI.getSyncropCommunicationThread().clean();
		
	}


	@Override
	public void reload() {
		removeDisabledFilesOnCloud.setEnabled(SyncropGUI.getSyncropCommunicationThread().isSyncropRunning());
	}
}