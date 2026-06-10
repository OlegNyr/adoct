package ru.gitverse.adoct.client;

import java.util.Map;

public record ContentPage(String title, String url, String date,
                          String content,
                          String view,
                          Map<String, LinkResult> attachment) {
}
