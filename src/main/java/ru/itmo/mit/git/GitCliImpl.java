package ru.itmo.mit.git;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static ru.itmo.mit.git.GitConstants.*;

public class GitCliImpl implements GitCli {
    private static Path directory;
    private static RepositoryManager repositoryManager;
    private static PrintStream outputStream = System.out;

    @Override
    public void runCommand(@NotNull String command, @NotNull List<@NotNull String> arguments) throws GitException {
        directory = Paths.get(System.getProperty("user.dir")).resolve("./playground/").normalize();

        if (command.equals(INIT)) {
            commandInit();
        } else if (repositoryManager == null) {
            getRepositoryManager();
        }

        switch (command) {
            case ADD:
                commandAdd(arguments);
                break;
            case COMMIT:
                commandCommit(arguments);
                break;
            case RM:
                commandRemove(arguments);
                break;
            case LOG:
                commandLog();
                break;
            case CHECKOUT:
                commandCheckout(arguments);
                break;
            case RESET:
                commandReset(arguments);
                break;
            case STATUS:
                commandStatusManager();
                break;
            case BRANCH_CREATE:
                commandBranchCreate(arguments);
                break;
            case BRANCH_REMOVE:
                commandBranchRemove(arguments);
                break;
            case SHOW_BRANCHES:
                commandShow();
                break;
            case MERGE:
                commandMerge(arguments);
        }
    }

    @Override
    public void setOutputStream(@NotNull PrintStream outputStream) {
        GitCliImpl.outputStream = outputStream;
    }

    @Override
    public @NotNull String getRelativeRevisionFromHead(int n) throws GitException {
        return repositoryManager.getRelativeRevisionFromHead(n);
    }

    private static void getRepositoryManager() {
        try {
            repositoryManager = RepositoryManager.getRepositoryManager(directory);
        } catch (IOException | ClassNotFoundException | GitException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    private static void commandInit() {
        try {
            RepositoryManager.initRepository(directory);
            outputStream.println("Project initialized");
        } catch (GitException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println("IOException occurred during initialization");
            e.printStackTrace();
        }
    }

    private static void commandAdd(@NotNull List<@NotNull String> arguments) {
        try {
            repositoryManager.add(getPath(arguments.get(0)));
            outputStream.println("Add completed successful");
        } catch (GitException | IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    private static void commandCommit(@NotNull List<@NotNull String> arguments) {
        try {
            repositoryManager.commit(arguments.get(0));
            outputStream.println("Files committed");
        } catch (IOException | ClassNotFoundException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }        
    }

    private static void commandCheckout(@NotNull List<@NotNull String> arguments) {
        try {
            if (arguments.get(0).equals("--")) {
                repositoryManager.checkoutFile(getPath(arguments.get(1)));
            } else {
                repositoryManager.checkout(arguments.get(0));
            }
            outputStream.println("Checkout completed successful");
        } catch (IOException | GitException | ClassNotFoundException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    private static void commandReset(@NotNull List<@NotNull String> arguments) {
        try {
            repositoryManager.reset(arguments.get(0));
            outputStream.println("Reset successful");
        } catch (IOException | ClassNotFoundException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    private static void commandRemove(@NotNull List<@NotNull String> arguments) {
        try {
            repositoryManager.remove(getPath(arguments.get(0)));
            outputStream.println("Rm completed successful");
        } catch (GitException | IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    private static void commandLog() {
        LogObject log = null;
        try {
            log = repositoryManager.log();
        } catch (IOException | ClassNotFoundException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        List<CommitInfo> commits = log.getCommits();
        for (int i = 0; i < commits.size(); i++) {
            CommitInfo commit = commits.get(i);
            outputStream.println("Commit " + (TEST_MODE ? "COMMIT_HASH" : commit.getHash()));
            outputStream.println("Author: " + (TEST_MODE ? "Test user" : commit.getAuthor()));
            outputStream.println("Date: " + (TEST_MODE ? "COMMIT_DATE" : commit.getDate()));
            outputStream.println();
            outputStream.println(commit.getMessage());
            if (i != (commits.size() - 1)) {
                outputStream.println();
            }
        }
    }

    private static void commandStatusManager() {
        Status status = null;
        try {
            status = repositoryManager.getStatus();
        } catch (IOException | ClassNotFoundException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }

        if (status == null) {
            outputStream.println("Error while performing status: Head is detached");
            return;
        }

        try {
            outputStream.println("Current branch is '" + repositoryManager.getCurrentBranchesName() + "'");
        } catch (ClassNotFoundException | IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }

        String stagedFiles = getFiles(status.getStagedFiles());
        String notStagedFiles = getFiles(status.getNotStagedFiles());
        String deletedFiles =  getFiles(status.getDeletedFiles());
        String missingFiles =  getFiles(status.getMissingFiles());
        String notTrackedFiles =  getFiles(status.getNotTrackedFiles());

        if (status.getStagedFiles().size() != 0) {
            outputStream.println("Ready to commit:");
            outputStream.println();
            outputStream.println("New files:");
            outputStream.println(stagedFiles);
            outputStream.println();
        }

        boolean existUntrackedFiles = false;
        if (status.getNotStagedFiles().size() != 0
                || status.getDeletedFiles().size() != 0
                || status.getMissingFiles().size() != 0
                || status.getNotTrackedFiles().size() != 0
        ) {
            existUntrackedFiles = true;
        }

        if (existUntrackedFiles) {
            outputStream.println("Untracked files:");
            outputStream.println();
            if (!notTrackedFiles.equals("")) {
                outputStream.println("New files:");
                outputStream.println(notTrackedFiles);
                outputStream.println();
            }
            if (!notStagedFiles.equals("")) {
                outputStream.println("Modified files:");
                outputStream.println(notStagedFiles);
                outputStream.println();
            }
            if (!deletedFiles.equals("")) {
                outputStream.println("Removed files:");
                outputStream.println(deletedFiles);
                outputStream.println(missingFiles);
            }
        }

        if (status.getStagedFiles().size() == 0 && !existUntrackedFiles) {
            outputStream.println("Everything up to date");
        }
    }

    private static String getFiles(@NotNull Set<Path> files) {
        return files.stream()
                .map(directory::relativize)
                .map(Path::toString)
                .sorted()
                .map(s -> "    " + s)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    public static void commandBranchCreate(@NotNull List<@NotNull String> arguments) {
        try {
            String name = arguments.get(0);
            repositoryManager.createBranch(name);
            outputStream.println("Branch " + name + " created successfully");
            outputStream.println("You can checkout it with 'checkout " + name + "'");
        } catch (IOException | GitException | ClassNotFoundException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }

    }

    public static void commandBranchRemove(@NotNull List<@NotNull String> arguments) {
        try {
            repositoryManager.removeBranch(arguments.get(0));
            outputStream.println("Branch " + arguments.get(0) + " removed successfully");
        } catch (GitException | ClassNotFoundException | IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    public static void commandShow() {
        outputStream.println("Available branches:");
        repositoryManager.getBranches().stream().filter(name -> !name.equals("~")).forEach(name -> outputStream.println(name));
    }

    public static void commandMerge(@NotNull List<@NotNull String> arguments) {
        try {
            repositoryManager.merge(arguments.get(0));
        } catch (IOException | ClassNotFoundException | GitException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    private static Path getPath(@NotNull String path) {
        return directory.resolve(path).toAbsolutePath().normalize();
    }
}
