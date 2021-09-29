package ru.itmo.mit.git;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static ru.itmo.mit.git.GitConstants.*;

public class RepositoryManager {
    private enum HeadType { COMMIT, BRANCH }

    private final Path root;
    private final List<Branch> branches = new ArrayList<>();

    private RepositoryManager(@NotNull Path path){
        root = path;
    }

    public static void initRepository(@NotNull Path path) throws GitException, IOException {
        if (Files.exists(path.resolve(myGitDirectory))) {
            throw new GitException("Repository already exists");
        }

        Files.createDirectory(path.resolve(myGitDirectory));
        Files.createDirectory(path.resolve(objectsDirectory));
        Files.createDirectory(path.resolve(branchesDirectory));
        Files.createFile(path.resolve(index));
        Files.createFile(path.resolve(head));


        RepositoryManager repositoryManager = new RepositoryManager(path);
        repositoryManager.initialCommit();
    }

    private void initialCommit() throws IOException {
        Commit commit = new Commit(root, "Initial commit", new ArrayList<>());
        Branch masterBranch = new Branch(root, "master", commit.getHash());
        branches.add(masterBranch);
        writeToHead(masterBranch);
    }

    public static RepositoryManager getRepositoryManager(@NotNull Path path) throws IOException, ClassNotFoundException, GitException {
        Path myGitDir = path.resolve(myGitDirectory);
        Path objDir = path.resolve(objectsDirectory);
        Path branchesDir = path.resolve(branchesDirectory);
        Path indexFile = path.resolve(index);
        Path headFile = path.resolve(head);
        if (Files.notExists(myGitDir) || !Files.isDirectory(myGitDir)) {
            throw new GitException("Repository was not initialized");
        }
        if (Files.notExists(objDir) || !Files.isDirectory(objDir)
                || Files.notExists(branchesDir) || !Files.isDirectory(branchesDir)
                || Files.notExists(indexFile) || Files.isDirectory(indexFile)
                || Files.notExists(headFile) || Files.isDirectory(headFile)){
            throw new GitException("MyGit files are broken");
        }
        RepositoryManager repositoryManager = new RepositoryManager(path);
        List<Path> paths = Files.walk(branchesDir).collect(Collectors.toList());
        for (Path file : paths) {
            if (!Files.isDirectory(file)) {
                Branch branch = (Branch) GitObject.read(file);
                repositoryManager.addBranch(branch);
            }
        }
        return repositoryManager;
    }

    public void add(@NotNull Path path) throws GitException, IOException {
        if (!Files.exists(path)) {
            throw new GitException("File doesn't exist" + path);
        }

        Blob blob;
        blob = new Blob(root, Files.readAllBytes(path), path.getFileName().toString());
        List<String> lines = Files.readAllLines(getIndex());
        String hash = blob.getHash();
        StringBuilder file = new StringBuilder();
        for (String line: lines) {
            String[] strings = line.split(" ");
            if (strings[0].equals(path.toString())) {
                continue;
            }
            file.append(line).append("\n");
        }
        file.append(path).append(" ").append(hash).append("\n");

        OutputStream outputStream = Files.newOutputStream(getIndex());
        outputStream.write(file.toString().getBytes());
        outputStream.close();
    }

    public void commit(@NotNull String message) throws IOException, ClassNotFoundException {
        List<String> lines;
        try {
            lines = Files.readAllLines(getIndex());
        } catch (IOException e) {
            throw new IOException("IOException occurred while reading the Index file:" + getIndex().toString());
        }

        List<Pair> pathsAndHashes = new ArrayList<>();
        for (String line : lines) {
            String[] strings = line.split(" ");
            pathsAndHashes.add(new Pair(Paths.get(strings[0]), strings[1]));
        }
        Tree tree = buildCommitTree(pathsAndHashes);
        List<String> parents = new ArrayList<>();
        Branch branch = getHeadBranch();
        parents.add(getHeadCommit().getHash());
        removeFromBranches(branch);
        Commit commit = new Commit(root, message, parents, tree);
        branch.setCommit(commit.getHash());
        writeToHead(commit.getHash());
        clearIndex();
        branches.add(branch);
        if (DEBUG_MODE) debugOutput();
    }

    private void removeFromBranches(@NotNull Branch branch) {
        branches.removeIf(b -> b.getName().equals(branch.getName()));
    }

