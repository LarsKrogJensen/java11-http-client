package se.lars;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FooBody {
    public final String foo;
    public final int bar;

    public FooBody(@JsonProperty("foo") String foo, @JsonProperty("bar") int bar) {
        this.foo = foo;
        this.bar = bar;
    }

    @Override
    public String toString() {
        return "FooBody{" +
               "foo='" + foo + '\'' +
               ", bar=" + bar +
               '}';
    }
}
