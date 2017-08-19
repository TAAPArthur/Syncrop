package helper;
import account.Account;
import listener.FileWatcher;
import listener.actions.RemoveSyncropConflictsAction;
import settings.SettingsManager;
import syncrop.ResourceManager;
import syncrop.Syncrop;
import syncrop.SyncropLogger;

public class SyncropHelper {

	enum Commands{
		HELP("help","displays this help message"),
		CLOUD("cloud","run as cloud"),
		INSTANCE("instance",""),
		USERNAME("username","username",true),
		EMAIL("email","email",true),
		FORCE("force","use with sync",true),
		HARD("hard","use with force to delte files not on client",true),
		
		
		ENABLE("enable", "enable account by username"),
		DISABLE("disable", "disable account by uername"),
		ADD_DIR("add-dir","adds a dir relative to HOME or a removable dirs iff it starts with /"),
		ADD_RESTRICTION("add-restriction","adds a restriction"),
		ADD_ACCOUNT("add-account","adds an account"),
		SYNC("sync","syncs client and remote"),
		REMOVE_CONFLICTS("remove-conflicts","remove conflicts locally"),
		SET_SETTING("set-setting","name value"),
		SAVE_SETTINGS("save-setting","saves currently loaded settings");
		;
		private final boolean setting;
		
		private final String longName;
		private final String description;
		
		private Commands(String longName,String description) {
			this(longName,description, false);
		}
		private Commands(String longName,String description, boolean setting) {
			this.longName = longName;
			this.setting = setting;
			this.description = description;
		}
		
		public String getLongName() {
			return longName;
		}
		public boolean isSetting() {
			return setting;
		}
		public String getDescription() {
			return description;
		}
		public String getHelpMessage() {
			return getLongName()+": "+getDescription();
		}
	}
	public static void main(String[] args) {
		String username = null;
		String email = null;
		boolean force = false;
		boolean hard = false;
		
		try {
			for(int i=0; i<args.length;i++) {
				Commands c = getCommand(args[i]);
				switch(c) {
					case USERNAME:
						username =  args[++i];
						continue;
					case EMAIL:
						email =  args[++i];
						continue;
					case FORCE:
						force = true;
						continue;
					case HARD:
						hard =true;
						continue;
					case CLOUD:
						Syncrop.setInstanceOfCloud(Boolean.parseBoolean(args[++i]));
						continue;
					case INSTANCE:
						Syncrop.setInstance(args[++i]);
						continue;
					default:
						continue;
				}
			}
			Syncrop.logger=new SyncropLogger("syncropHeler.log");
			ResourceManager.initializeConfigurationFiles();
			SettingsManager.loadSettings();
			ResourceManager.readFromConfigFile();
			for(int i=0; i<args.length;i++) {
				Commands c = getCommand(args[i]);
				if(c.isSetting())
					continue;
				switch(c) {
					case USERNAME:
					case EMAIL:
					case FORCE:
					case HARD:
					case CLOUD:
					case INSTANCE:
						continue;
					case ENABLE:
						output("Enabling "+ResourceManager.getAccount(username).getName());
						ResourceManager.getAccount(username).setEnabled(true);
						ResourceManager.writeConfigFile();
						break;
					case DISABLE:
						output("Disabling "+ResourceManager.getAccount(username).getName());
						ResourceManager.getAccount(username).setEnabled(false);
						ResourceManager.writeConfigFile();
						break;
					case ADD_ACCOUNT:
						if(username == null)
							username = args[++i];
						if(email == null)
							email = args[++i];
						Account a =new Account(username, email, false);
						output("Adding account "+ a);
						ResourceManager.addAccount(a);
						ResourceManager.writeConfigFile();
						break;
					case ADD_RESTRICTION:
						for(int n =i+1; n< args.length; n++)
							ResourceManager.getAccount(username).addGenericDir(args[n]);
						ResourceManager.writeConfigFile();
						break;
					case ADD_DIR:
						for(int n =i+1; n< args.length; n++)
							ResourceManager.getAccount(username).addRestrictions(args[n]);
						ResourceManager.writeConfigFile();
						break;
					case SET_SETTING:
						boolean b=SettingsManager.dynamicallyLoadSetting(args[++i],args[++i]);
						output(b?"successfully added setting":"failed to set setting");
					case SAVE_SETTINGS:
						SettingsManager.saveSettings(true);
						output("saved setting");
					case SYNC:
						break;
						
					case REMOVE_CONFLICTS:
						new FileWatcher(null).checkAllFiles(new RemoveSyncropConflictsAction());
						break;
					case HELP:
					default:
						displayHelpMessage();
						System.exit(1);
						break;
						
				}
				return;
			}
			
			
			} catch (Exception e) {
				displayHelpMessage();
			}
	}
	private static void displayHelpMessage(){
		for(Commands c :Commands.values()) 
			output(c.getHelpMessage());
		
	}
	private static Commands getCommand(String arg) {
		if(arg.startsWith("--"))
			return Commands.valueOf(arg.substring(2));
		else if(arg.startsWith("-"))
			return Commands.valueOf(arg.substring(1));
		else return Commands.valueOf(arg);
	}
	private static void output(Object s) {
		System.out.println(s);
	}
	
	
}