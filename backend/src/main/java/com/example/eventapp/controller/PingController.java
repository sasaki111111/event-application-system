package com.example.eventapp.controller;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.common.CurrentUser;
import com.example.eventapp.service.PingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 実行環境: サーバー側（JVM、localhost:8080）。ブラウザ（Angular）やcurlからのHTTPリクエストを
// 最初に受け取るController層。B-3: 起動確認用の疎通エンドポイント。業務APIはD以降で実装する。
/**
 * 稼働確認（AP-24）・ログイン中利用者情報確認（AP-23）用のController。他のControllerと異なり、
 * フロントエンド（Angular）の画面からは呼ばれない（開発・動作確認時にブラウザやcurlから直接呼ぶための窓口）。
 * {@code /api/ping}はPingService#pongに処理を委譲し、{@code /api/whoami}はAuthContextから直接値を返すのみで、
 * Service層を経由しない。
 */
@Tag(name = "稼働確認", description = "稼働確認・ログイン中利用者情報取得（AP-23〜24）")
@RestController
public class PingController {

    private final PingService pingService;
    private final AuthContext authContext;

    public PingController(PingService pingService, AuthContext authContext) {
        this.pingService = pingService;
        this.authContext = authContext;
    }

    /**
     * システムの起動状態を確認する（AP-24）。認証不要で呼び出せる。PingService#pongに処理を委譲する。
     *
     * @return 固定文字列"pong"
     */
    @Operation(summary = "AP-24 稼働確認", description = "システムの起動状態を確認する。\"pong\"を返す。認証不要で呼び出せる。")
    @GetMapping("/api/ping")
    public String ping() {
        // PingServiceに確認用文字列の取得を依頼し、その結果をそのまま返す
        return pingService.pong();
    }

    // B-5: ダミー認証（AuthInterceptor→AuthContext）の疎通確認用。業務APIではない。
    /**
     * 現在の認証状態（ログイン中利用者の情報）を確認する（AP-23）。開発・動作確認用で、Service層は使わず
     * AuthContextが保持する値をそのまま返す。
     *
     * @return ログイン中の利用者情報（利用者ID・名前・利用者区分・adminフラグ）
     */
    @Operation(summary = "AP-23 ログイン中利用者情報取得",
            description = "現在の認証状態（利用者ID・名前・利用者区分・adminフラグ）を確認する。開発・動作確認時に使う。")
    @GetMapping("/api/whoami")
    public CurrentUser whoami() {
        // AuthContextが保持しているログイン中の利用者情報を、そのまま取り出して返す
        return authContext.getCurrentUser();
    }
}
