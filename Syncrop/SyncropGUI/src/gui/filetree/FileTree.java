package gui.filetree;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;

import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import account.Account;
import file.SyncropItem;
import syncrop.ResourceManager;


public class FileTree extends JTree implements TreeExpansionListener,TreeModelListener,KeyListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	HashSet<TreePath>expandedTreePaths=new HashSet<>();

	HashSet<SyncropTreeNode> nodes=new HashSet<SyncropTreeNode>();
	
	volatile boolean loading=false;
	String searchPattern=null;
	/** Construct a FileTree */
	public FileTree(Account a) {
		super();
		setEditable(true);
		setInvokesStopCellEditing(true);
		setModel(new DefaultTreeModel(addNodes(a, null, new File("/"))));
		getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
		//setModel(new DefaultTreeModel(addNodes(a, null, new File("/"))));
		
		setShowsRootHandles(true);
		setCellRenderer(new SyncropTreeCellRender());
		addKeyListener(this);
		addTreeExpansionListener(this);
		getModel().addTreeModelListener(this);
		
			
	}
	public void refresh(){
		refresh((SyncropTreeNode) ((DefaultMutableTreeNode)getModel().getRoot()));
	}
	private void refresh(SyncropTreeNode node){
		
		Enumeration<?>children=node.children();
		File file=node.getFile();
		HashSet<File>changedFiles=new HashSet<>();
		changedFiles.addAll(Arrays.asList(file.listFiles()));
		ArrayList<File>unchangedFiles=new ArrayList<>();
		while(children.hasMoreElements()){
			SyncropTreeNode childNode=(SyncropTreeNode) children.nextElement();
			File childFile=childNode.getFile();
			boolean notDeleted=false;
			for(String s:file.list()){
				if(childFile.getName().equals(s)){
					changedFiles.remove(childFile);
					unchangedFiles.add(childFile);
					notDeleted=true;
					break;
				}
			}
			if(!notDeleted)//delted file
				((DefaultTreeModel)getModel()).removeNodeFromParent(childNode);
			else if(!ResourceManager.getAccount().isPathContainedInDirectories(childFile.getAbsolutePath()))
				((DefaultTreeModel)getModel()).removeNodeFromParent(childNode);
			else if(childNode.getChildCount()>0)
				refresh(childNode);			
		}
		
		for(File f:changedFiles){
			if(ResourceManager.getAccount().isPathContainedInDirectories(f.getAbsolutePath())){
				System.out.println(f);
				if(f.isDirectory())
					addNodes(ResourceManager.getAccount(), node, f);
				else {
					int i;
					for(i=0;i<unchangedFiles.size();i++)
						if(unchangedFiles.get(i).getName().compareToIgnoreCase(f.getName())>0)
							break;
					((DefaultTreeModel)getModel()).insertNodeInto(new SyncropTreeNode(f), node, i);
				}
					
			}
		}
	}
	
	public File getFile(Object[] path,Object name){
		String s="";
		for(Object o:path)
			s=s+File.separator+o;
		return new File(s+File.separator+name);
	}
	

	/** Add nodes from under "dir" into curTop. Highly recursive. */

	private DefaultMutableTreeNode addNodes(Account account,DefaultMutableTreeNode curTop, File dir) {
		String path = dir.getAbsolutePath();
		SyncropTreeNode curDir = new SyncropTreeNode(dir);
		
		if (curTop != null) { // should only be null at root
			curTop.add(curDir);
		}
		
		
		ArrayList<String>files=new ArrayList<>();
		files.addAll(Arrays.asList(dir.list()));
		Collections.sort(files,String.CASE_INSENSITIVE_ORDER);
		
		// Make two passes, one for Dirs and one for Files. This is #1.
		for (int i = 0; i < files.size(); i++) {
		  File file = new File(path,files.get(i));
		  if (file.isDirectory()){
			  if(account.isPathContainedInDirectories(file.getAbsolutePath()))
				  addNodes(account,curDir, file);
			  
		  }
		}
				
		// Pass two: for files.
		for (int i = 0; i < files.size(); i++){
			File file = new File(path,files.get(i));
				if (!file.isDirectory()&&account.isPathContainedInDirectories(file.getAbsolutePath()))
					curDir.add(new SyncropTreeNode(file));
	 
		}
		  
		return curDir;
	}
	
	
	

	@Override
	public void treeStructureChanged(TreeModelEvent e) {
		
	}
	
	@Override
	public void treeNodesRemoved(TreeModelEvent e) {
	}
	
	@Override
	public void treeNodesInserted(TreeModelEvent e) {
		
	}
	
	@Override
	public void treeNodesChanged(TreeModelEvent e) {
		if(loading)return;
		SyncropTreeNode node=(SyncropTreeNode)getLastSelectedPathComponent();
		
		File newFile=getFile(e.getPath(),e.getChildren()[0]);
		try {
			Files.move(node.getFile().toPath(), newFile.toPath());
			node.setFile(newFile);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
	}

	public void deleteSelectedNodes(){
		if(JOptionPane.showConfirmDialog(null, "You are about to delete files")==JOptionPane.YES_OPTION)
		while(true){
			SyncropTreeNode node=((SyncropTreeNode)getLastSelectedPathComponent());
			if(node==null)break;
			((DefaultTreeModel)getModel()).removeNodeFromParent(node);
			
			//((SyncropTreeNode)node.getParent()).remove(((SyncropTreeNode)getLastSelectedPathComponent()));
			if(node.getFile().exists())
				if(node.getFile().isDirectory())
					try {
						Files.walkFileTree(node.getFile().toPath(), new SimpleFileVisitor<Path>() {
							@Override
							public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
								dir.toFile().delete();
								return FileVisitResult.CONTINUE;
							}
							@Override
							public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
								file.toFile().delete();
								return FileVisitResult.CONTINUE;
							}
							@Override
							public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
								return FileVisitResult.SKIP_SUBTREE;
							}	
						});
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				else node.getFile().delete();
		};
	}
	
	
	public SyncropTreeNode getSelectedNode(){
		return ((SyncropTreeNode)getLastSelectedPathComponent());
	}
	public void createNode(final String name,boolean directory) throws IOException{
		SyncropTreeNode node=((SyncropTreeNode)getLastSelectedPathComponent());
		
		if(!node.getFile().isDirectory())
			node=(SyncropTreeNode) node.getParent();
		
		File newFile;
		String fileName=name;
		int count=0;
		do {
			newFile=new File(node.getFile(),fileName);
			count++;
			fileName=name+count;
		}while(newFile.exists());
		if(directory)
			newFile.mkdir();
		else newFile.createNewFile();
		int i=0;
		
		for(;i<node.getChildCount();i++){
			SyncropTreeNode childNode=((SyncropTreeNode)node.getChildAt(i));
			if(directory&&!childNode.getFile().isDirectory())
				break;
			if(!directory&&childNode.getFile().isDirectory())
				continue;
			if(fileName.compareTo(childNode.getFile().getName())<0)
				break;
		}
		((DefaultTreeModel)getModel()).insertNodeInto(new SyncropTreeNode(newFile), node, i);
	}
	
	@Override
	public void keyPressed(KeyEvent e) {
		switch (e.getKeyCode()){
			case KeyEvent.VK_DELETE:
					deleteSelectedNodes();
				break;
			case KeyEvent.VK_ENTER:
				if(isCollapsed(getSelectionPath()))
					this.expandPath(getSelectionPath());
				else this.collapsePath(getSelectionPath());
				break;		
			case KeyEvent.VK_F:
				if(e.isControlDown())
					search();
				break;
			case KeyEvent.VK_ESCAPE:
				stopSearching();
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {
	}

	@Override
	public void keyTyped(KeyEvent e) {
	}
	@Override
	public void treeCollapsed(TreeExpansionEvent event) {
		if(loading)return;
		expandedTreePaths.remove(getFilePath(event.getPath().getPath()));
	}
	@Override
	public void treeExpanded(TreeExpansionEvent event) {
		if(loading)return;
		expandedTreePaths.add(event.getPath());
	}

	public static String getFilePath(Object paths[]){
		String s="";
		for(Object path:paths)
			s+=File.separator+path;
		return s.substring(1);
	}
	
	public void search(){
		
		SyncropTreeNode node=(SyncropTreeNode) this.getModel().getRoot();
		
		searchPattern=
				ResourceManager.convertToPattern("*"+
						JOptionPane.showInputDialog("Search For:")+"*");
		searchChildNodes(node, searchPattern);
		
	}
	private void searchChildNodes(SyncropTreeNode node,String pattern){
		Enumeration<?>children=node.children();
		while(children.hasMoreElements()){
			SyncropTreeNode child=(SyncropTreeNode) children.nextElement();
			searchChildNodes(child,pattern);
			if(child.getFile().getName().matches(pattern)){
				expandPath(new TreePath(child.getPath()));
			}
		}
	}
	public void collapseAllChildNodes(){
		collapseChildNodes((SyncropTreeNode) this.getModel().getRoot());
	}
	public void collapseChildNodes(SyncropTreeNode node){
		Enumeration<?>children=node.children();
		while(children.hasMoreElements()){
			SyncropTreeNode child=(SyncropTreeNode) children.nextElement();
			collapseChildNodes(child);
			collapsePath(new TreePath(child.getPath()));
		}
	}
	public boolean isSearching(){return searchPattern!=null;}
	public void stopSearching(){searchPattern=null;}
	String getSearchPattern(){
		return searchPattern;
	}
	
	
		  
}
class SyncropTreeCellRender extends DefaultTreeCellRenderer{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf,
			int row, boolean arg6) {
		Component c=super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, arg6);
		
		File file=((SyncropTreeNode)value).file;
		Account account=ResourceManager.getAccount();
		boolean removable=false;
		if(!account.getHome(true).startsWith(file.getAbsolutePath())&&!account.getHome(false).startsWith(file.getAbsolutePath()))
			do{
				if(!SyncropItem.isValidFileName(file.getAbsolutePath())||account.isPathContainedInDirectory(file.getAbsolutePath().substring(account.getHome(removable).length()), removable)&&
					!account.isPathEnabled(file.getAbsolutePath().substring(account.getHome(removable).length()))){
					c.setForeground(Color.RED);
					break;
				}
				removable=!removable;
			}while(removable);
		
		if(((FileTree)tree).isSearching())
			if(file.getName().matches(((FileTree)tree).getSearchPattern())){
				c.setForeground(Color.GREEN);
			}
		
		return c;
	}
}