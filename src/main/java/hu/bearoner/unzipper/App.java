package hu.bearoner.unzipper;

import hu.bearoner.unzipper.fileparser.FileParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Scanner;

public class App {
    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        LOG.info("The app started!");
        Scanner scanner = new Scanner(System.in);
        String filePath = null;
        while (!checkFilePath(filePath)) {
            if(filePath != null){
                System.out.println("This file does not exist!");
            }
            System.out.println("Enter zip file or folder with zip files:");
            filePath = scanner.next();
        }
        String shouldDelete = "";
        while (!shouldDelete.equalsIgnoreCase("Y") && !shouldDelete.equalsIgnoreCase("N")) {
            System.out.println("Should delete files after processing?(Y/N)");
            shouldDelete = scanner.next();
        }
        FileParser fileParser = new FileParser();
        try {
            fileParser.parseFiles(filePath, shouldDelete.equalsIgnoreCase("Y"));
        }catch (IllegalArgumentException e){
            LOG.error(e.getMessage());
        }

        LOG.info("The app stopped!");
    }

    private static boolean checkFilePath(String filePath) {
        if(filePath != null) {
            File file = new File(filePath);
            return file.exists();
        }
        return false;
    }

}
