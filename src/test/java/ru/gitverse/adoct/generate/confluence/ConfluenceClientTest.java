package ru.gitverse.adoct.generate.confluence;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Разбор поля message из JSON-ошибки Confluence для понятных сообщений об ошибке. */
public class ConfluenceClientTest {

    @Test
    public void extractsMessageFromJsonError() {
        assertEquals("Page not found",
                ConfluenceClient.errorReason("{\"statusCode\":404,\"message\":\"Page not found\"}"));
    }

    @Test
    public void nullWhenNoMessageField() {
        assertNull(ConfluenceClient.errorReason("{\"statusCode\":500}"));
    }

    @Test
    public void nullWhenNotJson() {
        assertNull(ConfluenceClient.errorReason("<html>502 Bad Gateway</html>"));
    }

    @Test
    public void nullWhenEmpty() {
        assertNull(ConfluenceClient.errorReason(""));
        assertNull(ConfluenceClient.errorReason(null));
    }
}
