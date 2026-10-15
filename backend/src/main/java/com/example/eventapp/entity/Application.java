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

// 実行環境: サーバー側（JVM）。applicationsテーブル（docs/20_基本設計/22_テーブル定義書.md）に対応するJPAエンティティ。
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

    // 申し込んだ区分。対象イベントに区分が無い場合はNULL（区分があるイベントでは、定員判定・繰り上げ・順位計算を区分単位で行う）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_type_id")
    private TicketType ticketType;

    // 申込状況コード（ApplicationStatus参照。1=受付済、2=キャンセル待ち、9=キャンセル済）。
    // 表示名はコードマスタ（application_statuses）で管理する
    @Column(name = "status_code", nullable = false)
    private Integer status;

    // APIレスポンスに即値が必要なためDB任せにせずJava側で設定する（created_at/updated_atとは異なる扱い）
    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    // 申込時アンケートの回答。対象イベントにextraQuestionが無ければ意味を持たない（docs/30_詳細設計/31_API詳細設計書.md AP-030）
    @Column(name = "extra_answer", length = 500)
    private String extraAnswer;

    // 当日受付（AP-126）でチェックインされた日時。NULL＝未チェックイン
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

    // イベント申込（AP-030）用。生成した瞬間は必ず「受付済」
    public Application(User user, Event event) {
        this(user, event, ApplicationStatus.ACCEPTED);
    }

    // 申込状況を指定して作る（定員超過時は最初から「キャンセル待ち」で作る。R-02）
    public Application(User user, Event event, Integer status) {
        this(user, event, null, status, null);
    }

    // 区分・アンケート回答を含めて作る（AP-030）。ticketTypeは区分の無いイベントへの申込ではNULL
    public Application(User user, Event event, TicketType ticketType, Integer status, String extraAnswer) {
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

    public Integer getStatus() {
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

    // 申込キャンセル（AP-032）用
    public void cancel() {
        this.status = ApplicationStatus.CANCELLED;
    }

    // キャンセル待ちの繰り上げ（R-05）: 受付済の枠が空いた時にキャンセル待ちから昇格させる
    public void promote() {
        this.status = ApplicationStatus.ACCEPTED;
    }

    // チェックイン（AP-126）: チェックイン可否チェック（R-06）はService側で行う。再実行時は日時を上書きする
    public void checkIn() {
        this.checkedInAt = LocalDateTime.now();
    }
}
