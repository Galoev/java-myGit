package ru.itmo.mit.git;

import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static ru.itmo.mit.git.GitConstants.objectsDirectory;
import static ru.itmo.mit.git.GitObject.write;

public class Tree implements GitObject {
    private final List<String> children;
    private final String directoryName;
    private String hash;
    private final String root;

    Tree(@NotNull Path root, @NotNull String directoryName, @NotNull List<String> children) {
        this.root = root.toString();
        this.directoryName = directoryName;
        this.children = children;
        updateHash();
        try {
            write(this, root);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    private Tree(@NotNull Path root, @NotNull String directoryName) {
        this.root = root.toString();
        this.directoryName = directoryName;
        children = new ArrayList<>();
        updateHash();
        try {
            GitObject.write(this, root);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public Type getType() {
        return Type.TREE;
    }

    @Override
    public String getHash() {
        return hash;
    }

    public String getDirectoryName() {
        return directoryName;
    }

    private void updateHash() {
        StringBuilder content = new StringBuilder();
        content.append(directoryName);
        children.forEach(content::append);
        hash = DigestUtils.sha1Hex(content.toString().getBytes());
    }

    public Tree addPathToTree(@NotNull Path path, @NotNull String hash) throws IOException, ClassNotFoundException {
        if (path.getNameCount() == 1) {
            Blob blob = (Blob) getChild(hash);
            List<String> newChildren = new ArrayList<>();
            for (String childHash : children) {
                GitObject child = getChild(childHash);
                if (!child.getType().equals(Type.BLOB) || !((Blob) child).getFileName().equals(blob.getFileName())) {
                    newChildren.add(childHash);
                }
            }
            newChildren.add(hash);
            return new Tree(Paths.get(root), directoryName, newChildren);
        }

        List<String> newChildren = new ArrayList<>();
        boolean foundInTree = false;
        String directory = path.getName(0).toString();
        for (String childHash : children) {
            GitObject child = getChild(childHash);
            if (child.getType().equals(Type.TREE) &&
                    ((Tree) child).getDirectoryName().equals(directory)) {
                foundInTree = true;
                newChildren.add(((Tree) child)
                        .addPathToTree(path.subpath(1, path.getNameCount()), hash)
                        .getHash());
            } else {
                newChildren.add(childHash);
            }
        }
        if (!foundInTree) {
            newChildren.add(new Tree(Paths.get(root), directory)
                    .addPathToTree(path.subpath(1, path.getNameCount()), hash)
                    .getHash());
        }
        return new Tree(Paths.get(root), directoryName, newChildren);
    }

    public List<Pair> checkoutTree(@NotNull Path currentPath) throws IOException, ClassNotFoundException {
        List<Pair> files = new ArrayList<>();
        for (String childHash : children) {
            GitObject child = getChild(childHash);
            if (child.getType().equals(Type.BLOB)) {
                Path filePath = currentPath.resolve(((Blob) child).getFileName());
                try {
                    OutputStream outputStream = Files.newOutputStream(filePath);
                    outputStream.write(((Blob) child).getContent());
                    outputStream.close();
                    files.add(new Pair(filePath, childHash));
                } catch (IOException e) {
                    throw new IOException("IOException occurred while writing a file" + filePath.toString());
                }
            } else {
                Path nextDirectory = currentPath.resolve(((Tree) child).getDirectoryName());
                if (Files.notExists(nextDirectory)) {
                    try {
                        Files.createDirectory(nextDirectory);
                    } catch (IOException e) {
                        throw new IOException("IOException occurred while creating a directory " + nextDirectory.toString());
                    }
                }
                files.addAll(((Tree) child).checkoutTree(nextDirectory));
            }
        }
        return files;
    }

    public Blob getBlob(@NotNull String name) throws IOException, ClassNotFoundException {
        for (String childHash : children) {
            GitObject child = getChild(childHash);
            if (child.getType().equals(Type.BLOB)) {
                Blob blob = (Blob) child;
                if (blob.getFileName().equals(name)) {
                    return blob;
                }
            } else {
                return ((Tree) child).getBlob(name);
            }
        }
        return null;
    }

    public List<Pair> getPairs(@NotNull Path currentPath) throws IOException, ClassNotFoundException {
        List<Pair> files = new ArrayList<>();
        for (String childHash : children) {
            GitObject child = getChild(childHash);
            if (child.getType().equals(Type.BLOB)) {
                files.add(new Pair(currentPath.resolve(((Blob) child).getFileName()), childHash));
            } else {
                Path nextDirectory = currentPath.resolve(((Tree) child).getDirectoryName());
                if (Files.notExists(nextDirectory)) {
                    try {
                        Files.createDirectory(nextDirectory);
                    } catch (IOException e) {
                        throw new IOException("IOException occurred while creating a directory " + nextDirectory.toString());
                    }
                }
                files.addAll(((Tree) child).getPairs(nextDirectory));
            }
        }
        return files;
    }

    private GitObject getChild(String childHash) throws IOException, ClassNotFoundException {
        return GitObject.read(Paths.get(root).resolve(objectsDirectory).resolve(childHash));
    }
}
