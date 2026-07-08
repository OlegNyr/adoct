package io.github.adoct.parser.confluence;

import java.util.Map;

public record ContextPage(String context, Map<String, String> attachment) {
}
