package vitaliy.gc.leak;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class UserGenerator implements Generate {

    public static final String PATH_NAMES = "src/main/vitaliy/gc/leak/files/names.txt";
    public static final String PATH_SURNAMES = "src/main/vitaliy/gc/leak/files/surnames.txt";
    public static final String PATH_PATRONS = "src/main/vitaliy/gc/leak/files/patr.txt";

    public static final String SEPARATOR = " ";
    public static final Integer NEW_USERS = 1000;
    private static final List<User> USERS = new ArrayList<>();
    public static List<String> names;
    public static List<String> surnames;
    public static List<String> patrons;
    private final Random random;

    public UserGenerator(Random random) {
        this.random = random;
        readAll();
    }

    public static List<User> getUsers() {
        return USERS;
    }

    @Override
    public void generate() {
        USERS.clear();
        for (int i = 0; i < NEW_USERS; i++) {
            USERS.add(new User(
                    surnames.get(random.nextInt(surnames.size())) + SEPARATOR
                            + names.get(random.nextInt(names.size())) + SEPARATOR
                            + patrons.get(random.nextInt(patrons.size()))));
        }
    }

    private void readAll() {
        try {
            names = read(PATH_NAMES);
            surnames = read(PATH_SURNAMES);
            patrons = read(PATH_PATRONS);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public User randomUser() {
        return USERS.get(random.nextInt(USERS.size()));
    }
}