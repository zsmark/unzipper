package hu.bearoner.unzipper.fileparser;


import com.github.junrar.extract.ExtractArchive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileParser {
    private static final Logger LOG = LoggerFactory.getLogger(FileParser.class);

    public void parseFiles(String rootFolderPath, boolean delete, boolean shouldCopy) throws IllegalArgumentException {
        if (rootFolderPath != null && !rootFolderPath.isEmpty()) {
            File root = new File(rootFolderPath);
            LOG.info("The root file is: " + rootFolderPath);
            if (root.exists()) {
                if (root.isDirectory()) {
                    List<File> filesToUnzip = getFilesInSubfolders(root, isFileProcessable());
                    //unzip files if there are any
                    filesToUnzip.stream().filter(file -> isFileZip().test(file.getName())).forEach(file -> {
                        unzipFile(file,root,shouldCopy);
                        deleteFile(file, delete);
                    });
                    //merge files if there any splitted
                    processRarFiles(filesToUnzip.stream().filter(this::isFileSplittedOrRar).collect(Collectors.toList()), delete,shouldCopy,root);
                } else if (root.isFile() && isFileZip().test(root.getName())) {
                    unzipFile(root, root, shouldCopy);
                    deleteFile(root, delete);
                } else {
                    throw new IllegalArgumentException("This file is not a folder or zip file!");
                }
            } else {
                throw new IllegalArgumentException("This file does not exist!");
            }
        } else {
            throw new IllegalArgumentException("You most add a root folder or file!");
        }
    }

    private void processRarFiles(List<File> splittedFiles, boolean deleteFile, boolean shouldCopy, File root) {
        LOG.info("Start rarfiles processing!");
        Map<String, List<File>> rarFilesMap = groupFilesByName(splittedFiles);
        ExtractArchive extractArchive = new ExtractArchive();
        for (List<File> fileList : rarFilesMap.values()) {
            File rar = fileList.stream().filter(file -> file.getName().contains(".rar")).findAny().orElse(null);
            if (rar != null) {
                File parentFolder = shouldCopy && root.isDirectory() ? root : rar.getParentFile();
                LOG.info("Process file: " + rar.getName());
                extractArchive.extractArchive(rar, parentFolder);
                fileList.forEach(file -> deleteFile(file, deleteFile));
            } else {
                if (fileList.size() > 0) {
                    LOG.error("In this folder cannot merge rip files: " + fileList.get(0).getPath());
                }else{
                    LOG.error("Error while processing!");
                }
            }
        }
        LOG.info("Processing ended!");
    }
/*
    private File mergeSplittedFiles(List<File> filesForMerge) {
        File rar = filesForMerge.stream().filter(file -> file.getName().contains(".rar")).findAny().orElse(null);
        if (rar != null) {
            File output = new File(rar.getParent() + "/temp_" +  rar.getName());
            new File(output.getParent()).mkdirs();
            try (FileOutputStream fos = new FileOutputStream(output, true)) {
                for (File file : filesForMerge.stream().filter(file -> !file.getName().equals(rar.getName())).sorted(Comparator.comparing(File::getName)).collect(Collectors.toList())) {
                    byte[] fileBytes = new byte[(int) file.length()];
                    try(FileInputStream fis = new FileInputStream(file)) {
                        byte[] buff = new byte[8000];
                        int bytesRead;
                        while ( (bytesRead = fis.read(buff)) > 0){
                            fos.write(buff, 0, bytesRead);
                        }
                    }
//                    int bytesRead = fis.read(fileBytes, 0, (int) file.length());
                    
                    fos.write(fileBytes);
                    fos.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return output;
        }
        return null;
    }*/


    private Map<String, List<File>> groupFilesByName(List<File> splittedFiles) {
        Map<String, List<File>> splittedFilesMap = new HashMap<>();
        for (File file : splittedFiles) {
            String fileName = file.getName().substring(0, file.getName().lastIndexOf(".") - 1);
            if (splittedFilesMap.containsKey(fileName)) {
                List<File> filesForMerge = splittedFilesMap.get(fileName);
                filesForMerge.add(file);
            } else {
                List<File> filesForMerge = new ArrayList<>();
                filesForMerge.add(file);
                splittedFilesMap.put(fileName, filesForMerge);
            }
        }
        return splittedFilesMap;
    }

    private void deleteFile(File file, boolean deleteZipFile) {
        if (deleteZipFile) {
            String fileName = file.getName();
            if (file.delete()) {
                LOG.info("File deleted: " + fileName);
            }
        }
    }

    private List<File> getFilesInSubfolders(File file, Predicate<String> predicate) {
        List<File> files = new ArrayList<>();
        if (file != null && Objects.nonNull(file.listFiles())) {
            for (File subFile : file.listFiles()) {
                if (!subFile.isDirectory() && predicate.test(subFile.getName())) {
                    LOG.info("File added to list: " + subFile.getName());
                    files.add(subFile);
                } else {
                    LOG.info("Opening folder: " + subFile.getAbsolutePath());
                    files.addAll(getFilesInSubfolders(subFile, predicate));
                }
            }
        }
        return files;
    }

    private boolean isFileSplittedOrRar(File subFile) {
        return subFile.getName().substring(subFile.getName().lastIndexOf(".") + 1, subFile.getName().length()).matches("[a-z]\\d{2,3}") || subFile.getName().contains(".rar");
    }

    private Predicate<String> isFileProcessable() {
        return s -> s.substring(s.lastIndexOf(".") + 1, s.length()).matches("[a-z]\\d{2,3}") || s.contains(".rar") || s.contains(".zip");
    }

    private Predicate<String> isFileZip() {
        return s -> s.contains(".zip");
    }

    private void unzipFile(File file, File root, boolean shouldCopy) {
        LOG.info("Starts unzipping!");
        byte[] buffer = new byte[1024];

        try (FileInputStream fis = new FileInputStream(file)) {
            try (ZipInputStream zis = new ZipInputStream(fis)) {
                for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
                    String fileName = entry.getName();
                    LOG.info("Unzipping file: " + fileName);
                    String parentPath =  shouldCopy && root.isDirectory() ? root.getAbsolutePath() : file.getParent();
                    File newFile = new File(parentPath + File.separator + fileName);
                    new File(newFile.getParent()).mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                        LOG.info("File unzipped: " + fileName);
                    }
                }
            }
        } catch (java.io.IOException e) {
            LOG.error("Cannot open this file: " + file.getName());
        }
        LOG.info("Unzipping ended!");
    }

}
