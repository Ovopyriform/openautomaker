
package celtech.configuration;

import java.io.File;
import java.io.FileFilter;

import org.openautomaker.ui.project.robox.RoboxFile;

/**
 *
 * @author ianhudson
 */
public class ProjectFileFilter implements FileFilter {

	/**
	 *
	 * @param pathname
	 * @return
	 */
	@Override
	public boolean accept(File pathname) {
		if (pathname.getName().endsWith(RoboxFile.EXTENSION)) {
			return true;
		}
		else {
			return false;
		}
	}

}
