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

/**
 * これもAuthInterceptorと同じ{@code HandlerInterceptor}（Controllerの処理の前に割り込む仕組み）。
 * こちらは認証ではなく、短時間に大量のリクエストを送ってくるIPアドレスを制限する役割を持つ。
 */
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
        // このリクエストのHTTPメソッドを取り出す
        String method = request.getMethod();
        // POST/PUT/DELETE（書き込み系）以外、つまりGETなら制限せず素通りさせる
        if (!("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))) {
            // GET（一覧・詳細取得等）は対象外。書き込み系のみ制限する
            return true;
        }

        // 接続元IPアドレスを取得する
        String ip = clientIp(request);
        // そのIPに対応するWindow（直近の集計用オブジェクト）を取得する。
        // まだ無ければcomputeIfAbsentで新規作成してMapに登録する
        Window window = windowsByIp.computeIfAbsent(ip, key -> new Window());

        // このリクエストの分をカウントし、まだ上限内であればtrue（素通り）が返る
        if (window.increment()) {
            return true;
        }

        // ここに到達するのは上限を超えた場合。WARNログを出し、429（Too Many Requests）を返す
        log.warn("E-R-001 リクエストが多すぎます ip={} method={} path={}", ip, method, request.getRequestURI());
        // レスポンスのHTTPステータスを429に設定する
        response.setStatus(429);
        // 何秒後に再試行すべきかをRetry-Afterヘッダで伝える（ウィンドウ長をミリ秒→秒に変換）
        response.setHeader("Retry-After", String.valueOf(WINDOW_MILLIS / 1000));
        // レスポンス本文がJSONであることをクライアントに伝える
        response.setContentType("application/json;charset=UTF-8");
        // docs/03_API設計書.md 2.3節の共通エラーレスポンス形式と同じ形。GlobalExceptionHandlerを経由しない
        // （Controllerに到達する前のHandlerInterceptorで判定するため）ため、ここで直接組み立てる。
        // メッセージは固定文字列のみを扱うため、JSON用のエスケープ処理は行っていない。
        // 現在時刻をISO 8601形式（タイムゾーン付き）の文字列に変換する
        String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        // ErrorResponseと同じ形（timestamp/status/error/message/errors）のJSON文字列を
        // 文字列連結で直接組み立てる
        String body = "{\"timestamp\":\"" + timestamp + "\",\"status\":429,\"error\":\"Too Many Requests\","
                + "\"message\":\"リクエストが多すぎます。しばらく時間をおいて再度お試しください\",\"errors\":null}";
        try {
            // 組み立てたJSON文字列をレスポンスの本文として書き込む
            response.getWriter().write(body);
        } catch (IOException ex) {
            // 書き込み自体が失敗した場合（クライアント切断等）は、処理を止めずログだけ残す
            log.warn("429レスポンスの書き込みに失敗しました", ex);
        }
        // falseを返し、Controllerへは進ませずここで処理を終える
        return false;
    }

    private String clientIp(HttpServletRequest request) {
        // リバースプロキシ経由を想定しないローカル構成のため、素のリモートアドレスをそのまま使う
        return request.getRemoteAddr();
    }

    // IPごとの直近ウィンドウの開始時刻とリクエスト数
    private static final class Window {
        // このウィンドウ（集計期間）が始まった時刻（エポックミリ秒）。複数スレッドから参照されるためvolatile
        private volatile long windowStart = System.currentTimeMillis();
        // 現在のウィンドウ内でのリクエスト数。複数スレッドから安全に増やせるようAtomicIntegerを使う
        private final AtomicInteger count = new AtomicInteger(0);

        // synchronizedにより、同一IPからの同時アクセスでも1リクエストずつ順番に処理させる
        synchronized boolean increment() {
            // 現在時刻を取得する
            long now = System.currentTimeMillis();
            // ウィンドウ開始から1分（WINDOW_MILLIS）以上経っていたら、ウィンドウを使い切ったとみなし、
            // 開始時刻を今にリセットしてカウントも0に戻す（固定ウィンドウ方式）
            if (now - windowStart >= WINDOW_MILLIS) {
                windowStart = now;
                count.set(0);
            }
            // カウントを1増やし、その結果が上限（MAX_REQUESTS_PER_WINDOW）以下ならtrue（まだ許可できる）を返す
            return count.incrementAndGet() <= MAX_REQUESTS_PER_WINDOW;
        }
    }
}
