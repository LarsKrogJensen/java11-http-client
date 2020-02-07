package se.lars;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class FooData {
    public final boolean gzipped;
    public final Map<String, String> args;
    public final Map<String, String> headers;
    public final String origin;
    public final String url;
    public final FooBody inputBody;

    public FooData(
            @JsonProperty("gzipped") boolean gzipped,
            @JsonProperty("args") Map<String, String> args,
            @JsonProperty("headers") Map<String, String> headers,
            @JsonProperty("origin") String origin,
            @JsonProperty("url") String url,
            @JsonProperty("json") FooBody inputBody) {
        this.gzipped = gzipped;
        this.args = args;
        this.headers = headers;
        this.origin = origin;
        this.url = url;
        this.inputBody = inputBody;
    }

    @Override
    public String toString() {
        return "FooData{" +
               "gzipped=" + gzipped +
               ", args=" + args +
               ", headers=" + headers +
               ", origin='" + origin + '\'' +
               ", url='" + url + '\'' +
               ", inputBody=" + inputBody +
               '}';
    }
}
