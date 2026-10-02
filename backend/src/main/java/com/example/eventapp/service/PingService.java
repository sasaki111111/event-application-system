package com.example.eventapp.service;

import org.springframework.stereotype.Service;

// 実行環境: サーバー側（JVM）。ControllerとRepositoryの間に立つ業務ロジック層(Service)の最小例。
// D以降、実際のAPI（イベント一覧・申込など）のロジックはこの層に実装していく。
/**
 * 稼働確認（AP-24）用の最小限のService。PingController#pingから呼ばれる。DBアクセスは行わない。
 */
@Service
public class PingService {

    /**
     * 固定の確認用文字列を返す。
     *
     * @return "pong"
     */
    public String pong() {
        return "pong";
    }
}
