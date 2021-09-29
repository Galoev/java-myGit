package ru.itmo.mit.git;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class GitConstants {
    private GitConstants() {}

    public static final @NotNull String INIT = "init";
    public static final @NotNull String COMMIT = "commit";
    public static final @NotNull String RESET = "reset";
    public static final @NotNull String LOG = "log";
    public static final @NotNull String CHECKOUT = "checkout";
    public static final @NotNull String STATUS = "status";
    public static final @NotNull String ADD = "add";
    public static final @NotNull String RM = "rm";
    public static final @NotNull String BRANCH_CREATE = "branch-create";
    public static final @NotNull String BRANCH_REMOVE = "branch-remove";
    public static final @NotNull String SHOW_BRANCHES = "show-branches";
    public static final @NotNull String MERGE = "merge";

    public static final @NotNull String MASTER = "master";

    public static final @NotNull Path myGitDirectory = Paths.get(".mygit");
    public static final @NotNull Path objectsDirectory = myGitDirectory.resolve("objects");
    public static final @NotNull Path branchesDirectory = myGitDirectory.resolve("branches");
    public static final @NotNull Path index = myGitDirectory.resolve("index");
    public static final @NotNull Path head = myGitDirectory.resolve("HEAD");

    public static final boolean TEST_MODE = true;
    public static final boolean DEBUG_MODE = false;
}
