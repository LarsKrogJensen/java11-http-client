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

public class Json {
    private static final ObjectMapper mapper;

    public static <T> T parse(InputStream is, Class<T> tagetType) {
        try {
            return mapper.readValue(is, tagetType);
        } catch (IOException e) {
            throw new JsonException(e);
        }
    }

    public static String stringify(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (IOException e) {
            throw new JsonException(e);
        }
    }

    static {
        mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new AfterburnerModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_ABSENT);

    }
}
