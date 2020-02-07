package se.lars;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.zip.GZIPInputStream;

import static java.net.http.HttpResponse.BodySubscribers.mapping;
import static java.net.http.HttpResponse.BodySubscribers.ofInputStream;
import static se.lars.Json.parse;

public class JsonBodyHandler {

    public static <T> HttpResponse.BodyHandler<T> of(Class<T> targetType) {
        // there is a JDK bug that prevents this from runingn
        //https://bugs.openjdk.java.net/browse/JDK-8217264
        return responseInfo -> mapping(ofInputStream(), is -> parse(is, targetType));
    }

    public static InputStream getDecodedInputStream(HttpResponse<InputStream> httpResponse) {
        String encoding = determineContentEncoding(httpResponse);
        try {
            switch (encoding) {
                case "":
                    return httpResponse.body();
                case "gzip":
                    return new GZIPInputStream(httpResponse.body());
                default:
                    throw new UnsupportedOperationException(
                            "Unexpected Content-Encoding: " + encoding);
            }
        } catch (IOException ioe) {
            throw new JsonException(ioe);
        }
    }

    public static String determineContentEncoding(
            HttpResponse<?> httpResponse) {
        return httpResponse.headers().firstValue("Content-Encoding").orElse("");
    }
}
