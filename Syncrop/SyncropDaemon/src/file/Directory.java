package file;

import java.io.File;

import syncrop.ResourceManager;

public class Directory 
{
	protected String dir;
	private String literalDir;
	/**
	 * If true, then the Directory should be taken literally else it should be treated as pattern 
	 */
	private boolean literal=true;
	public Directory(String dir,boolean literal)
	{
		literalDir=dir;
		if(!literal)
		{
			dir=ResourceManager.convertToPattern(dir);
		}
		
		this.dir=dir;
		this.literal=literal;
	}
	public String getDir(){return dir;}
	public String getLiteralDir(){return literalDir;}
	public static boolean isPathContainedInDirectory(String path,String dir)
	{
		return dir.equals(path)||dir.isEmpty()||
					path.startsWith(dir+File.separatorChar);
	}
	public boolean isPathContainedInDirectory(String path)
	{
		if(literal)
			return dir.equals(path)||dir.isEmpty()||
					path.startsWith(dir+File.separatorChar);
		else 
			return path.matches(dir);
	}
	
	public boolean equals(Directory dir){return dir.equals(dir.dir);}
	
	public String toString(){return literalDir;}
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