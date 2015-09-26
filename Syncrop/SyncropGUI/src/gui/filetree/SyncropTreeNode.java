package gui.filetree;

import java.io.File;

import javax.swing.tree.DefaultMutableTreeNode;

public class SyncropTreeNode extends DefaultMutableTreeNode{
	private static final long serialVersionUID = 1L;
	File file;
	public SyncropTreeNode(File file) {
		super(file.getName());
		this.file=file;
	}
	public void setFile(File file){
		this.file=file;
		setUserObject(file.getName());
	}
	public File getFile(){return file;}
	public boolean equals(Object o){
		return o instanceof SyncropTreeNode&&((SyncropTreeNode)o).getFile().equals(file);
			
	}
}