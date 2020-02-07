package se.lars;

import com.codahale.metrics.MetricRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import static java.lang.Thread.sleep;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws InterruptedException {
        MetricRegistry registry = new MetricRegistry();

        var client = RxHttpClient.create(registry);

        var single = client.post("http://httpbin.org/post",
                new FooBody("1", 3),
                FooData.class);

        var latch = new CountDownLatch(1);

        single.subscribe(
                data -> {
                    log.info("Success {}", data);
                    latch.countDown();
                },
                ex -> log.error("Opps ", ex)
        );


        latch.await();
    }
}
