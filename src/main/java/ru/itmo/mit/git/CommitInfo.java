package ru.itmo.mit.git;

public class CommitInfo {
    private final String hash;
    private final String message;
    private final String author;
    private final String date;

    CommitInfo(Commit commit) {
        hash = commit.getHash();
        message = commit.getMessage();
        author = commit.getAuthor();
        date = commit.getDate().toString();
    }


    public String getHash() {
        return hash;
    }

    public String getMessage() {
        return message;
    }

    public String getAuthor() {
        return author;
    }

    public String getDate() {
        return date;
    }
}
