package se.lars;


import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class DefaultHttpThreadFactory implements ThreadFactory {
    private final String namePrefix;
    private final AtomicInteger nextId = new AtomicInteger();

    DefaultHttpThreadFactory(String clientID) {
        namePrefix = "RxHttpClient-" + clientID + "-Worker-";
    }

    @Override
    public Thread newThread(Runnable r) {
        String name = namePrefix + nextId.getAndIncrement();
        Thread t = new Thread(null, r, name, 0, false);
        t.setDaemon(true);
        return t;
    }
}