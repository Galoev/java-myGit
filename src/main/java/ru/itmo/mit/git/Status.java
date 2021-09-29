package ru.itmo.mit.git;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Status {
    private final @NotNull
Set<Path> notTrackedFiles = new HashSet<>();
    private final @NotNull
Set<Path> committedFiles = new HashSet<>();
    private final @NotNull
Set<Path> stagedFiles = new HashSet<>();
    private final @NotNull
Set<Path> notStagedFiles = new HashSet<>();
    private final @NotNull
Set<Path> deletedFiles = new HashSet<>();
    private final @NotNull
Set<Path> missingFiles = new HashSet<>();

    public void addCommittedFiles(Path... paths) {
        Collections.addAll(committedFiles, paths);        
    }

    public void addStagedFiles(Path... paths) {
        Collections.addAll(stagedFiles, paths);        
    }

    public void addNotStagedFiles(Path... paths) {
        Collections.addAll(notStagedFiles, paths);        
    }

    public void addDeletedFiles(Path... paths) {
        Collections.addAll(deletedFiles, paths);        
    }

    public void addNotTrackedFiles(Path... paths) {
        Collections.addAll(notTrackedFiles, paths);        
    }

    public void addMissingFiles(Path... paths) {
        Collections.addAll(missingFiles, paths);        
    }

    @NotNull
    Set<Path> getNotTrackedFiles() {
        return notTrackedFiles;
    }

    @NotNull
    Set<Path> getNotStagedFiles() {
        return notStagedFiles;
    }

    @NotNull
    Set<Path> getStagedFiles() {
        return stagedFiles;
    }

    @NotNull
    Set<Path> getCommittedFiles() {
        return committedFiles;
    }

    @NotNull
    Set<Path> getDeletedFiles() {
        return deletedFiles;
    }

    @NotNull
    Set<Path> getMissingFiles() {
        return missingFiles;
    }
}

