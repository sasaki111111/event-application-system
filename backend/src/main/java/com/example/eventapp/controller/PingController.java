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
@Tag(name = "稼働確認", description = "稼働確認・ログイン中利用者情報取得（AP-23〜24）")
@RestController
public class PingController {

    private final PingService pingService;
    private final AuthContext authContext;

    public PingController(PingService pingService, AuthContext authContext) {
        this.pingService = pingService;
        this.authContext = authContext;
    }

    @Operation(summary = "AP-24 稼働確認", description = "システムの起動状態を確認する。\"pong\"を返す。認証不要で呼び出せる。")
    @GetMapping("/api/ping")
    public String ping() {
        return pingService.pong();
    }

    // B-5: ダミー認証（AuthInterceptor→AuthContext）の疎通確認用。業務APIではない。
    @Operation(summary = "AP-23 ログイン中利用者情報取得",
            description = "現在の認証状態（利用者ID・名前・利用者区分・adminフラグ）を確認する。開発・動作確認時に使う。")
    @GetMapping("/api/whoami")
    public CurrentUser whoami() {
        return authContext.getCurrentUser();
    }
}
