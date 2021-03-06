package FileSieve.BusinessLogic.FileEnumeration;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Concrete implementation of a FileEnumerator. Provides for the discovery of paths (folders and files) within one or
 * more provided search paths. Principle methods return a Map with key-value pairs of "Path-BasicFileAttributes" which
 * may be passed to methods of a FileDifferentiator or FileManager instance. This class has package-private access.
 */
class FileDiscoverer implements FileEnumerator {

    private int fileCountFromLastEnumeration = 0;
    private long totalFileByteCountFromLastEnumeration = 0;

    // Used in determining when the above two counters should be reset to zero (i.e. when a new file enumeration has begun)
    private int recursionLevel = 0;

    /**
     * Returns a count of the number of files discovered during the most recently completed file discovery.
     * The count excludes folders.
     *
     * @return  count of the number of discovered files from most recent enumeration
     */
    @Override
    public int getFileCount() {
        return fileCountFromLastEnumeration;
    }

    /**
     * Returns the sum of the bytes of the files discovered during the most recently completed file discovery.
     *
     * @return  sum of the bytes of discovered files from most recent enumeration
     */
    @Override
    public long getByteCount() {
        return totalFileByteCountFromLastEnumeration;
    }

    /**
     * Returns a list of discovered folders and files amongst a list of provided pathnames, including the paths of
     * empty folders. The returned Map is a LinkedHashMap, which maintains insertion order while also preventing
     * duplicate keys. The map's keys are the discovered folder/file paths, while values are set to instances of the
     * BasicFileAttributes class, containing attributes for each folder or file. The provided list of paths to
     * enumerate may contain folders or files. File paths in the passed list are, effectively, added to the returned
     * Map since folders are given order priority in the returned Map. Folders, followed by files, within each
     * discovered folder are ordered lexicographically.
     *
     * @param pathsToEnumerate  list of paths with the pathnames of folders and specific files to be included in the returned Map
     * @param recursiveSearch   boolean parameter indicating if path discovery should extend to subfolders
     * @return                  discovered folders/files and their BasicFileAttributes
     * @throws IOException      thrown if an I/O exception occurs
     */
    @Override
    public Map<Path, BasicFileAttributes> getPathnames(List<Path> pathsToEnumerate, boolean recursiveSearch) throws IOException {
        // Set file and byte counts to zero if this is the start of a new enumeration
        if (recursionLevel == 0) {
            fileCountFromLastEnumeration = 0;
            totalFileByteCountFromLastEnumeration = 0;
        }

        ++recursionLevel;

        try {
            if ((pathsToEnumerate == null) || (pathsToEnumerate.size() == 0)) {
                throw new IllegalArgumentException("no paths to existing files or folder were provided for enumeration");
            }

            // Map to be returned
            Map<Path, BasicFileAttributes> pathMap;

            // Test to ensure Path objects abstract existing files or folders
            List<Path> sourcePaths = new ArrayList<>(pathsToEnumerate.size());
            for (Path path : pathsToEnumerate) {
                if ((path != null) && (! sourcePaths.contains(path)) && (Files.exists(path, LinkOption.NOFOLLOW_LINKS))) {
                    sourcePaths.add(path);
                }
            }

            // Sort paths lexicographically, with folders first
            Path[] rootPathsArray;
            if (sourcePaths.size() == 0) {
                throw new IllegalArgumentException("no path(s) to existing files or folders were provided for enumeration");
            } else {
                rootPathsArray = sourcePaths.toArray(new Path[sourcePaths.size()]);
                Arrays.sort(rootPathsArray, FileComparator.getInstance());
                sourcePaths.clear();
                Collections.addAll(sourcePaths, rootPathsArray);
            }

            // Remove superfluous paths from sourcePaths list
            for (int i = 1; i < sourcePaths.size(); ++i) {
                Path path = sourcePaths.get(i);
                Path parentFolder = path.getParent();

                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    if (Files.isSameFile(parentFolder, sourcePaths.get(i - 1))) {
                        // The path will be discovered by enumeration of the previous source path
                        sourcePaths.remove(i);
                        --i;
                    } else if (path.startsWith(sourcePaths.get(i - 1)) && recursiveSearch) {
                        // The path will be discovered by enumeration of the previous source path
                        sourcePaths.remove(i);
                        --i;
                    }
                } else {
                    for (int j = 0; j < sourcePaths.size(); ++j) {
                        if (Files.isSameFile(parentFolder, sourcePaths.get(j))) {
                            // The path will be discovered by enumeration of a previous source path
                            sourcePaths.remove(i);
                            --i;
                            break;
                        } else if (parentFolder.startsWith(sourcePaths.get(j)) && recursiveSearch) {
                            // The path will be discovered by enumeration of a previous source path
                            sourcePaths.remove(i);
                            --i;
                            break;
                        }
                    }
                }
            }

            // Initialize map if we got this far
            pathMap = Collections.synchronizedMap(new LinkedHashMap<Path, BasicFileAttributes>(50));

            for (Path rootPath : sourcePaths) {
                List<Path> directoryContents = new ArrayList<>(25);

                if (Files.isRegularFile(rootPath, LinkOption.NOFOLLOW_LINKS)) {
                    // Add file path to Map
                    pathMap.put(new DiscoveredPath(rootPath), Files.readAttributes(rootPath, BasicFileAttributes.class));

                    // Increment discovered file counter
                    ++fileCountFromLastEnumeration;

                    // Add files bytes to byte counter
                    totalFileByteCountFromLastEnumeration += rootPath.toFile().length();

                } else {
                    // Enumerate contents of directory
                    try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(rootPath)) {
                        for (Path path : dirStream) {
                            directoryContents.add(path);
                        }
                    }

                    if (directoryContents.size() > 0) {
                        // Sort paths lexicographically, with folders first
                        Path[] pathsInDirectory = directoryContents.toArray(new Path[directoryContents.size()]);
                        Arrays.sort(pathsInDirectory, FileComparator.getInstance());
                        directoryContents.clear();

                        // Add paths to Map
                        for (Path path : pathsInDirectory) {
                            if (recursionLevel == 1) {
                                pathMap.put(new DiscoveredPath(path, rootPath), Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS));
                            } else {
                                pathMap.put(new DiscoveredPath(path), Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS));
                            }

                            if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                                // Increment discovered file counter
                                ++fileCountFromLastEnumeration;

                                // Add file's byte size to byte counter
                                totalFileByteCountFromLastEnumeration += path.toFile().length();
                            }
                        }

