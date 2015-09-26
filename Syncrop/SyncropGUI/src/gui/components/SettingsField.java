package gui.components;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import settings.SettingsManager.Options;

public class SettingsField extends JPanel{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	JComponent component;
	Options option;
	
	public SettingsField(Options option){
		this.option=option;
		if(option.getType().equals(double.class)||option.getType().equals(int.class)){
			component=new JSpinner();
			if(option.getType().equals(double.class))
			((JSpinner)component).setModel(new SpinnerNumberModel(0, 0, 10.0, .1));
			
			add(new JLabel(option.getTitle()));
		}
		else if(option.getType().equals(boolean.class))
			component=new JRadioButton(option.getTitle());
		else component=new JTextField();
		add(component);
		load();
	}
	public void load(){
		Object value=option.getValue();
		if(option.getType().equals(double.class)||option.getType().equals(int.class)){
			((JSpinner)component).setValue(value);
		}
		else if(option.getType().equals(boolean.class))
			((JRadioButton)component).setSelected((boolean) value);
		else ((JTextField)component).setText((String) value);
	}
	public void save(){
		Object value;
		if(option.getType().equals(double.class)||option.getType().equals(int.class)){
			value=((JSpinner)component).getValue();
		}
		else if(option.getType().equals(boolean.class))
			value=((JRadioButton)component).isSelected();
		else value=((JTextField)component).getText();
		option.setValue(value);
	}
	
}
