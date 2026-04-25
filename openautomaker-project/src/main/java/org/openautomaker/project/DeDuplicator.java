package org.openautomaker.project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;


public class DeDuplicator {

	/**
	 * If the desired path already exists, returns a new path with a suffix added to the name to avoid overwriting the existing file.
	 * For example, if "Project.rbxproj" already exists, it will return "Project (1).rbxproj".  If that also exists, it will return "Project (2).rbxproj", etc.
	 *
	 * @param desiredPath the desired path for the new file
	 * @return a path that does not already exist, with a suffix added if necessary
	 */
	public static Path deduplicate(Path desiredPath) {
		if (!Files.exists(desiredPath))
			return desiredPath;

		// If it's a directory just append to the end.  If it's a file we need to add a suffix before the extension (if it has one).
		if (Files.isDirectory(desiredPath)) {
			int i = 1;
			Path newPath = desiredPath.resolveSibling(desiredPath.getFileName().toString() + " (" + i++ + ")");
			while (Files.exists(newPath)) {
				newPath = desiredPath.resolveSibling(desiredPath.getFileName().toString() + " (" + i++ + ")");
			}
			return newPath;
		}

		// If it's a file that already exists we can simply add a siffix to the name, e.g. "Project (1).rbxproj", "Project (2).rbxproj", etc.
		String[] nameElements = desiredPath.getFileName().toString().split("\\.");

		//pop the last element of the string array ans treat as the extension.  If there is only one element, we can assume there is no extension and just add the suffix to the end of the name.
		String nameWithoutExtension = nameElements[0];
		String extension = "";
		if (nameElements.length > 1) {
			extension = nameElements[nameElements.length - 1];
			nameWithoutExtension = String.join(".", Arrays.copyOf(nameElements, nameElements.length - 1));
		}

		// If the file name doesn't have an extension, we can just add the suffix to the end of the name.
		String namePattern;
		if (extension.isEmpty())
			namePattern = nameWithoutExtension + " (%d)";
		else
			namePattern = nameWithoutExtension + " (%d)." + extension;

		int i = 1;
		desiredPath = desiredPath.resolveSibling(String.format(namePattern, i++));

		while (Files.exists(desiredPath)) {
			desiredPath = desiredPath.resolveSibling(String.format(namePattern, i++));
		}

		return desiredPath;
	}

	private DeDuplicator() {
	}
}
