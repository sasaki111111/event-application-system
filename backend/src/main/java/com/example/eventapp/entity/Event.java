package com.example.eventapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。eventsテーブル（テーブル定義書_v1.0.md §2.2）に対応するJPAエンティティ。
// setter類はD-2（登録・編集・削除API）で追加する。D-1では取得のみのため未使用。
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false, length = 100)
    private String place;

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "application_deadline", nullable = false)
    private LocalDateTime applicationDeadline;

    @Column(length = 1000)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Event() {
        // JPAが利用するデフォルトコンストラクタ
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public String getPlace() {
        return place;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public LocalDateTime getApplicationDeadline() {
        return applicationDeadline;
    }

    public String getDescription() {
        return description;
    }

    // 受付中か（要件定義書§4）：現在 <= 申込締切 かつ 現在 < 開催日時
    public boolean isOpen(LocalDateTime now) {
        return !now.isAfter(applicationDeadline) && now.isBefore(startAt);
    }
}
