package ru.itmo.mit.git;

import java.nio.file.Path;
import java.util.Objects;

public class Pair {
    private final Path path;
    private final String string;

    Pair(Path path, String string) {
        this.path = path;
        this.string = string;
    }

    Path getPath() {
        return path;
    }

    String getString() {
        return string;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Pair that = (Pair) o;

        return path != null ? path.equals(that.path) : (that.path == null && (Objects.equals(string, that.string)));
    }
}
