package gui.components;

import java.awt.Dimension;

import javax.swing.JTextPane;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;

public class SyncropTextBox extends JTextPane {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public SyncropTextBox(boolean validateInput, boolean allowEnter){
		super();
		((AbstractDocument)getStyledDocument()).setDocumentFilter(new SyncropDocumentFilter(validateInput,allowEnter));
		setAutoscrolls(true);
		
		this.setSize(new Dimension(100, 100));
	}
	public SyncropTextBox(){
		this(true,true);
	}
	public SyncropTextBox(String text){
		this();
		setText(text);
	}
	public SyncropTextBox(String text,boolean validateInput,boolean allowEnter){
		this(validateInput,allowEnter);
		setText(text);
	}
		
	public void addDocumentListener(DocumentListener listener){
		getStyledDocument().addDocumentListener(listener);
	}
}