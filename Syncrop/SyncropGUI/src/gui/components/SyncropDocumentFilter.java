package gui.components;

import java.awt.Toolkit;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

import file.SyncropItem;

public class SyncropDocumentFilter extends DocumentFilter {
	
	boolean allowWhiteSpace=true;
	boolean validateInput=true;
	public SyncropDocumentFilter() {}
	public SyncropDocumentFilter(boolean validateInput,boolean allowWhileSpace) {
		this.allowWhiteSpace=allowWhileSpace;
		this.validateInput=validateInput;
	}
	
	@Override
	public void insertString(FilterBypass fb, int offs,String str, AttributeSet a)throws BadLocationException {
		str=formatInput(str);
		if(isValidInput(str))
			super.insertString(fb, offs, str, a);
		else
			Toolkit.getDefaultToolkit().beep();
	}
	@Override
	public void replace(FilterBypass fb, int offset,int length,String str, AttributeSet a)throws BadLocationException {
		str=formatInput(str);
		if(isValidInput(str))
			super.replace(fb, offset,length, str, a);
		else
			Toolkit.getDefaultToolkit().beep();
	}
	boolean isValidInput(String str){
		return !validateInput||SyncropItem.isValidFileName(str);
	}
	String formatInput(String str){
		if(!allowWhiteSpace)
			str=str.replaceAll("\\s", "");
		else str=str.replaceAll("\t", "");
		return str;
	}
}