package com.example.eventapp.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

// 実行環境: サーバー側（JVM）。書き込み系API（POST/PUT/DELETE、ログインAP-01を含む）に対する、
// IPアドレス単位の簡易な回数制限（docs/10_非機能設計書.md 10-3、機能追加）。
// ログイン総当たり・コメントやアカウント登録の大量投稿を抑止する。単一インスタンス運用（学内限定の
// 小規模利用、docs/10_非機能設計書.md 10-1）を前提としたインメモリ実装であり、複数インスタンス構成には
// 対応しない（その場合はRedis等の共有ストアへの置き換えが必要）。
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    // 固定ウィンドウ方式: ウィンドウ内の上限に達したら429を返す。閾値はいずれも暫定値（運用しながら調整する想定）
    private static final int MAX_REQUESTS_PER_WINDOW = 60;
    private static final long WINDOW_MILLIS = 60_000L;

    private final ConcurrentHashMap<String, Window> windowsByIp = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        if (!("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))) {
            // GET（一覧・詳細取得等）は対象外。書き込み系のみ制限する
            return true;
        }

        String ip = clientIp(request);
        Window window = windowsByIp.computeIfAbsent(ip, key -> new Window());

        if (window.increment()) {
            return true;
        }

        log.warn("E-R-001 リクエストが多すぎます ip={} method={} path={}", ip, method, request.getRequestURI());
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(WINDOW_MILLIS / 1000));
        response.setContentType("application/json;charset=UTF-8");
        // docs/03_API設計書.md 2.3節の共通エラーレスポンス形式と同じ形。GlobalExceptionHandlerを経由しない
        // （Controllerに到達する前のHandlerInterceptorで判定するため）ため、ここで直接組み立てる。
        // メッセージは固定文字列のみを扱うため、JSON用のエスケープ処理は行っていない。
        String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String body = "{\"timestamp\":\"" + timestamp + "\",\"status\":429,\"error\":\"Too Many Requests\","
                + "\"message\":\"リクエストが多すぎます。しばらく時間をおいて再度お試しください\",\"errors\":null}";
        try {
            response.getWriter().write(body);
        } catch (IOException ex) {
            log.warn("429レスポンスの書き込みに失敗しました", ex);
        }
        return false;
    }

    private String clientIp(HttpServletRequest request) {
        // リバースプロキシ経由を想定しないローカル構成のため、素のリモートアドレスをそのまま使う
        return request.getRemoteAddr();
    }

    // IPごとの直近ウィンドウの開始時刻とリクエスト数
    private static final class Window {
        private volatile long windowStart = System.currentTimeMillis();
        private final AtomicInteger count = new AtomicInteger(0);

        synchronized boolean increment() {
            long now = System.currentTimeMillis();
            if (now - windowStart >= WINDOW_MILLIS) {
                windowStart = now;
                count.set(0);
            }
            return count.incrementAndGet() <= MAX_REQUESTS_PER_WINDOW;
        }
    }
}
