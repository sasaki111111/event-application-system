package com.example.eventapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。applicationsテーブル（テーブル定義書_v1.0.md §2.3）に対応するJPAエンティティ。
@Entity
@Table(name = "applications")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    // '受付済' または 'キャンセル済'（要件定義書§4）
    @Column(nullable = false, length = 20)
    private String status;

    // APIレスポンスに即値が必要なためDB任せにせずJava側で設定する（created_at/updated_atとは異なる扱い）
    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    // created_at/updated_atはDB側のDEFAULT/ON UPDATEに任せる（Java側からは書き込まない）。
    // columnDefinitionはテスト環境（H2、ddl-auto: create-drop）でHibernateがスキーマを自動生成する際に
    // 本番のschema.sql同様のDEFAULTを持たせるためのもの（本番はddl-auto: noneのため影響しない）
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    protected Application() {
        // JPAが利用するデフォルトコンストラクタ
    }

    // D-3: イベント申込（API-03）用。生成した瞬間は必ず「受付済」
    public Application(User user, Event event) {
        this.user = user;
        this.event = event;
        this.status = ApplicationStatus.ACCEPTED;
        this.appliedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Event getEvent() {
        return event;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    // D-5: 申込キャンセル（API-05）用
    public void cancel() {
        this.status = ApplicationStatus.CANCELLED;
    }
}
