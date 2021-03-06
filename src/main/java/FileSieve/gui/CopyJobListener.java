package FileSieve.gui;

import FileSieve.BusinessLogic.FileManagement.SwingCopyJob;
import FileSieve.BusinessLogic.FileManagement.SwingCopyJobException;
import FileSieve.BusinessLogic.FileManagement.SwingCopyJobListener;

import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.swing.*;

/**
 * Listens to the progress of copying operation and updates the GUI
 * @author olgakaraseva
 */
public class CopyJobListener implements SwingCopyJobListener {
    
    private static final int ONE_HUNDRED_PERCENT = 100;
    private int totalFiles;                 //total number of files to be copied
    private long totalBytes;                //total number of bytes to be copied
    private int filesDone;                  //track how many files are already copied
    private long bytesDone;                 //track how many bytes are already copied
    private Controller controller;
    private CopyScreen copyScreen;
    private ArrayList<File> copiedFiles;    //used to keep track of which files are added to
                                            //display and which are just updating percentage
    
    /**
     * @param cpscrn     Copy Screen instance
     * @param totFiles   total Files to be copied
     * @param totBytes   total bytes to be copied
     */
    CopyJobListener(Controller cntrl, CopyScreen cpscrn, int totFiles, long totBytes){
        controller = cntrl;
        copyScreen = cpscrn;
        totalFiles = totFiles;
        filesDone = 0;
        totalBytes = totBytes;
        bytesDone = 0;
        copiedFiles = new ArrayList<>();
    }

    /**
     * Updates progress bar to display total percent of copy process for all files
     *
     * @param swingCopyJob          reference to a swingCopyJob that currently copies files could be several
     *                              if multi-thread is enabled
     * @param percentProgressed     how many percents have been copied
     */ 
    @Override
    public void UpdateCopyJobProgress(SwingCopyJob swingCopyJob, int percentProgressed) {
        //since there is only a single instance of swingCopyJob there's no need
        //to pass it to controller to stop the copy job - controller keeps track of its
        //own swingCopyJob instance
        copyScreen.totalProgressBar.setValue(percentProgressed);
        if (percentProgressed == ONE_HUNDRED_PERCENT){
            controller.stopCopyJob(false);
        }
    }
    
     /**
     * Updates GUI with current state of copying process for individual file
     * @param swingCopyJob          reference to a swingCopyJob that currently copies files
     *                              could be several if multi-thread is enabled
     * @param pathnameBeingCopied   which file should be updated
     * @param percentProgressed     how many percents have been copied
     */
    @Override
    public void UpdatePathnameCopyProgress(SwingCopyJob swingCopyJob, Path pathnameBeingCopied, int percentProgressed) {
        File fileBeingCopied = pathnameBeingCopied.toFile();

        //show progress only for files, not for directories
        int fileIndex = copiedFiles.indexOf((Object) fileBeingCopied);
        if (fileBeingCopied.isFile() || (fileIndex != -1)) {
            String fileProgress = "Creating " + pathnameBeingCopied.toString() + " (" + percentProgressed + "%)";

            //check if file is already in the list
            //if not - then add it, otherwise - update it
            if (fileIndex != -1) {
                copyScreen.copyListModel.set(fileIndex, fileProgress);
                //System.err.println(pathnameBeingCopied.getFileName() + ": " + fileIndex + "- " + percentProgressed + "%");
                //when file is copied update the corresponding text in GUI
                if (percentProgressed == ONE_HUNDRED_PERCENT) {
                    bytesDone += copiedFiles.get(fileIndex).length();
                    filesDone++;
                    updateTotalProgress();
                }
            } else {
                copiedFiles.add(fileBeingCopied);
                copyScreen.copyListModel.addElement(fileProgress);
                //scroll to the bottom of the list as the items are added
                copyScreen.copyList.ensureIndexIsVisible(copiedFiles.size()-1);
            }
        }
    }

    /**
     * Catches exception which might be thrown during the copy process and displays an error message to the user.
     *
     * @param swingCopyJob              reference to a swingCopyJob that currently copies files
     *                                  could be several if multi-thread is enabled
     * @param swingCopyJobException     exception thrown during the copy operation - will be null if no exceptions
     *                                  are thrown
     */
    @Override
    public void JobFinished(SwingCopyJob swingCopyJob, SwingCopyJobException swingCopyJobException) {
        if (swingCopyJobException != null) {
            JOptionPane.showMessageDialog(null, "Cannot copy file: " + swingCopyJobException.getMessage(), "Can't proceed", JOptionPane.WARNING_MESSAGE);
        }
    }
    
    // Updates how many files and bytes have been copied
    private void updateTotalProgress(){      
        String totalBytesStr = FileSieve.gui.util.Utilities.readableFileSize(totalBytes);
        String bytesDoneStr = FileSieve.gui.util.Utilities.readableFileSize(bytesDone);
        copyScreen.progressTxt.setText("Copied "+filesDone+" of "+totalFiles+" files ("+
                bytesDoneStr+" of "+totalBytesStr+")");
    }

}
