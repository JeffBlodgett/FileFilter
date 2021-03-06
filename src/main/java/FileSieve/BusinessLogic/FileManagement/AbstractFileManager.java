package FileSieve.BusinessLogic.FileManagement;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Abstract file management class with default implementations for deleting and opening files and folders
 *
 * @param <T>   The type of the object returned by the "copyPathname" method of the FileCopier interface. In a simple
 *              implementation this may simply be a Boolean object that indicates if the copy operation was started or
 *              completed successfully.
 * @param <L>   The type of the listener which is to receive copy notifications, as set by the "setCopyOperationsListener"
 *              method of the FileCopier interface.
 * @param <C>   The type of the Comparator object to be used by the copyPathname method in determining if two
 *              files are similar.
*/
abstract class AbstractFileManager<T, L, C> implements FileOpener, FileDeleter, FileCopier<T, L, C> {

    private boolean disableDesktopOpenMethod = false;

    /**
     * Prevents the Desktop.open method from being called within the class' openPathname method during JUnit testing.
     * Flag holds for only one client call of the "openPathname" method. Had considered using a mock object for the
     * Desktop instance but the runtime's Desktop instance is a singleton instantiated at runtime startup.
     *
     * @param disableFileOpen                   pass a value of "true "if Desktop.open method is to be disable for testing
     */
    protected void setDesktopOpenDisabled(boolean disableFileOpen) {
        disableDesktopOpenMethod = disableFileOpen;
    }

    /**
     * Deletes a given file or folder
     *
     * @param pathname                          pathname of file or folder to delete
     * @return                                  true if the file or folder was deleted, false if it did not exist
     * @throws NullPointerException             thrown if provided pathname is null (RunTimeException)
     * @throws SecurityException                thrown if the SecurityManager.checkDelete method throws a SecurityException (RunTimeException)
     * @throws IOException                      thrown if some other I/O error occurs
     */
    @Override
    public boolean deletePathname(Path pathname) throws NullPointerException, SecurityException, IOException  {
        if (pathname == null) {
            throw new NullPointerException("null pathname provided");
        }

        boolean result = false;

        if (Files.exists(pathname)) {
            if (! Files.isDirectory(pathname)) {
                Files.delete(pathname);
                result = true;
            } else {
                deleteRecursively(pathname);
                result = true;
            }
        }

        return result;
    }

    /**
     * Opens the given file or folder using the application registered on the host system for opening files of the
     * pathname type. The default file browser is used if the pathname specified is a directory.
     *
     * @param pathname                          pathname of a file or folder to be opened
     * @throws NullPointerException             thrown if provided pathname is null (RunTimeException)
     * @throws UnsupportedOperationException    thrown if the platform does not support the Desktop class or does not support the Desktop.Action.OPEN action (RunTimeException)
     * @throws SecurityException                thrown if the SecurityManager.checkDelete method throws a SecurityException (RunTimeException)
     * @throws IllegalArgumentException         thrown if the provided pathname does not exist
     * @throws IOException                      thrown if the specified file has no associated application or the associated application fails to be launched
     */
    @Override
    public void openPathname(Path pathname) throws NullPointerException, UnsupportedOperationException, IllegalArgumentException, SecurityException, IOException {
        try {
            if (pathname == null) {
                throw new NullPointerException("null pathname provided");
            }

            if (Desktop.isDesktopSupported()) {
                if (!GraphicsEnvironment.isHeadless()) {
                    Desktop desktop = Desktop.getDesktop();
                    if (desktop.isSupported(Desktop.Action.OPEN)) {
                        if (!disableDesktopOpenMethod) {
                            desktop.open(pathname.toFile());
                        }
                    }
                } else {
                    throw new UnsupportedOperationException("The system is headless");
                }
            } else {
                throw new UnsupportedOperationException("Desktop class is not supported by this platform");
            }
        } finally {
            disableDesktopOpenMethod = false;
        }
    }

    /**
     * Private utility method for deleting files and folders recursively (e.g. from a folder).
     * Original code posted by Trevor Robinson at:
     * http://stackoverflow.com/questions/779519/delete-files-recursively-in-java/8685959#8685959
     *
     * @param path              pathname of folder to be deleted recursively (all content will be deleted)
     * @throws IOException      thrown if the folder could not be deleted
     */
    private static void deleteRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                /* try to delete the file anyway, even if its attributes could not be read, since delete-only access is
                   theoretically possible */
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc == null) {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                } else {
                    // directory iteration failed; propagate exception
                    throw exc;
                }
            }
        });
    }

} // abstract class AbstractFileManager<T, L, C> implements FileOpener, FileDeleter, FileCopier<T, L, C>
