package testify.matchers;

import org.hamcrest.Description;
import org.hamcrest.SelfDescribing;

import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.joining;

class TestDescription implements Description {
    final StringBuilder buffer = new StringBuilder();

    public Description appendText(String text) {
        buffer.append(text);
        return this;
    }

    public Description appendDescriptionOf(SelfDescribing value) {
        value.describeTo(this);
        return this;
    }

    public Description appendValue(Object value) {
        buffer.append(value);
        return this;
    }

    public <T> Description appendValueList(String start, String separator, String end, T... values) {
        buffer.append(Stream.of(values)
                .map(Objects::toString)
                .collect(joining(separator, start, end)));
        return this;
    }

    public <T> Description appendValueList(String start, String separator, String end, Iterable<T> values) {
        buffer.append(StreamSupport.stream(values.spliterator(), false)
                .map(Objects::toString)
                .collect(joining(separator, start, end)));
        return this;
    }

    public Description appendList(String start, String separator, String end, Iterable<? extends SelfDescribing> values) {
        buffer.append(start);
        boolean separatorNeeded = false;
        for (SelfDescribing value : values) {
            if (separatorNeeded) buffer.append(separator);
            value.describeTo(this);
            separatorNeeded = true;
        }
        buffer.append(end);
        return this;
    }

    public String toString() {
        return buffer.toString();
    }
}
