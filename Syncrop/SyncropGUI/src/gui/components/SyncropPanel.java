package gui.components;

import gui.SyncropGUI;

import java.awt.Color;
import java.awt.Dimension;
import java.util.Collection;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

public class SyncropPanel extends JPanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	SyncropTextBox textBox;
	public SyncropPanel(){
		super();
		textBox=new SyncropTextBox();
	}
	
	public SyncropPanel(String title,boolean validateInput){
		super();
		//setLayout(new GridLayout(3, 1));
		setName(title);
		JLabel label=new JLabel(title);
		
		label.setPreferredSize(new Dimension(getWidth(),100));
		//label.setDisplayedMnemonic(title.charAt(0));
		add(label);
		label.setBackground(Color.BLUE);
		textBox=new SyncropTextBox(validateInput,true);
		textBox.setSize(getWidth(), 10);
		label.setLabelFor(textBox);
		JScrollPane scrollPane=new JScrollPane(textBox);
		
		scrollPane.setPreferredSize(new Dimension(SyncropGUI.WIDTH,100));
		add(scrollPane);
	}
	public void addLine(String line){
		if(textBox.getText().isEmpty())
			textBox.setText(line);
		else textBox.setText(textBox.getText()+"\n"+line);
	}
	public String[] getLines(){
		return textBox.getText().split("\n");
	}
	public void setLines(Collection<?>collections){
		if(collections.isEmpty())return;
		String s="";
		for(Object c:collections){
			if(!c.toString().isEmpty())
				s+="\n"+c.toString();
		}
		textBox.setText(s.substring(1));
	}
}