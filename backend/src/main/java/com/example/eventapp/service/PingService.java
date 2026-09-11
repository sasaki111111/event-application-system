package com.example.eventapp.service;

import org.springframework.stereotype.Service;

// 実行環境: サーバー側（JVM）。ControllerとRepositoryの間に立つ業務ロジック層(Service)の最小例。
// D以降、実際のAPI（イベント一覧・申込など）のロジックはこの層に実装していく。
@Service
public class PingService {

    public String pong() {
        return "pong";
    }
}
