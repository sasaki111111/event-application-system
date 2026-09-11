package com.example.eventapp.controller;

import com.example.eventapp.service.PingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// B-3: 起動確認用の疎通エンドポイント。業務APIはD以降で実装する。
@RestController
public class PingController {

    private final PingService pingService;

    public PingController(PingService pingService) {
        this.pingService = pingService;
    }

    @GetMapping("/api/ping")
    public String ping() {
        return pingService.pong();
    }
}
