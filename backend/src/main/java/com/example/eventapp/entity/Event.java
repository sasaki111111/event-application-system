package com.example.eventapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。eventsテーブル（テーブル定義書_v1.0.md §2.2）に対応するJPAエンティティ。
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

    // 機能追加（ソフトデリート）: NULL=有効、日時あり=削除済み。物理削除はせず、管理者が「削除済み
    // イベント」画面から復元できるようにする。
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "organizer_name", length = 100)
    private String organizerName;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // 申込時アンケートの質問文言。NULL＝アンケート無し
    @Column(name = "extra_question", length = 200)
    private String extraQuestion;

    // created_at/updated_atはDB側のDEFAULT/ON UPDATEに任せる（Java側からは書き込まない）。
    // columnDefinitionはテスト環境（H2、ddl-auto: create-drop）でHibernateがスキーマを自動生成する際に
    // 本番のschema.sql同様のDEFAULTを持たせるためのもの（本番はddl-auto: noneのため影響しない）
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    protected Event() {
        // JPAが利用するデフォルトコンストラクタ
    }

    // D-2: イベント登録（API-06）用
    public Event(String name, LocalDateTime startAt, String place, Integer capacity,
            LocalDateTime applicationDeadline, String description,
            String organizerName, String imageUrl, String extraQuestion) {
        this.name = name;
        this.startAt = startAt;
        this.place = place;
        this.capacity = capacity;
        this.applicationDeadline = applicationDeadline;
        this.description = description;
        this.organizerName = organizerName;
        this.imageUrl = imageUrl;
        this.extraQuestion = extraQuestion;
    }

    // D-2: イベント編集（API-07）用
    public void applyChanges(String name, LocalDateTime startAt, String place, Integer capacity,
            LocalDateTime applicationDeadline, String description,
            String organizerName, String imageUrl, String extraQuestion) {
        this.name = name;
        this.startAt = startAt;
        this.place = place;
        this.capacity = capacity;
        this.applicationDeadline = applicationDeadline;
        this.description = description;
        this.organizerName = organizerName;
        this.imageUrl = imageUrl;
        this.extraQuestion = extraQuestion;
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

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public String getOrganizerName() {
        return organizerName;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getExtraQuestion() {
        return extraQuestion;
    }

    // 受付中か（要件定義書§4）：現在 <= 申込締切 かつ 現在 < 開催日時
    public boolean isOpen(LocalDateTime now) {
        return !now.isAfter(applicationDeadline) && now.isBefore(startAt);
    }

    // 機能追加（ソフトデリート）: 物理削除の代わりに削除日時を記録する
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    // 機能追加（ソフトデリートの復元）
    public void restore() {
        this.deletedAt = null;
    }

    // 機能追加（定員区分）: 区分を保存した際、capacityを区分の定員合計に同期させる
    // （区分の無いイベントではEventUpsertRequest.capacity()がそのまま定員になるため呼び出さない）
    public void syncCapacityFromTicketTypes(int totalCapacity) {
        this.capacity = totalCapacity;
    }
}
