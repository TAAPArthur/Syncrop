package syncrop;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class RecursiveDeletionFileVisitor extends SimpleFileVisitor<Path>{
	
	
	/**
	 *  {@inheritDoc}
	 */
	@Override
	public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
		dir.toFile().delete();
		return FileVisitResult.CONTINUE;
	}
	/**
	 *  {@inheritDoc}
	 */
	@Override
	public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
		file.toFile().delete();
		return FileVisitResult.CONTINUE;
	}
	/**
	 *  {@inheritDoc}
	 */
	@Override
	public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
		return FileVisitResult.SKIP_SUBTREE;
	}
}
