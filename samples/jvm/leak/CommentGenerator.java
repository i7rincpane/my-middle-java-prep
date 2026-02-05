package vitaliy.gc.leak;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CommentGenerator implements Generate {

    public static final String PATH_PHRASES = "src/main/vitaliy/gc/leak/files/phrases.txt";

    public static final String SEPARATOR = System.lineSeparator();
    public static final Integer COUNT = 50;
    private static final List<Comment> COMMENTS = new ArrayList<>();
    private static List<String> phrases;

    private final UserGenerator userGenerator;

    private final Random random;

    public CommentGenerator(Random random, UserGenerator userGenerator) {
        this.userGenerator = userGenerator;
        this.random = random;
        read();
    }

    public static List<Comment> getComments() {
        return COMMENTS;
    }

    private void read() {
        try {
            phrases = read(PATH_PHRASES);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public void generate() {
        COMMENTS.clear();
        List<Integer> ints = new ArrayList<>();
        random.ints(0, phrases.size())
                .distinct().limit(3).forEach(ints::add);
        for (int i = 0; i < COUNT; i++) {
            String comment = phrases.get(ints.get(0)) + SEPARATOR
                    + phrases.get(ints.get(1)) + SEPARATOR
                    + phrases.get(ints.get(2));
            COMMENTS.add(new Comment(comment,
                    userGenerator.randomUser()));
        }
    }
}

