package se.lars;

import java.io.InputStream;
import java.net.http.HttpResponse;

public class HttpResponseException extends RuntimeException {
    final HttpResponse<InputStream> response;

    public HttpResponseException(HttpResponse<InputStream> response) {
        this.response = response;
    }
}
