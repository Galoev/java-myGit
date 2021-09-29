package ru.itmo.mit.git;

import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static ru.itmo.mit.git.GitConstants.*;

public class Commit implements GitObject, Comparable<Commit> {
    private final String root;
    private final String message;
    private final String author;
    private final Date date;
    private final List<String> parents;
    private final Tree tree;
    private String hash;

    private Commit(@NotNull Path root, @NotNull String message, @NotNull String author, @NotNull Date date, @NotNull List<String> parents, @NotNull Tree tree) {
        this.root = root.toString();
        this.message = message;
        this.author = author;
        this.date = date;
        this.parents = parents;
        this.tree = tree;
        updateHash();
        try {
            GitObject.write(this, root);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    Commit(@NotNull Path root, @NotNull String message, @NotNull List<String> parents, @NotNull Tree tree) {
        this(root, message, System.getProperty("user.name"), new Date(), parents, tree);
    }

    Commit(@NotNull Path root, @NotNull String message, @NotNull List<String> parents) {
        this(root, message, System.getProperty("user.name"), new Date(), parents, new Tree(root, root.getName(root.getNameCount() - 1).toString(), new ArrayList<>()));
    }

    @Override
    public int compareTo(@NotNull Commit commit) {
        return this.getDate().compareTo(commit.getDate());
    }

    @Override
    public Type getType() {
        return Type.COMMIT;
    }

    @Override
    public String getHash() {
        return hash;
    }

    public String getMessage() {
        return message;
    }

    public String getAuthor() {
        return author;
    }

    public Date getDate() {
        return date;
    }

    public Tree getTree() {
        return tree;
    }

    public List<String> getParents() {
        return parents;
    }

    List<Commit> getLog() throws IOException, ClassNotFoundException {
        List<Commit> result = new ArrayList<>();
        result.add(this);
        for (String hashParent : parents) {
            Commit parent = (Commit) GitObject.read(Paths.get(root).resolve(objectsDirectory).resolve(hashParent));
            result.addAll(parent.getLog());
        }
        return result;
    }

    private void updateHash() {
        StringBuilder content = new StringBuilder();
        content.append(message);
        content.append(author);
        content.append(date);
        content.append(parents);
        content.append(tree.getHash());
        parents.forEach(content::append);
        hash = DigestUtils.sha1Hex(content.toString().getBytes());
    }
}
