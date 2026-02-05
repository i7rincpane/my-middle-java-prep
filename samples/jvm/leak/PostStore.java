package vitaliy.gc.leak;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class PostStore {

    private static final Map<Integer, Post> POSTS = new HashMap<>();

    public AtomicInteger atomicInteger = new AtomicInteger(1);

    public static Collection<Post> getPosts() {
        return POSTS.values();
    }

    public Post add(Post post) {
        Integer id = atomicInteger.getAndIncrement();
        post.setId(id);
        POSTS.put(id, post);
        return post;
    }

    public void removeAll() {
        POSTS.clear();
    }
}