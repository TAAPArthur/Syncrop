package file;

import java.io.File;

import syncrop.ResourceManager;

public class Directory 
{
	final protected String dir;
	final private String literalDir;
	/**
	 * If true, then the Directory should be taken literally else it should be treated as pattern 
	 */
	protected boolean literal=true;
	public Directory(String literalDir)
	{
		this.literalDir=literalDir;
		literal=!containsMatchingChars(literalDir);
		if(literal)
			dir=literalDir;
		else dir=ResourceManager.convertToPattern(literalDir);		
	}
	public String getDir(){return dir;}
	public String getLiteralDir(){return literalDir;}
	
	public boolean isPathContainedInDirectory(String path)
	{
		if(literal)
			return dir.equals(path)||dir.isEmpty()||
					path.startsWith(dir+File.separatorChar)||dir.startsWith(path+File.separatorChar);
		else 
			return path.matches(dir);
	}
	
	public boolean equals(Directory dir){return dir.equals(dir.dir);}
	
	public String toString(){
		return "dir:"+literalDir;
	}
	/**
	 * @return if the dir should be taken literally or as a pattern
	 */
	public boolean isLiteral() {
		return literal;
	}
	/**
	 * @param literal Whether the dir should be taken literally or as a pattern
	 */
	public void setLiteral(boolean literal) {
		this.literal = literal;
	}
	
	/**
	 * Checks to see if the String is meant to be interpreted as a pattern
	 * @param s the String to check
	 * @return true if s contains at least one '*' or an '?'
	 */
	public static boolean containsMatchingChars(String s)
	{
		return s.contains("*")||s.contains("?");
	}
	
}