package org.example.util;

import org.springframework.http.HttpHeaders;

import java.util.Arrays;
import java.util.List;

public final class HeaderUtils {

    private static final List<String> HOP_BY_HOP = Arrays.asList(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailer",
            "transfer-encoding", "upgrade", "host"
    );

    private HeaderUtils() {}

    public static HttpHeaders filterHopByHop(HttpHeaders source) {
        HttpHeaders copy = new HttpHeaders();
        if (source == null) return copy;
        source.forEach((k, v) -> {
            if (!HOP_BY_HOP.contains(k.toLowerCase())) copy.put(k, v);
        });
        return copy;
    }

    public static boolean hasContentType(HttpHeaders headers) {
        if (headers == null) return false;
        String v = headers.getFirst("Content-Type");
        return v != null && !v.isBlank();
    }
}

