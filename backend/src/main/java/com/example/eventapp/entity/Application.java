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

    // 申し込んだ区分。対象イベントに区分が無い場合はNULL（区分単位の申込ロジックは今後の対応で使用）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_type_id")
    private TicketType ticketType;

    // '受付済' または 'キャンセル済'（要件定義書§4）
    @Column(nullable = false, length = 20)
    private String status;

    // APIレスポンスに即値が必要なためDB任せにせずJava側で設定する（created_at/updated_atとは異なる扱い）
    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    // 申込時アンケートの回答。対象イベントにextraQuestionが無ければ意味を持たない（API設計書 API-03）
    @Column(name = "extra_answer", length = 500)
    private String extraAnswer;

    // 当日受付でチェックインされた日時。NULL＝未チェックイン（機能追加）
    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

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
        this(user, event, ApplicationStatus.ACCEPTED);
    }

    // 機能追加（キャンセル待ち）: 定員超過時は最初から「キャンセル待ち」で作る
    public Application(User user, Event event, String status) {
        this(user, event, null, status, null);
    }

    // 機能追加（定員区分）: 区分単位の申込用。ticketTypeは区分の無いイベントへの申込ではNULL
    public Application(User user, Event event, TicketType ticketType, String status, String extraAnswer) {
        this.user = user;
        this.event = event;
        this.ticketType = ticketType;
        this.status = status;
        this.extraAnswer = extraAnswer;
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

    public TicketType getTicketType() {
        return ticketType;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public String getExtraAnswer() {
        return extraAnswer;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }

    // D-5: 申込キャンセル（API-05）用
    public void cancel() {
        this.status = ApplicationStatus.CANCELLED;
    }

    // 機能追加（キャンセル待ちの繰り上げ）: 受付済の枠が空いた時にキャンセル待ちから昇格させる
    public void promote() {
        this.status = ApplicationStatus.ACCEPTED;
    }

    // 機能追加（当日受付）: チェックイン可否チェック（要件定義書§8 E8）はService側で行う
    public void checkIn() {
        this.checkedInAt = LocalDateTime.now();
    }
}
