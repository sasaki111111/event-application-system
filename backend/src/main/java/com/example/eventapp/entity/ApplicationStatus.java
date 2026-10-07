package com.example.eventapp.entity;

import java.util.Set;

// 実行環境: サーバー側（JVM）。applications.status_codeに入るコード値（docs/20_基本設計/22_テーブル定義書.md 7章）。
// 表示名（受付済・キャンセル待ち・キャンセル済）はコードマスタ（application_statuses）で管理し、ここには持たない。
// コード値を直接あちこちに書かないための定数置き場。
public final class ApplicationStatus {

    // 受付済：定員内で受け付けられた申込（定員に算入する）
    public static final int ACCEPTED = 1;

    // キャンセル待ち：定員超過時に登録され、受付済の枠が空くと自動的にACCEPTEDへ繰り上がる
    public static final int WAITLISTED = 2;

    // キャンセル済：利用者が取り消した申込
    public static final int CANCELLED = 9;

    // 定員・区分の判定上「有効」とみなす状態（受付済＋キャンセル待ち）
    public static final Set<Integer> ACTIVE_STATUSES = Set.of(ACCEPTED, WAITLISTED);

    private ApplicationStatus() {
    }
}
