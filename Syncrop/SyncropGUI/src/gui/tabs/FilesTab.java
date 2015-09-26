package gui.tabs;

import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.IOException;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.tree.TreePath;

import encryption.SyncropCipher;
import gui.SyncropGUI;
import gui.filetree.FileTree;
import settings.Settings;
import syncrop.ResourceManager;
import syncrop.Syncrop;
//TODO fix reload
public class FilesTab extends JScrollPane implements SyncropTab,MouseListener{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	SyncropGUI gui;
	final FileTree tree=getFileTree();
	JPanel panel=new JPanel();
	FileOptionMenu menu=new FileOptionMenu(tree);
	public FilesTab(SyncropGUI parent){
		super();
		setViewportView(panel);
		
		panel.setLayout(new GridLayout(0, 1));
		

		panel.add(tree);
		setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		gui=parent;
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
	JMenuItem encrypt=new JMenuItem("Encrypt");
	JMenuItem decrypt=new JMenuItem("Decrypt");
	JMenuItem search=new JMenuItem("Search");
	JMenuItem refresh=new JMenuItem("Refresh");
	JMenuItem collapseAll=new JMenuItem("Collapse All");
	
	FileTree tree;
	FileOptionMenu(FileTree tree){
		super();
		add(open);open.addActionListener(this);
		add(delete);delete.addActionListener(this);
		add(newFile);newFile.addActionListener(this);
		add(newDirectory);newDirectory.addActionListener(this);
		add(encrypt);encrypt.addActionListener(this);
		add(decrypt);decrypt.addActionListener(this);
		add(search);search.addActionListener(this);
		add(refresh);refresh.addActionListener(this);
		add(collapseAll);collapseAll.addActionListener(this);
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
			else if(e.getSource().equals(search))
				tree.search();
			else if(e.getSource().equals(collapseAll))
				tree.collapseAllChildNodes();
			else if(e.getSource().equals(encrypt)||e.getSource().equals(decrypt)){
				if(tree.getSelectedNode()!=null){
					String key=getKey();
					for(TreePath path:tree.getSelectionPaths()){
						File file=new File(FileTree.getFilePath(path.getPath()));
						if(e.getSource().equals(encrypt))
							SyncropCipher.encrypt(file,key);
						else 
							SyncropCipher.decrypt(file,key);
					}
				}
			}
			else if(e.getSource().equals(refresh))
				tree.refresh();
			
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
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
