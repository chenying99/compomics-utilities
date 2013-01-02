package com.compomics.software;

import com.compomics.util.Util;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;

/**
 * This class groups some convenience methods for the use of compomics tools in
 * command line.
 *
 * @author Marc Vaudel
 */
public class CommandLineUtils {

    /**
     * The command line argument separator.
     */
    public static final String SEPARATOR = ",";

    /**
     * Returns the list of file as argument for the command line.
     *
     * @param files the files
     * @return the list of file as string for command line argument
     */
    public static String getCommandLineArgument(ArrayList<File> files) {
        String result = "";
        for (File file : files) {
            if (!result.equals("")) {
                result += SEPARATOR;
            }
            result += "\"" + file.getAbsolutePath() + "\""; // @TODO: same quotes on mac/linux?
        }
        return result;
    }

    /**
     * Returns the list of arguments as space separated string for the command
     * line.
     *
     * @param args the arguments
     * @return a comma separated string
     */
    public static String concatenate(ArrayList<String> args) {
        if (args == null) {
            return null;
        }
        String result = "";
        for (String arg : args) {
            if (!result.equals("")) {
                result += " ";
            }
            result += arg;
        }
        return result;
    }

    /**
     * Returns the list of arguments as space separated string for the command
     * line.
     *
     * @param args the arguments
     * @return a comma separated string
     */
    public static String concatenate(String[] args) {
        if (args == null) {
            return null;
        }
        String result = "";
        for (String arg : args) {
            if (!result.equals("")) {
                result += " ";
            }
            result += arg;
        }
        return result;
    }

    /**
     * Returns the file as argument for the command line.
     *
     * @param file the file
     * @return the list of file as string for command line argument
     */
    public static String getCommandLineArgument(File file) {
        return "\"" + file.getAbsolutePath() + "\""; // @TODO: same quotes on mac/linux?
    }

    /**
     * Returns a list of file names for inputs of comma separated files.
     *
     * @param cliInput the CLI input
     * @return a list of file names
     */
    public static ArrayList<String> splitInput(String cliInput) {
        ArrayList<String> results = new ArrayList<String>();
        for (String file : cliInput.split(SEPARATOR)) {
            results.add(file.trim());
        }
        return results;
    }

    /**
     * Returns a list of files as imported from the command line option.
     *
     * @param optionInput the command line option
     * @param fileExtentions the file extensions to be considered
     * @return a list of file candidates
     * @throws FileNotFoundException exception thrown whenever a file is not
     * found
     */
    public static ArrayList<File> getFiles(String optionInput, ArrayList<String> fileExtentions) throws FileNotFoundException {
        ArrayList<File> result = new ArrayList<File>();
        ArrayList<String> files = splitInput(optionInput);
        if (files.size() == 1) {
            File testFile = new File(files.get(0));
            if (testFile.exists()) {
                if (testFile.isDirectory()) {
                    for (File childFile : testFile.listFiles()) {
                        String fileName = Util.getFileName(childFile.getAbsolutePath());
                        for (String extention : fileExtentions) {
                            if (fileName.toLowerCase().endsWith(extention)) {
                                if (childFile.exists()) {
                                    result.add(childFile);
                                    break;
                                } else {
                                    throw new FileNotFoundException(childFile.getAbsolutePath() + " not found.");
                                }
                            }
                        }
                    }
                } else {
                    String fileName = Util.getFileName(testFile.getAbsolutePath());
                    for (String extention : fileExtentions) {
                        if (fileName.toLowerCase().endsWith(extention)) {
                            result.add(testFile);
                            break;
                        }
                    }
                }
            } else {
                throw new FileNotFoundException(files.get(0) + " not found.");
            }
        } else {
            for (String file : files) {
                for (String extention : fileExtentions) {
                    if (file.toLowerCase().endsWith(extention)) {
                        File testFile = new File(file);
                        if (testFile.exists()) {
                            result.add(testFile);
                        } else {
                            throw new FileNotFoundException(file + " not found.");
                        }
                        break;
                    }
                }
            }
        }
        return result;
    }
}