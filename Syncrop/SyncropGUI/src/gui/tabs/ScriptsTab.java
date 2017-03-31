package gui.tabs;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JPanel;
//TODO implement
public class ScriptsTab extends JPanel implements SyncropTab,ActionListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public ScriptsTab(){
		super();
		
		initilize();
		setName("Scripts");	
	}
	
	void initilize(){
		
	}
	@Override
	public void actionPerformed(ActionEvent arg0) {
	}

	@Override
	public void reload() {
	}


}