    private Tree buildCommitTree(@NotNull List<Pair> pathsAndHashes) throws IOException, ClassNotFoundException {
        Tree tree = getHeadCommit().getTree();
        for (Pair pair : pathsAndHashes) {
            tree = tree.addPathToTree(root.relativize(pair.getPath()), pair.getString());
        }
        return tree;
    }

    private void clearIndex() throws IOException {
        try {
            OutputStream outputStream = Files.newOutputStream(getIndex());
            outputStream.write(new byte[0]);
            outputStream.close();
        } catch (IOException e) {
            throw new IOException("IOException occurred while cleaning the Index file:" + getIndex().toString());
        }
    }

    public LogObject log() throws IOException, ClassNotFoundException {
        Commit lastCommit = (Commit) GitObject.read(getObjectsDir().resolve(getHeadBranch().getCommitHash()));
        List<Commit> commitsInLog = lastCommit.getLog();
        List<Commit> uniqueCommits = new ArrayList<>();
        Set<String> hashes = new HashSet<>();
        for (Commit commit : commitsInLog) {
            if (!hashes.contains(commit.getHash())) {
                uniqueCommits.add(commit);
            }
            hashes.add(commit.getHash());
        }
        uniqueCommits.sort(Comparator.reverseOrder());
        return new LogObject(uniqueCommits, getCurrentBranchesName());
    }

    public Status getStatus() throws IOException, ClassNotFoundException {
        if (getHeadBranch().getName().equals("~")) {
            return null;
        }
        Status status = new Status();
        fillStatusInDir(status, root);
        getRemovedFiles(status);

        return status;
    }

    private void fillStatusInDir(Status status, Path folder) throws IOException, ClassNotFoundException {
        List<Path> folderFiles = getRootFiles(folder);

        for (Path folderFile : folderFiles) {
            if (Files.isDirectory(folderFile)) {
                fillStatusInDir(status, folderFile);
            } else {
                String blobHash = Blob.getFileHash(folderFile);
                if (!Blob.exist(blobHash, getObjectsDir())) {
                    addNotBlobbedFile(status, folderFile);
                } else {
                    addBlobbedFile(status, folderFile);
                }
            }
        }
    }

    private void getRemovedFiles(Status status) throws IOException, ClassNotFoundException {
        List<Pair> files = getHeadCommit().getTree().getPairs(root);
        for (Pair pair : files) {
            Path filePath = pair.getPath();
            boolean fileIsRemovedFromDisk = Files.notExists(filePath);
            boolean fileIsNotInIndex = getFileHashInIndex(filePath) == null;

            if (fileIsRemovedFromDisk && fileIsNotInIndex) {
                status.addDeletedFiles(filePath);
            } else if (fileIsRemovedFromDisk) {
                status.addMissingFiles(filePath);
            }
        }
    }

    private void addNotBlobbedFile(Status status, Path folderFile) throws IOException, ClassNotFoundException {
        if (fileExistInCurrentCommit(folderFile)) {
            status.addNotStagedFiles(folderFile);
        } else {
            status.addNotTrackedFiles(folderFile);
        }
    }

    private void addBlobbedFile(Status status, Path folderFile) throws IOException, ClassNotFoundException {
        String indexHash = getFileHashInIndex(folderFile);
        String commitHash = getFileHashInCommit(folderFile);

        if (indexHash != null) {
            if (indexHash.equals(commitHash)) {
                status.addCommittedFiles(folderFile);
            } else {
                status.addStagedFiles(folderFile);
            }
        } else if (commitHash == null) {
            status.addNotTrackedFiles(folderFile);
        }
    }

    private String getFileHashInIndex(Path folderFile) throws IOException {
        List<String> lines = Files.readAllLines(getIndex());
        String fileName = root.resolve(folderFile).normalize().toString();
        for (String line: lines) {
            String[] strings = line.split(" ");
            if (strings[0].equals(fileName)) {
                return strings[1];
            }
        }
        return null;
    }

    private String getFileHashInCommit(Path folderFile) throws IOException, ClassNotFoundException {
        Blob blob = getHeadCommit().getTree().getBlob(folderFile.getFileName().toString());
        if (blob != null) {
            return blob.getHash();
        }
        return null;
    }

    private boolean fileExistInCurrentCommit(Path folderFile) throws IOException, ClassNotFoundException {
        return getHeadCommit().getTree().getBlob(folderFile.getFileName().toString()) != null;
    }

