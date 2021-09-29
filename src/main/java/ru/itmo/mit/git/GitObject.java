package ru.itmo.mit.git;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

import static ru.itmo.mit.git.GitConstants.*;

public interface GitObject extends Serializable {
    enum Type {
        BLOB,
        TREE,
        COMMIT,
        BRANCH
    }

    Type getType();

    String getHash();

    static void write(@NotNull GitObject gitObject, @NotNull Path path) throws IOException {
        try {
            OutputStream fileOutputStream;
            if (gitObject.getType().equals(Type.BRANCH)) {
                fileOutputStream = Files.newOutputStream(path.resolve(branchesDirectory).resolve(((Branch) gitObject).getName()));
            } else {
                fileOutputStream = Files.newOutputStream(path.resolve(objectsDirectory).resolve(gitObject.getHash()));
            }
            if (GitConstants.DEBUG_MODE) debugOutput(gitObject, path);
            ObjectOutputStream outputStream = new ObjectOutputStream(fileOutputStream);
            outputStream.writeObject(gitObject);
            outputStream.close();
            fileOutputStream.close();
        } catch (IOException e) {
            throw new IOException("IOException occurred while writing the object" + path);
        }
    }

    static @NotNull GitObject read(@NotNull Path path) throws IOException, ClassNotFoundException {
        try {
            InputStream fileInputStream = Files.newInputStream(path);
            ObjectInputStream inputStream = new ObjectInputStream(fileInputStream);
            GitObject object = (GitObject) inputStream.readObject();
            inputStream.close();
            fileInputStream.close();
            return object;
        } catch (IOException e) {
            throw new IOException("IOException occurred while reading the object: " + path);
        } catch (ClassNotFoundException e) {
            throw new ClassNotFoundException("ClassNotFoundException occurred while reading the object: " + path);
        }
    }

    static void debugOutput(GitObject gitObject, Path path) {
        switch (gitObject.getType()) {
            case BRANCH:
                System.out.println("WRITE:" + path + " HASH: " + gitObject.getHash() + " BRANCH: " + ((Branch) gitObject).getName() + " COMMIT HASH: " + ((Branch) gitObject).getCommitHash());
                break;
            case TREE:
                System.out.println("WRITE:" + path + " HASH: " + gitObject.getHash() + " TREE: " + ((Tree) gitObject).getDirectoryName());
                break;
            case BLOB:
                System.out.println("WRITE:" + path + " HASH: " + gitObject.getHash() + " BLOB: " + ((Blob) gitObject).getFileName());
                break;
            case COMMIT:
                System.out.println("WRITE:" + path + " HASH: " + gitObject.getHash() + " COMMIT: " + ((Commit) gitObject).getMessage() + " TREE: " + ((Commit) gitObject).getTree().getHash());
                break;
        }
    }
}
