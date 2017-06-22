
import java.io.IOException;

import syncrop.Syncrop;
import syncrop.SyncropLogger;
public class SyncropTester extends Syncrop{
	public SyncropTester() throws IOException {
		super("");
	}
	protected void initializeLogger() throws IOException{
		logger=new SyncropLogger("syncropTester.log");
	}
	public static void main(String[] args) throws Exception {
		SyncropTester tester=new SyncropTester();
		tester.test();
	}
	void test(){
		
	}
	
  }