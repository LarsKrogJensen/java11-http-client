package se.lars;

import com.codahale.metrics.MetricRegistry;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.codahale.metrics.MetricRegistry.name;
import static se.lars.Json.parse;
import static se.lars.Json.stringify;
import static se.lars.JsonBodyHandler.getDecodedInputStream;

public class RxHttpClient {
    private static final AtomicInteger clientCounter = new AtomicInteger();
    private static final Logger log = LoggerFactory.getLogger(RxHttpClient.class);

    private final HttpClient client;
    private final Duration defaultReadTimeout;
    private static ThreadPoolExecutor dispatchExecutor;

    public RxHttpClient(HttpClient client) {
        this(client, Duration.ofSeconds(10));
    }

    public RxHttpClient(HttpClient client, Duration defaultTimeout) {
        this.client = client;
        this.defaultReadTimeout = defaultTimeout;
    }

    public static RxHttpClient create(MetricRegistry registry) {
        // Some notes to remember
        // the http client keeps a connection pool where connections are keept alive for 20 minutes
        // - controlled by system property jdk.httpclient.keepalive.timeout
        // - connection pool size (http 1.1) is unbounded controlled by jdk.httpclient.connectionPoolSize

        int clientId = clientCounter.incrementAndGet();

        // IO executor is used by http client to consume the io stream before dispatching
        // if not supplied http client will use a cachedThreadPool that doesn't have an proper upper bound for threads
        // we want to limit threads and and use a queue to mitigate the thread bound
        var ioWorkQueue = new ArrayBlockingQueue<Runnable>(1_000);
        var ioExecutor = new ThreadPoolExecutor(
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().availableProcessors() * 2,
                60, TimeUnit.SECONDS,
                ioWorkQueue,
                new DefaultHttpThreadFactory("dispatch-" + clientId),
                (runnable, executor1) -> {
                    log.error("IO Pool and threads exhausted {}", clientId);
                    // this is actually the default behavior but wanted
                    // capture and log
                    throw new RejectedExecutionException("Task " + runnable.toString() +
                                                         " rejected from " +
                                                         executor1.toString());
                }
        );

        // dispatch executor is used by this wrapper to dispatch completed responses
        // from the http client and thus a continuation executor.
        var dispatchWorkQueue = new ArrayBlockingQueue<Runnable>(1_000);
        dispatchExecutor = new ThreadPoolExecutor(
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().availableProcessors() * 2,
                60, TimeUnit.SECONDS,
                dispatchWorkQueue,
                new DefaultHttpThreadFactory("dispatch-" + clientId),
                (runnable, executor1) -> {
                    log.error("Dispatch pool and threads exhausted, client {}", clientId);
                    // this is actually the default behavior but wanted
                    // capture and log
                    throw new RejectedExecutionException("Task " + runnable.toString() +
                                                         " rejected from " +
                                                         executor1.toString());
                }
        );

        registry.gauge(name("rx_client_" + clientId, "dispatch", "pool", "core_size"), () -> dispatchExecutor::getCorePoolSize);
        registry.gauge(name("rx_client_" + clientId, "dispatch", "pool", "max_size"), () -> dispatchExecutor::getCorePoolSize);
        registry.gauge(name("rx_client_" + clientId, "dispatch", "pool", "active_count"), () -> dispatchExecutor::getActiveCount);
        registry.gauge(name("rx_client_" + clientId, "dispatch", "pool", "size"), () -> dispatchExecutor::getPoolSize);
        registry.gauge(name("rx_client_" + clientId, "dispatch", "queue", "count"), () -> dispatchWorkQueue::size);

        registry.gauge(name("rx_client_" + clientId, "io", "pool", "core_size"), () -> ioExecutor::getCorePoolSize);
        registry.gauge(name("rx_client_" + clientId, "io", "pool", "max_size"), () -> ioExecutor::getCorePoolSize);
        registry.gauge(name("rx_client_" + clientId, "io", "pool", "active_count"), () -> ioExecutor::getActiveCount);
        registry.gauge(name("rx_client_" + clientId, "io", "pool", "size"), () -> ioExecutor::getPoolSize);
        registry.gauge(name("rx_client_" + clientId, "io", "queue", "count"), () -> ioWorkQueue::size);

        var client = HttpClient.newBuilder()
                .executor(ioExecutor)
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        return new RxHttpClient(client);
    }

    public <T> Single<T> get(String url, Class<T> targetType) {
        try {
            var request = HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .header("Accept-Encoding", "gzip")
                    .timeout(defaultReadTimeout)
                    .build();
            return request(request, targetType);
        } catch (Exception e) {
            return Single.error(e);
        }
    }

    public <T> Single<T> post(String url, Object body, Class<T> targetType) {
        try {
            var request = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString(stringify(body)))
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Accept-Encoding", "gzip")
                    .timeout(defaultReadTimeout)
                    .build();
            return request(request, targetType);
        } catch (Exception e) {
            return Single.error(e);
        }
    }

    public <T> Single<T> request(HttpRequest request, Class<T> targetType) {

        var mdc = MDC.getCopyOfContextMap();

        return Single.defer(() -> {
            log.info("Sending request {} url {}", request.method(), request.uri());

            return Single.create(source -> client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                    .whenCompleteAsync((response, ex) -> {
                        restoreMdc(mdc);
                        log.info("Processing response");
                        if (ex != null) {
                            source.onError(unwrapCompletionException(ex));
                        } else {
                            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                                try {
                                    InputStream stream = getDecodedInputStream(response);
                                    T data = parse(stream, targetType);
                                    source.onSuccess(data);
                                } catch (Exception e) {
                                    source.onError(e);
                                }
                            } else {
                                source.onError(new HttpResponseException(response));
                            }
                        }
                    }, dispatchExecutor));
        });
    }

    private void restoreMdc(Map<String, String> mdc) {
        try {
            if (mdc != null) {
                MDC.setContextMap(mdc);
            }
        } catch (Exception e) {
            log.warn("Failed to restore MDC context - why", e);
        }
    }

    private Throwable unwrapCompletionException(Throwable ex) {
        if (ex instanceof CompletionException) {
            return ex.getCause();
        }

        return ex;
    }

}
