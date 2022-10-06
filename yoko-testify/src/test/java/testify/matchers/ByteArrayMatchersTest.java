package testify.matchers;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static testify.matchers.ByteArrayMatchers.matchesHex;


class ByteArrayMatchersTest {

    @Test
    void testEmptyByteArray() {
        Matcher<byte[]> emptyByteArray = ByteArrayMatchers.emptyByteArray();
        assertThat(new byte[] {}, emptyByteArray);
    }

    @Test
    void testMatchesHexCreatesBaseMatcherObject() {
        Matcher<byte[]> baseMatcherObject = matchesHex("");
        assertTrue(baseMatcherObject instanceof BaseMatcher);
    }

    @Test
    void testPrettifyHexRegexToRemoveUnwantedChars() {
        Matcher<byte[]> baseMatcherObject = matchesHex("not a hex string");
        Description testDescription = new TestDescription();

        baseMatcherObject.describeTo(testDescription);
        assertThat(testDescription.toString(), is("\n\t\tae"));
    }

    @Test
    void testMatchesHex() {
        Matcher<byte[]> baseMatcherObject = matchesHex("0c0d647F0c");
        assertTrue(baseMatcherObject.matches(new byte[]{12, 13, 100, 127, 0xc}));
    }

    @Test
    void testNotMatchesHex() {
        Matcher<byte[]> baseMatcherObject = matchesHex("0c0d647F0c");
        assertFalse(baseMatcherObject.matches(new byte[]{12, 13, 100}));
    }

    @Test
    void testDescribeTo() {
        Matcher<byte[]> baseMatcherObject = matchesHex("0c0d647F");

        Description testDescription = new TestDescription();
        baseMatcherObject.describeTo(testDescription);
        assertThat(testDescription.toString(), is("\n\t\t0c0d647F"));
    }

    @Test
    void testMismatchDescription() {
        Matcher<byte[]> baseMatcherObject = matchesHex("0c0d647F");

        Description testDescription = new TestDescription();
        baseMatcherObject.describeMismatch(new byte[]{12, 13, 100}, testDescription);
        assertThat(testDescription.toString(), is("actual bytes differed at byte 0x3" +
                                                        "\ncommon prefix:\n\t\t0c0d64\nexpected suffix:" +
                                                        "\n\t\t      7F\nactual suffix:\n\t\t      \n"));
    }

}
