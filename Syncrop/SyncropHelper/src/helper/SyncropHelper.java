package helper;
import java.io.IOException;

import account.Account;
import syncrop.ResourceManager;
import syncrop.Syncrop;
import syncrop.SyncropLogger;

public class SyncropHelper {

	public static void main(String[] args) {
		if(args!=null)
			try {
				Syncrop.logger=new SyncropLogger("syncropHeler.log");
				ResourceManager.initializeConfigurationFiles();
				ResourceManager.readFromConfigFile();
				new SyncropHelper(args);
			} catch (ArrayIndexOutOfBoundsException|IOException e) {
				e.printStackTrace();
			}
	}
	public SyncropHelper(String[] args) throws IOException{
		int startIndex=0;
		if(args[0].equals("--cloud")){
			Syncrop.setInstanceOfCloud(true);
			startIndex++;
		}
			
		Account account = null;
		if(args.length<startIndex+1){
			System.out.println("useage");
			return;
		}
		String username=args[startIndex+1];
		
		switch (args[startIndex]){
			case "enabled":
				ResourceManager.getAccount(username).setEnabled(true);
				break;
			case "disable":
				ResourceManager.getAccount(username).setEnabled(false);
				break;
			case "delete":
				ResourceManager.deleteAccount(ResourceManager.getAccount(args[1]));
				break;
			case "create":
				String email=args[startIndex+2];
				boolean enabled=args.length>3+startIndex?Boolean.parseBoolean(args[startIndex+3]):true;
				addAccount(username, email, enabled);
				break;
			case "add-restriction":
				account=ResourceManager.getAccount(username);
				for(int i=startIndex+2;i<args.length;i++)
					account.addRestrictions(args[startIndex+i]);
				break;
			case "add-dir":
				account=ResourceManager.getAccount(username);
				for(int i=startIndex+2;i<args.length;i++)
					account.addDirs(args[startIndex+i]);
				break;
			case "add-removeable-dir":
				account=ResourceManager.getAccount(username);
				for(int i=startIndex+2;i<args.length;i++)
					account.addRemoveableDirs(args[i]);
				break;
		}
		ResourceManager.writeConfigFile();
		if(account!=null)
			if(ResourceManager.getAccount(username)!=null)
				System.out.println("Account "+account);
			else System.out.println(account+" has been removed"); 
	}
	
	void addAccount(String username,String email,boolean enabled) throws IOException{
		Account account=new Account(username, email, enabled);
		int size=ResourceManager.getAllAccounts().size();
		ResourceManager.addAccount(account);
		ResourceManager.writeConfigFile();
		if(size==ResourceManager.getAllAccounts().size())
			System.out.println("Account "+account.getName()+" alread exists");
		else System.out.println("Account "+account.getName()+" has been created");
	}
}