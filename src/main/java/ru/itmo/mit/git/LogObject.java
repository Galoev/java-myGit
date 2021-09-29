package ru.itmo.mit.git;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LogObject {
     private final String branchName;
     private final List<CommitInfo> commits = new ArrayList<>();

     public LogObject(@NotNull List<Commit> commits, @NotNull String branchName) {
         this.commits.addAll(commits.stream().map(CommitInfo::new).collect(Collectors.toList()));
         this.branchName = branchName;
     }

    public String getBranchName() {
        return branchName;
    }

    public @NotNull List<CommitInfo> getCommits() {
        return commits;
    }
}
