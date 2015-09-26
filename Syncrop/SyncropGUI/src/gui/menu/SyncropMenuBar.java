package gui.menu;

import java.awt.FlowLayout;

import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

public class SyncropMenuBar extends JMenuBar{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	JMenuItem file=new JMenuItem("File");
	JMenuItem options=new JMenuItem("Options");
	public SyncropMenuBar(){
		super();
		setLayout(new FlowLayout(FlowLayout.LEFT));;
		
		add(file);
		add(options);
	}

}
