package com.flowlens.admin.live.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class DouyinLiveInputResolver {

    private static final Pattern HTTP_URL_PATTERN = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);

    private static final Pattern LIVE_URL_PATTERN = Pattern.compile("live\\.douyin\\.com/(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern REFLOW_URL_PATTERN = Pattern.compile("/(?:douyin/)?webcast/reflow/(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern NUMBER_PATTERN = Pattern.compile("^\\d{6,}$");

    private static final String DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140 Safari/537.36";

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    public ResolvedDouyinLiveInput resolve(String input) {
        String candidate = extractCandidate(input);
        if (!StringUtils.hasText(candidate)) {
            return ResolvedDouyinLiveInput.empty();
        }
        ResolvedDouyinLiveInput direct = resolveKnownFormat(candidate);
        if (direct.hasStorableIdentifier()) {
            return direct;
        }
        if (!isDouyinUrl(candidate)) {
            return ResolvedDouyinLiveInput.empty();
        }
        String redirectedUrl = resolveRedirectUrl(candidate);
        if (!StringUtils.hasText(redirectedUrl) || redirectedUrl.equals(candidate)) {
            return new ResolvedDouyinLiveInput(null, null, candidate, null);
        }
        ResolvedDouyinLiveInput redirected = resolveKnownFormat(redirectedUrl);
        if (redirected.hasStorableIdentifier()) {
            return redirected;
        }
        return new ResolvedDouyinLiveInput(null, null, candidate, null);
    }

    private ResolvedDouyinLiveInput resolveKnownFormat(String value) {
        String liveId = findFirstGroup(LIVE_URL_PATTERN, value);
        if (StringUtils.hasText(liveId)) {
            return new ResolvedDouyinLiveInput(liveId, null, liveId, null);
        }
        String roomId = findFirstGroup(REFLOW_URL_PATTERN, value);
        if (StringUtils.hasText(roomId)) {
            return resolveReflowRoom(roomId, null);
        }
        if (!NUMBER_PATTERN.matcher(value).matches()) {
            return ResolvedDouyinLiveInput.empty();
        }
        // 纯数字按 PC live_id 保存；room_id 只从分享短链或 reflow URL 解析出来。
        return new ResolvedDouyinLiveInput(value, null, value, null);
    }

    private ResolvedDouyinLiveInput resolveReflowRoom(String roomId, String fallbackCanonicalInput) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://webcast.amemv.com/webcast/room/reflow/info/?type_id=0&live_id=1&room_id="
                    + roomId + "&app_id=1128"))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", DESKTOP_USER_AGENT)
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("抖音 reflow 房间解析失败，roomId={}, statusCode={}", roomId, response.statusCode());
                return new ResolvedDouyinLiveInput(null, roomId, fallbackCanonicalInput, null);
            }
            JsonNode room = objectMapper.readTree(response.body()).path("data").path("room");
            if (room.isMissingNode() || room.isNull()) {
                return new ResolvedDouyinLiveInput(null, roomId, fallbackCanonicalInput, null);
            }
            String resolvedRoomId = firstText(textOrNull(room.path("id_str")), textOrNull(room.path("id")), roomId);
            String liveId = textOrNull(room.path("owner").path("web_rid"));
            String liveTitle = textOrNull(room.path("title"));
            log.info("抖音 reflow 房间解析成功，roomId={}, liveId={}, liveTitle={}", resolvedRoomId, liveId, liveTitle);
            return new ResolvedDouyinLiveInput(liveId, resolvedRoomId, firstText(liveId, fallbackCanonicalInput), liveTitle);
        } catch (IOException ex) {
            log.warn("抖音 reflow 房间解析响应异常，roomId={}", roomId, ex);
            return new ResolvedDouyinLiveInput(null, roomId, fallbackCanonicalInput, null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new ResolvedDouyinLiveInput(null, roomId, fallbackCanonicalInput, null);
        } catch (RuntimeException ex) {
            log.warn("抖音 reflow 房间解析异常，roomId={}", roomId, ex);
            return new ResolvedDouyinLiveInput(null, roomId, fallbackCanonicalInput, null);
        }
    }

    private String resolveRedirectUrl(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", DESKTOP_USER_AGENT)
                .GET()
                .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).uri().toString();
        } catch (IOException ex) {
            log.warn("抖音短链跳转解析响应异常，url={}", url, ex);
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (RuntimeException ex) {
            log.warn("抖音短链跳转解析异常，url={}", url, ex);
            return null;
        }
    }

    private String extractCandidate(String input) {
        if (!StringUtils.hasText(input)) {
            return null;
        }
        String trimmed = input.trim();
        Matcher matcher = HTTP_URL_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return stripUrlTail(matcher.group());
        }
        return trimmed;
    }

    private String stripUrlTail(String value) {
        String cleaned = value.trim();
        while (cleaned.endsWith("。")
            || cleaned.endsWith("，")
            || cleaned.endsWith(",")
            || cleaned.endsWith(")")
            || cleaned.endsWith("）")
            || cleaned.endsWith("]")
            || cleaned.endsWith("】")
            || cleaned.endsWith("\"")
            || cleaned.endsWith("'")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    private boolean isDouyinUrl(String value) {
        try {
            String host = URI.create(value).getHost();
            if (!StringUtils.hasText(host)) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return normalizedHost.endsWith("douyin.com") || normalizedHost.endsWith("amemv.com");
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String findFirstGroup(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    public record ResolvedDouyinLiveInput(String liveId, String roomId, String canonicalInput, String liveTitle) {

        public static ResolvedDouyinLiveInput empty() {
            return new ResolvedDouyinLiveInput(null, null, null, null);
        }

        public boolean hasStorableIdentifier() {
            return StringUtils.hasText(liveId) || StringUtils.hasText(roomId) || StringUtils.hasText(canonicalInput);
        }
    }
}
