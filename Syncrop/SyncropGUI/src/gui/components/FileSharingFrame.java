package gui.components;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.filechooser.FileFilter;

import syncrop.ResourceManager;
import file.SyncROPDir;
import file.SyncROPFile;
import file.SyncROPItem;
import gui.SyncropGUI;

public class FileSharingFrame extends JFrame implements ActionListener{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private JFileChooser fileChooser=new JFileChooser();
	private JCheckBox removable=new JCheckBox("Removable");
	private JTextArea userToShareWith=new JTextArea(0,20);
	
	public FileSharingFrame(){
		super("Share file");
		
		this.setVisible(true);
		setSize(600, 400);
		JPanel panel=new JPanel();
		
		panel.add(removable);
		panel.add(userToShareWith);
		userToShareWith.setVisible(true);
		fileChooser.setApproveButtonText("Share File");
		fileChooser.addActionListener(this);
		fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		fileChooser.setFileFilter(new FileFilter() {
			
			@Override
			public String getDescription() {
				return "Enabled files";
			}
			
			@Override
			public boolean accept(File f) {
				String path=ResourceManager.getRelativePath(f.getAbsolutePath(), 
						ResourceManager.getAccount().getName(), removable.isSelected());
				
				return SyncROPItem.isFileEnabled(f)&&
						SyncROPItem.isPathEnabled(path,ResourceManager.getAccount().getName());
				
			}
		});
		
		panel.add(fileChooser);
		add(panel);
		
	}
	@Override
	public void actionPerformed(ActionEvent e) {
		if(fileChooser.isMultiSelectionEnabled())
			for(File fileToShare:fileChooser.getSelectedFiles())
				shareFile(fileToShare);
		else shareFile(fileChooser.getSelectedFile());
	}
	private void shareFile(File fileToShare){
		String realitvePath=ResourceManager.getRelativePath(fileToShare.getAbsolutePath(), ResourceManager.getAccount().getName(), removable.isSelected());
		SyncROPItem file=
				fileToShare.isFile()?
					new SyncROPFile(realitvePath, ResourceManager.getAccount().getName()):
					new SyncROPDir(realitvePath, ResourceManager.getAccount().getName());
		if(file.isEnabled()){
				SyncropGUI.getSyncropCommunicationThread().share(realitvePath);
			dispose();
		}
	}
}