package ru.itmo.mit.git;

import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Branch implements GitObject {

    private final String root;
    private String hash;
    private final String name;
    private String commit;

    Branch(@NotNull Path root, @NotNull String name, @NotNull String commit) {
        this.root = root.toString();
        this.name = name;
        this.commit = commit;
        updateHash();
        try {
            GitObject.write(this, root);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }


    @Override
    public Type getType() {
        return Type.BRANCH;
    }

    @Override
    public String getHash() {
        return hash;
    }

    String getName() {
        return name;
    }

    String getCommitHash() {
        return commit;
    }

    void setCommit(@NotNull String commit) throws IOException {
        this.commit = commit;
        updateHash();
        GitObject.write(this, Paths.get(root));
    }

    private void updateHash() {
        hash = DigestUtils.sha1Hex((name + commit).getBytes());
    }
}
