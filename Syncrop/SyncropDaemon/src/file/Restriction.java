package file;

import java.io.File;

public class Restriction extends Directory{

	public Restriction(String dir) {
		super(dir);	
	}
	@Override
	public boolean isPathContainedInDirectory(String path)
	{
		if(literal)
			return dir.equals(path)||dir.isEmpty()||
					path.startsWith(dir+File.separatorChar);
		else 
			return path.matches(dir);
	}
}