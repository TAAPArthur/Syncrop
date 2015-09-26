package gui.tabs;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import account.Account;
import gui.SyncropGUI;
import gui.components.SyncropPanel;
import gui.components.SyncropTextBox;
import syncrop.ResourceManager;
import syncrop.Syncrop;

public class AccountTab extends JPanel implements SyncropTab,ActionListener{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	Account account=null;
	
	
	SyncropTextBox user=new SyncropTextBox(true,false);
	SyncropTextBox email=new SyncropTextBox(true,false);
	SyncropTextBox refreshToken=new SyncropTextBox(true,false);
	
	SyncropPanel directoryPanel=new SyncropPanel("Directories",true);
	SyncropPanel removableDirectoryPanel=new SyncropPanel("Removable Directories",true);
	SyncropPanel restirctionsPanel=new SyncropPanel("Restrictions",false);
	
	JButton apply=new JButton("Apply");
	JButton revert=new JButton("Revert");
	JButton save=new JButton("Save");
	
	JButton addNewRegularFolder=new JButton("Add new folder");
	JButton addNewRemovableFolder=new JButton("Add new folder");
	JButton addNewRestrictionFolder=new JButton("Add new folder");
	
	SyncropGUI gui;
	
	
	public AccountTab(SyncropGUI parent){
		super();
		gui=parent;
		initilize();
		setName("Account");
		
	}
	
	void initilize(){
		
		
		
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		
		add(createPanel("User :",user));
		add(createPanel("Email:",email));
		add(createPanel("Token:",refreshToken));
		
		addNewRegularFolder.addActionListener(this);
		addNewRemovableFolder.addActionListener(this);
		addNewRestrictionFolder.addActionListener(this);
		
		directoryPanel.add(addNewRegularFolder);
		removableDirectoryPanel.add(addNewRemovableFolder);
		restirctionsPanel.add(addNewRestrictionFolder);
		
		JTabbedPane directoryPane= new JTabbedPane();
		directoryPane.add("Directories", directoryPanel);
		directoryPane.add("Removable Directories", removableDirectoryPanel);
		directoryPane.add("Restrictions ", restirctionsPanel);
		
		add(directoryPane);
	
		apply.addActionListener(this);
		revert.addActionListener(this);
		save.addActionListener(this);
		JPanel layout=new JPanel();
		layout.add("", revert);
		layout.add("", apply);
		layout.add("",save);
		add(layout);
		
		reload();
	}
	
	JPanel createPanel(String labelText,SyncropTextBox c){
		JPanel panel=new JPanel();
		
		panel.setLayout(new FlowLayout(FlowLayout.LEFT));
		JLabel label=new JLabel(labelText);
		label.setDisplayedMnemonic(labelText.charAt(0));
		panel.add(label);
		label.setLabelFor(c);
		panel.add(c);
		return panel;
	}
	
	public void reload(){
		account=ResourceManager.getAccount();
		
		if(account!=null){
			user.setText(account.getName());
			email.setText(account.getEmail());
			refreshToken.setText(account.getToken());
			
			directoryPanel.setLines(account.getDirectories());
			restirctionsPanel.setLines(account.getRestrictions());
			removableDirectoryPanel.setLines(account.getRemovableDirectories());
		}
		else{
			account=new Account();
			ResourceManager.addAccount(account);
		}
	}

	void updateAccount(){
		Syncrop.logger.logDebug("Updating account");
		if(user.getText().isEmpty()||email.getText().isEmpty()||refreshToken.getText().isEmpty()){
			Syncrop.logger.logDebug("Canceling updating account");
			return;
		}
		String accountName=account.getName();
		account.setName(user.getText());
		account.setEmail(email.getText());
		account.setRefreshToken(refreshToken.getText());
		
		account.clear();
		account.addDirs(directoryPanel.getLines());
		account.addRemoveableDirs(removableDirectoryPanel.getLines());
		account.addRestrictions(restirctionsPanel.getLines());
		if(accountName.isEmpty()){
			gui.loadHeaderPanel();
			//ResourceManager.generateID();
		}
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		Object source=e.getSource();
		if(source.equals(apply)){
			updateAccount();
		}
		else if(source.equals(save)){
			try {
				updateAccount();
				ResourceManager.writeConfigFile();
				ResourceManager.readFromConfigFile();
				gui.reload();
			} catch (IOException e1) {
				Syncrop.logger.logError(e1);
			}
		}
		else if(source.equals(revert)){
			reload();
		}
		else if(source.equals(addNewRegularFolder)){
			String home=ResourceManager.getHome(account.getName(), false);
			JFileChooser chooser=new JFileChooser(home);
			chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
			if(chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION){
				File choosenFile=chooser.getSelectedFile();
				String relativePath=ResourceManager.getRelativePath(choosenFile.getAbsolutePath(),account.getName(),false);
				directoryPanel.addLine(relativePath);
			}
		}
		else if(source.equals(addNewRemovableFolder)){
			String home=ResourceManager.getHome(account.getName(), true);
			JFileChooser chooser=new JFileChooser(home);
			chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
			if(chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION){
				File choosenFile=chooser.getSelectedFile();
				
				String relativePath=ResourceManager.getRelativePath(choosenFile.getAbsolutePath(),account.getName(),true);
				removableDirectoryPanel.addLine(relativePath);
			}
		}
	}
}