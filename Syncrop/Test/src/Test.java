import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;

public class Test {

	public static void main(String[] args) throws IOException {
		
		File dir=new File("Testdir/Test");
		dir.mkdir();
		File regular=new File("SYNCROP_PLACEHOLDER");
		File link=new File(dir.getParentFile(),"link3");
		
		Files.createSymbolicLink(link.toPath(),regular.toPath());
		System.out.println(Files.readSymbolicLink(link.toPath()));
		

	}

}
