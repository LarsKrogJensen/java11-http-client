package se.lars

import com.codahale.metrics.MetricRegistry
import io.reactivex.observers.TestObserver
import org.slf4j.MDC
import org.testcontainers.containers.GenericContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpTimeoutException
import java.time.Duration

@Testcontainers
class HttpClientSpec extends Specification {
    RxHttpClient client = RxHttpClient.create(new MetricRegistry())

    @Shared
    GenericContainer httpBin = new GenericContainer("kennethreitz/httpbin:latest")
        .withExposedPorts(80)

    def "should get a 200 response and map to object with gzip"() {
        when:
        def result = client.get("${url()}/gzip", FooData).blockingGet()

        then:
        result.gzipped
    }

    def "should get a 200 response and map to object with arguments"() {
        when:
        def result = client.get("${url()}/get?x=y", FooData).blockingGet()

        then:
        result.args["x"] == "y"
    }

    def "should post a 200 response and map to object"() {
        when:
        def result = client.post("${url()}/post", new FooBody("a", 1), FooData).blockingGet()

        then:
        result.inputBody.foo == "a"
        result.inputBody.bar == 1
    }

    def "should get a 418 response"() {
        when:
        client.get("${url()}/status/418", FooData).blockingGet()

        then:
        def ex = thrown(HttpResponseException)
        ex.response.statusCode() == 418
    }

    def "should get a json exception when bad format response"() {
        when:
        client.get("${url()}/xml", FooData).blockingGet()

        then:
        thrown(JsonException)
    }

    def "should get malformed url exception"() {
        when:
        def observer = TestObserver.create()
        client.get("htt::://httpbin.org/status/418", FooData).subscribe(observer)

        then:
        observer.awaitTerminalEvent()
        observer.assertError(IllegalArgumentException)
    }

    def "should get a timeout exception"() {
        given:
        def client2 = new RxHttpClient(HttpClient.newHttpClient(), Duration.ofSeconds(1))
        def observer = TestObserver.create()

        when:
        // can't use blocking get cause it will wrap checked exceptions with a runtime exceptions
        client2.get("${url()}/delay/3", FooData).subscribe(observer)

        then:
        observer.awaitTerminalEvent()
        observer.assertError(HttpTimeoutException)
    }

    def "ensure SLF4J MDC is restored"() {
        given:
        MDC.put("x", "1")
        def observer = new TestObserver<String>()

        when:
        client.get("${url()}/gzip", FooData)
              .map { MDC.get("x") }
              .subscribe(observer)

        then:
        observer.awaitTerminalEvent()
        observer.assertValue("1")

    }

    String url() {
        return "http://localhost:${httpBin.getFirstMappedPort()}"
    }
}
