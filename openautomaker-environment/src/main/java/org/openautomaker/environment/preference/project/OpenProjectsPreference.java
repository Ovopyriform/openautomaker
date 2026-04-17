package org.openautomaker.environment.preference.project;

import static org.openautomaker.environment.preference.APathPreference.pathExists;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;

import org.openautomaker.environment.preference.APreference;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.inject.Singleton;

/**
 * user space preference for a list of paths. This is used for the recent projects list.
 */
@Singleton
public class OpenProjectsPreference extends APreference<List<Path>>{

    private List<Path> paths;

    protected OpenProjectsPreference() {
        super();
        readPaths();
    }

    /**
     * Reads the paths from the preference node. If any of the paths do not exist, they will be ignored.
     */
    private void readPaths() {
        List<Path> newPaths = new ArrayList<>();

        String pathsJSON = getNode().get(getKey(), "");
        if (pathsJSON.isEmpty()) {
            paths = newPaths;
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            List<String> pathStrings = mapper.readValue(pathsJSON, mapper.getTypeFactory().constructCollectionType(List.class, String.class));
            for (String pathString : pathStrings) {
                Path path = Path.of(pathString);
                if (pathExists(path))
                    newPaths.add(Path.of(pathString));
            }
        }
        catch (Exception e) {
            //If we fail to read the paths, just start with an empty list.
            e.printStackTrace();
        }

        paths = newPaths;
    }

    /**
     * Writes the paths to the preference node.
     */
    private void writePaths() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            List<String> pathStrings = paths.stream().map(Path::toString).toList();
            String pathsJSON = mapper.writeValueAsString(pathStrings);
            getNode().put(getKey(), pathsJSON);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Path> getValue() {
        return getPaths();
    }

    /**
     * Gets the list of paths for this preference. The returned list is unmodifiable, so to add or remove paths, use the add and remove methods.
     * 
     * @return List<Path> - The list of paths for this preference
     */
    public List<Path> getPaths() {
        return Collections.unmodifiableList(paths);
    }

    /**
     * Adds a path to the list of paths for this preference. If the path is already in the list, it will not be added again.
     * 
     * @param path The path to add
     * @return boolean - true if the path was added, false otherwise
     */
    public boolean add(Path path) {
        if (!paths.add(path))
            return false;

        writePaths();
        return true;
    }
    
    /**
     * Removes a path from the list of paths for this preference. If the path is not in the list, nothing will be done.
     * 
     * @param path The path to remove
     * @return boolean - true if the path was removed, false otherwise
     */
    public boolean remove(Path path) {
        if (!paths.remove(path))
            return false;

        writePaths();
        return true;
    }

    /**
     * This method is not supported for this preference.
     * 
     * @param value The list of paths to set
     * @throws UnsupportedOperationException if called
     */
    public void setValue(List<Path> value) {
        throw new UnsupportedOperationException("This preference does not support setting a the value as a list of paths. Use ");
    }

    @Override
    protected Preferences getNode() {
        return getUserNode();
    }
}
