package com.example.eventapp.controller;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.common.CurrentUser;
import com.example.eventapp.service.PingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 実行環境: サーバー側（JVM、localhost:8080）。ブラウザ（Angular）やcurlからのHTTPリクエストを
// 最初に受け取るController層。B-3: 起動確認用の疎通エンドポイント。業務APIはD以降で実装する。
@RestController
public class PingController {

    private final PingService pingService;
    private final AuthContext authContext;

    public PingController(PingService pingService, AuthContext authContext) {
        this.pingService = pingService;
        this.authContext = authContext;
    }

    @GetMapping("/api/ping")
    public String ping() {
        return pingService.pong();
    }

    // B-5: ダミー認証（AuthInterceptor→AuthContext）の疎通確認用。業務APIではない。
    @GetMapping("/api/whoami")
    public CurrentUser whoami() {
        return authContext.getCurrentUser();
    }
}
