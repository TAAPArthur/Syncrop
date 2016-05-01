package gui.tabs;

import encryption.SyncropCipher;
import gui.SyncropGUI;
import gui.filetree.FileTree;

import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.IOException;

import javax.crypto.BadPaddingException;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.tree.TreePath;

import settings.Settings;
import syncrop.ResourceManager;
import syncrop.Syncrop;
//TODO fix reload
public class FilesTab extends JScrollPane implements SyncropTab,MouseListener{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	final FileTree tree=getFileTree();
	JPanel panel=new JPanel();
	FileOptionMenu menu;
	public FilesTab(){
		super();
		
		menu=new FileOptionMenu(tree);
		setViewportView(panel);
		
		panel.setLayout(new GridLayout(0, 1));
		

		panel.add(tree);
		setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		
		setName("Files");
		tree.addMouseListener(this);
		tree.setDragEnabled(true);
		
		
	}
	private static FileTree getFileTree(){
		return new FileTree(ResourceManager.getAccount());
	}
	@Override
	public void reload() {
		tree.refresh();
	}
	@Override
	public void mouseClicked(MouseEvent e) {}
	@Override
	public void mouseEntered(MouseEvent e) {}
	@Override
	public void mouseExited(MouseEvent e) {}
	@Override
	public void mousePressed(MouseEvent e) {
		if(e.getButton()>2)
			menu.show(tree, e.getX(), e.getY());
	}
	@Override
	public void mouseReleased(MouseEvent e) {}
}
class FileOptionMenu extends JPopupMenu implements ActionListener{

	private static final long serialVersionUID = 1L;
	
	JMenuItem open=new JMenuItem("Open");
	JMenuItem delete=new JMenuItem("Delete");
	JMenuItem newFile=new JMenuItem("New File");
	JMenuItem newDirectory=new JMenuItem("New Directory");
	JMenuItem sharePublicly=new JMenuItem("Share Public");
	JMenuItem sharePrivately=new JMenuItem("Share Private");
	JMenuItem encrypt=new JMenuItem("Encrypt");
	JMenuItem decrypt=new JMenuItem("Decrypt");
	JMenuItem search=new JMenuItem("Search");
	JMenuItem refresh=new JMenuItem("Refresh");
	JMenuItem collapseAll=new JMenuItem("Collapse All");
	
	FileTree tree;
	FileOptionMenu(FileTree tree){
		super();
		
		JMenuItem options[]={open, delete,newFile,newDirectory,sharePrivately,sharePublicly,encrypt,decrypt,search,refresh,collapseAll};
		for(JMenuItem option:options){
			add(option);
			option.addActionListener(this);
		}
		this.tree=tree;
	}
	public void show(Component c,int x,int y){
		super.show(c, x, y);
		encrypt.setEnabled(Settings.getAllowEncription());
		decrypt.setEnabled(Settings.getAllowEncription());
		open.setEnabled(tree.getSelectionPath()!=null);
		delete.setEnabled(tree.getSelectionPath()!=null);
	}
	@Override
	public void actionPerformed(ActionEvent e) {
		try {
			if(e.getSource().equals(open))
				open();
			else if(e.getSource().equals(delete))
				tree.deleteSelectedNodes();
			else if(e.getSource().equals(newFile))
				tree.createNode("New File", false);
			else if(e.getSource().equals(newDirectory))
				tree.createNode("New Directory", true);
			else if(e.getSource().equals(sharePrivately))
				SyncropGUI.shareFile(FileTree.getFilePath(tree.getSelectionPaths()[0].getPath()),true);
			else if(e.getSource().equals(sharePublicly))
				SyncropGUI.shareFile(FileTree.getFilePath(tree.getSelectionPaths()[0].getPath()),false);
			else if(e.getSource().equals(search))
				tree.search();
			else if(e.getSource().equals(collapseAll))
				tree.collapseAllChildNodes();
			else if(e.getSource().equals(encrypt)||e.getSource().equals(decrypt)){
				if(tree.getSelectedNode()!=null){
					String key=getKey();
					for(TreePath path:tree.getSelectionPaths()){
						File file=new File(FileTree.getFilePath(path.getPath()));
						try {
							if(e.getSource().equals(encrypt))
								SyncropCipher.encrypt(file,key);
							else 
								SyncropCipher.decrypt(file,key);
							refresh();
						} catch (BadPaddingException e1) {
							JOptionPane.showMessageDialog(null, "Invalid password");
						}
						catch(IllegalArgumentException e1){
							JOptionPane.showMessageDialog(null, "File is too large to encrypt");
						}
					}
				}
			}
			else if(e.getSource().equals(refresh))
				refresh();
			
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	private void refresh(){
		tree.refresh();
	}
	private String getKey(){
		String keyString=JOptionPane.showInputDialog("Enter key");
		if(keyString.length()<16)
			keyString = String.format("%0"+(16-keyString.length())+"d%s", 0, keyString);
		else keyString=keyString.substring(0,16);
		return keyString;
	}
	void open(){
		new Thread(){
			public void run(){
				try {
					if(Syncrop.isNotWindows()&&Syncrop.isNotMac()){
						File file=tree.getSelectedNode().getFile();
						String key=null;
						File fileToBeOpened=file;
						try {
							if(SyncropCipher.isEncrypted(file.getName())){
								File tempFile=ResourceManager.createTemporaryFile(file);
								fileToBeOpened=tempFile;
								SyncropCipher.decrypt(file,fileToBeOpened , key=getKey());
							}
							//TODO fix implimation; update on save
							String command[]={"xdg-open",fileToBeOpened.getAbsolutePath()};
							Process p=Runtime.getRuntime().exec(command);
							p.waitFor();//file has been closed
							if(key!=null){//encryptFileAfterClose
								SyncropCipher.encrypt(fileToBeOpened, file, key);
							}
						} catch (BadPaddingException e1) {
							JOptionPane.showMessageDialog(null, "Invalid password");
						}
						catch(IllegalArgumentException e1){
							JOptionPane.showMessageDialog(null, "File is too large to encrypt");
						}
						
					}
					//TODO windows
					//if(Syncrop.isNot())
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}.start();
	}
}
