package ru.gitverse.adoct.client;

import java.util.Map;

public record ContextPage(String context, Map<String, String> attachment) {
}
