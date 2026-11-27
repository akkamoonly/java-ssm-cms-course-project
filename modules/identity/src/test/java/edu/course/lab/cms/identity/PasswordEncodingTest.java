package edu.course.lab.cms.identity;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordEncodingTest {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void bcryptEncodesAndMatchesAndSaltsRandomly() {
        var first = encoder.encode("Reader@123");
        var second = encoder.encode("Reader@123");

        assertNotEquals(first, second);
        assertTrue(first.startsWith("$2"));
        assertTrue(encoder.matches("Reader@123", first));
        assertTrue(encoder.matches("Reader@123", second));
        assertFalse(encoder.matches("reader@123", first));
        assertFalse(encoder.matches("Reader@1234", first));
    }
}
