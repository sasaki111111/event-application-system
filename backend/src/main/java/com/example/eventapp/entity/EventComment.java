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

// 実行環境: サーバー側（JVM）。event_commentsテーブル（docs/20_基本設計/22_テーブル定義書.md）に対応するJPAエンティティ。
@Entity
@Table(name = "event_comments")
public class EventComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 返信先のコメント。通常の投稿（返信ではない）場合はNULL。階層数に制限は設けない
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private EventComment parentComment;

    @Column(nullable = false, length = 500)
    private String body;

    // 返信が残っているため物理削除できないコメントの論理削除日時。NULL＝有効（削除されていない）
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // AP-050/20のレスポンスに即値が必要なためDB任せにせずJava側で設定する
    // （Favorite.createdAtと同じ考え方。コメントは編集不可のため業務上はこれが唯一のタイムスタンプ）
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    protected EventComment() {
        // JPAが利用するデフォルトコンストラクタ
    }

    // AP-051: コメント投稿用（通常の投稿）
    public EventComment(Event event, User user, String body) {
        this(event, user, body, null);
    }

    // AP-051: コメント投稿用（parentCommentを指定すると返信になる）
    public EventComment(Event event, User user, String body, EventComment parentComment) {
        this.event = event;
        this.user = user;
        this.body = body;
        this.parentComment = parentComment;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public User getUser() {
        return user;
    }

    public EventComment getParentComment() {
        return parentComment;
    }

    public String getBody() {
        return body;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // 削除可否チェック（要件定義書§8 E10）で使う本人判定
    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    // 返信が残っているコメントの削除（論理削除）。物理削除できる場合はService側がrepository.delete()を使う
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
