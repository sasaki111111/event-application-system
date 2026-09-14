package com.example.eventapp.repository;

import com.example.eventapp.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;

// 実行環境: サーバー側（JVM）。applicationsテーブルへの問い合わせ口。
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    // 受付済数の集計（acceptedCount／充足率）に使う。テーブル定義書§5「カラムにしない派生値」
    long countByEvent_IdAndStatus(Long eventId, String status);

    // D-3: 二重申込チェック（同一ユーザー×同一イベントに「受付済」が既に無いか）
    boolean existsByUser_IdAndEvent_IdAndStatus(Long userId, Long eventId, String status);
}