                        if (recursiveSearch) {
                            // Call this method recursively with each discovered subfolder as the path to enumerate
                            for (Path path : pathsInDirectory) {
                                if (Files.isDirectory(path)) {
                                    ++recursionLevel;
                                    try {
                                        pathMap.putAll(getPathnames(path, true));

                                    /* Decrement the recursionLevel counter. This try-finally block maintains a valid
                                       state for the recursionLevel counter if an IOException is thrown */
                                    } finally {
                                        --recursionLevel;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            return pathMap;

        /* Decrement the recursionLevel counter irregardless of the occurrence of an exception. This try-finally
           block maintains a valid state for the recursionLevel counter. */
        } finally {
            --recursionLevel;
        }
    }

    /**
     * Convenience method (overload) for the getPathnames method. Works the same as the getPathnames(List<Path> , boolean)
     * version of this method but assumes a recursive search (boolean value of "true") for its second parameter.
     *
     * @param pathsToEnumerate  list of paths with the pathnames of folders and specific files to be included in the returned Map
     * @return                  discovered files/folders and their BasicFileAttributes
     * @throws IOException      thrown if an I/O exception occurs
     */
    @Override
    public Map<Path, BasicFileAttributes> getPathnames(List<Path> pathsToEnumerate) throws IOException{
        return getPathnames(pathsToEnumerate, true);
    }

    /**
     * Convenience method (overload) for the getPathnames method. Works the same as the getPathnames(List<Path> , boolean)
     * version of this method but takes a reference to a single Path object, rather than a List<Path>, as it first
     * parameter.
     *
     * @param pathToEnumerate   a single Path within which to discover folders/files
     * @param recursiveSearch   boolean parameter indicating if searches should extend to subfolders
     * @return                  discovered folders/files and their BasicFileAttributes
     * @throws IOException      thrown if an I/O exception occurs
     */
    @Override
    public Map<Path, BasicFileAttributes> getPathnames(Path pathToEnumerate, boolean recursiveSearch) throws IOException {
        if ((pathToEnumerate == null) || (!Files.exists(pathToEnumerate, LinkOption.NOFOLLOW_LINKS))) {
            throw new IllegalArgumentException("no path to existing an file or folder was provided for enumeration");
        }

        List<Path> paths = new ArrayList<>(1);
        paths.add(pathToEnumerate);

        return getPathnames(paths, recursiveSearch);
    }

    /**
     * Convenience method (overload) for the getPathnames method. Works the same as the getPathnames(List<Path> , boolean)
     * version of this method but takes a reference to a single Path object, rather than a List<Path>, as its first
     * parameter and assumes a recursive search (boolean value of "true") for its second parameter.
     *
     * @param pathToEnumerate   a single Path within which to discover files and folders.
     * @return                  discovered files/folders and their BasicFileAttributes
     * @throws IOException      thrown if an I/O exception occurs
     */
    public Map<Path, BasicFileAttributes> getPathnames(Path pathToEnumerate) throws IOException {
        if ((pathToEnumerate == null) || (!Files.exists(pathToEnumerate, LinkOption.NOFOLLOW_LINKS))) {
            throw new IllegalArgumentException("no path to existing an file or folder was provided for enumeration");
        }

        List<Path> paths = new ArrayList<>(1);
        paths.add(pathToEnumerate);

        return getPathnames(paths, true);
    }

    /**
     * File comparator (function object) for use by instances in sorting an array of Path objects
     * lexicographically by name, with folders listed first and files listed second.
     */
    private static class FileComparator implements Comparator<Path> {

        public static final FileComparator INSTANCE = new FileComparator();

        public static FileComparator getInstance() {
            return INSTANCE;
        }

        private FileComparator() { }

        public int compare(Path path1, Path path2) {
            int result;

            if (Files.isDirectory(path1) && Files.isRegularFile(path2, LinkOption.NOFOLLOW_LINKS)) {
                result = -1;
            } else if (Files.isRegularFile(path1, LinkOption.NOFOLLOW_LINKS) && Files.isDirectory(path2)) {
                result = 1;
            } else {
                result = path1.compareTo(path2);
            }

            return result;
        }

    } // class FileComparator implements Comparator<Path>

} // class FileDiscoverer implements FileEnumerator
