package gui.tabs;

import gui.components.SettingsField;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileNotFoundException;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextPane;

import settings.SettingsManager;
import settings.SettingsManager.Options;
import syncrop.Syncrop;

public class SettingsTab extends JPanel implements SyncropTab,ActionListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	JButton submitChanges=new JButton("Apply");
	
	JTextPane pane=new JTextPane();
	ArrayList<SettingsField>fields=new ArrayList<SettingsField>();
	JButton save=new JButton("Save");
	
	public SettingsTab(){
		initilize();
		setName("Settings");
	}
		
	void initilize(){
		setLayout(new GridLayout(0,1));
		for(Options option:Options.values()){
			SettingsField field=new SettingsField(option);
			fields.add(field);
			add(field);
		}
		add(save);
		save.addActionListener(this);
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		try {
			for(SettingsField field:fields)
				field.save();
			new SettingsManager().saveSettings();
		} catch (FileNotFoundException e1) {
			Syncrop.logger.logError(e1);
		}
	}

	@Override
	public void reload() {
		// TODO Auto-generated method stub
		
	}

}