    private @NotNull List<Path> getRootFiles(Path folder) throws IOException {
        Path myGit = root.resolve(myGitDirectory);
        return Files.list(folder)
                .filter(f -> {
                    try {
                        return !Files.isSameFile(f, myGit);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    public void remove(@NotNull Path path) throws GitException, IOException {
        if (!path.startsWith(root)) {
            throw new GitException("Wrong directory");
        }
        removeFromIndex(path);
    }

    private void removeFromIndex(@NotNull Path path) throws IOException {
        List<String> lines;
        try {
            lines = Files.readAllLines(getIndex());
        } catch (IOException e) {
            throw new IOException("IOException occurred while reading the Index file:" + getIndex().toString());
        }

        StringBuilder file = new StringBuilder();
        for (String line: lines) {
            String[] strings = line.split(" ");
            if (strings[0].equals(path.toString())) {
                continue;
            }
            file.append(line).append("\n");
        }

        try {
            OutputStream outputStream = Files.newOutputStream(getIndex());
            outputStream.write(file.toString().getBytes());
            outputStream.close();
        } catch (IOException e) {
            throw new IOException("IOException occurred while deleting file from the Index file:" + getIndex().toString());
        }
    }

    public void reset(@NotNull String name) throws IOException, ClassNotFoundException {
        Branch branch = getHeadBranch();
        branch.setCommit(checkoutCommit(name).getHash());
        writeToHead(branch);
        clearIndex();
    }

    public void checkout(@NotNull String name) throws GitException, IOException, ClassNotFoundException {
        Branch branch = getBranch(name);
        if (branch == null) {
            if (Files.notExists(getObjectsDir().resolve(name))) {
                throw new GitException("Checkout " + name + " failed because such commit or branch doesn't exist\n");
            }
            branch = new Branch(root, "~", name);
            branches.add(branch);
        }
        removeFiles(getHeadCommit().getTree());
        removeFromBranches(branch);
        checkoutCommit(branch.getCommitHash());
        writeToHead(branch);
        branches.add(branch);
        clearIndex();
    }

    public void checkoutFile(@NotNull Path filesToCheckout) throws IOException, ClassNotFoundException {
        Blob blob = getHeadCommit().getTree().getBlob(filesToCheckout.getFileName().toString());
        Files.write(filesToCheckout, blob.getContent());
        removeFromIndex(filesToCheckout);
    }

    private Commit checkoutCommit(@NotNull String hash) throws IOException, ClassNotFoundException {
        Commit commit = (Commit)GitObject.read(getObjectsDir().resolve(hash));
        Tree tree = commit.getTree();
        removeFiles(tree);
        List<Pair> files = tree.checkoutTree(root);
        writePairsToIndex(files);
        return commit;
    }

    private void removeFiles(Tree tree) throws IOException, ClassNotFoundException {
        for (Pair pair : tree.getPairs(root)) {
            Path targetFile = root.resolve(pair.getPath());
            Files.deleteIfExists(targetFile);
            removeFromIndex(targetFile);
        }
    }

    public void createBranch(@NotNull String name) throws GitException, IOException, ClassNotFoundException {
        if (getBranch(name) != null) {
            throw new GitException("Branch already exists");
        }
        Branch branch = new Branch(root, name, getHeadCommit().getHash());
        branches.add(branch);
        checkoutCommit(branch.getCommitHash());
        writeToHead(branch);
        clearIndex();
    }

    public void removeBranch(@NotNull String name) throws IOException, ClassNotFoundException, GitException {
        if (getHeadBranch().getName().equals(name)) {
            throw new GitException("Cannot delete branch '" + name + "'");
        }
        Branch branch = getBranch(name);
        try {
            Files.deleteIfExists(getBranchesDir().resolve(name));
        } catch (IOException e) {
            throw new IOException("IOException occurred while deleting the " + getBranchesDir().resolve(name).toString());
        }
        if (branch != null) {
            branches.remove(branch);
        }
    }

    public @NotNull List<@NotNull String> getBranches() {
        return branches.stream().map(Branch::getName).collect(Collectors.toList());
    }

    public void merge(@NotNull String name) throws IOException, ClassNotFoundException, GitException {
        Branch curBranch = getHeadBranch();
        Branch secBranch = getBranch(name);
        if (secBranch == null) {
            throw new GitException("Merging is not possible because branch doesn't exist");
        }
        if (curBranch.getName().equals(secBranch.getName())) {
            throw new GitException("Merging is not possible because it is the same branch");
        }
        Commit curCommit = getHeadCommit();
        Commit secCommit = (Commit) GitObject.read(getObjectsDir().resolve(secBranch.getCommitHash()));

        List<String> parents = new ArrayList<>();
        parents.add(curCommit.getHash());
        parents.add(secCommit.getHash());

        List<Pair> files1 = curCommit.getTree().checkoutTree(root);
        List<Pair> files2 = secCommit.getTree().checkoutTree(root);

        Set<Path> filePaths = files1.stream()
                .map(Pair::getPath)
                .collect(Collectors.toSet());
        files1.addAll(files2.stream()
                .filter(p -> !filePaths.contains(p.getPath()))
                .collect(Collectors.toList()));

        Tree newCommitTree = buildCommitTree(files1);
        Commit newCommit = new Commit(root, "Merge branch '" + name + "' into '" + curBranch.getName() + "'", parents, newCommitTree);
        curBranch.setCommit(newCommit.getHash());
        writeToHead(newCommit.getHash());
        writePairsToIndex(files1);
    }

    private void writePairsToIndex(@NotNull List<Pair> files) throws IOException {
        try {
            OutputStream outputStream = Files.newOutputStream(getIndex());
            for (Pair pair : files) {
                outputStream.write((pair.getPath().toString() + " " + pair.getString() + "\n").getBytes());
            }
            outputStream.close();
        } catch (IOException e) {
            throw new IOException("IOException occurred while writing a pair to a Index file");
        }
    }


    private @Nullable Branch getBranch(@NotNull String name) {
        for (Branch branch : branches) {
            if (branch.getName().equals(name)) {
                return branch;
            }
        }
        return null;
    }

    public String getCurrentBranchesName() throws ClassNotFoundException, IOException {
        return getHeadBranch().getName();
    }

    private Commit getHeadCommit() throws IOException, ClassNotFoundException {
        return (Commit) readFromHead(HeadType.COMMIT);
    }

    private Branch getHeadBranch() throws IOException, ClassNotFoundException {
        return (Branch) readFromHead(HeadType.BRANCH);
    }

    private GitObject readFromHead(HeadType type) throws IOException, ClassNotFoundException {
        List<String> lines = Files.readAllLines(getHead());

        if (type.equals(HeadType.BRANCH)) {
            return GitObject.read(getBranchesDir().resolve(lines.get(0)));
        } else {
            return GitObject.read(getObjectsDir().resolve(lines.get(1)));
        }
    }

    private void writeToHead(@NotNull Branch branch) throws IOException {
        try {
            OutputStream outputStream = Files.newOutputStream(getHead());
            outputStream.write((branch.getName() + "\n").getBytes());
            outputStream.write((branch.getCommitHash() + "\n").getBytes());
            outputStream.close();
        } catch (IOException e) {
            throw new IOException("IOException occurred while reading branch from the head");
        }
    }

    private void writeToHead(@NotNull String commitHash) throws IOException, ClassNotFoundException {
        String name = getCurrentBranchesName();
        try {
            OutputStream outputStream = Files.newOutputStream(getHead());
            outputStream.write((name + "\n").getBytes());
            outputStream.write((commitHash + "\n").getBytes());
            outputStream.close();
        } catch (IOException e) {
            throw new IOException("IOException occurred while reading commit from the head");
        }
    }

    public @NotNull String getRelativeRevisionFromHead(int n) throws GitException {
        try {
            Commit commit = (Commit) readFromHead(HeadType.COMMIT);
            for (int i = 0; i < n; i++) {
                commit = (Commit) GitObject.read(getObjectsDir().resolve(commit.getParents().get(0)));
            }
            return commit.getHash();
        } catch (IOException e) {
            throw new GitException("IOException:" + e.getMessage());
        } catch (ClassNotFoundException e) {
            throw new GitException("ClassNotFoundException:" + e.getMessage());
        }
    }

    private void addBranch(@NotNull Branch branch) {
        branches.add(branch);
    }

    private Path getObjectsDir() {
        return root.resolve(objectsDirectory);
    }

    private Path getBranchesDir() {
        return root.resolve(branchesDirectory);
    }

    private Path getIndex() {
        return root.resolve(index);
    }

    private Path getHead() {
        return root.resolve(head);
    }

    private void debugOutput() {
        System.out.println();
        System.out.println("LIST branches:");
        for (Branch b : branches) {
            System.out.print(b.getName());
            System.out.println(" " + b.getCommitHash());
        }
        System.out.println();
    }
}
