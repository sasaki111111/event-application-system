-- C-1: DDL適用（docs/20_基本設計/22_テーブル定義書.md 準拠）
-- 実行環境: MySQLサーバー（アプリのJVMプロセスとは別）。Spring Bootからは自動実行しない
-- （application.ymlで ddl-auto: none にしているため、このファイルを手動で一度だけ流す）。
--
-- 実行例:
--   mysql --default-character-set=utf8mb4 -u eventapp_app -p eventapp < backend/src/main/resources/db/schema.sql
--
-- カラムの並び順はテーブル定義書1.3に従う（主キー → 外部キー（イベント→利用者→その他） → 業務項目 →
-- 区分コード → 業務上の日時 → 削除・匿名化の日時 → 監査用の日時）。

-- 外部キーの都合上、依存される側から先に作成し、削除(DROP)は依存する側から先に行う。
-- 作成順: コードマスタ（roles・application_statuses） → users → events → ticket_types
--         → applications → favorites → event_comments
DROP TABLE IF EXISTS event_comments;
DROP TABLE IF EXISTS favorites;
DROP TABLE IF EXISTS applications;
DROP TABLE IF EXISTS ticket_types;
DROP TABLE IF EXISTS events;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS application_statuses;
DROP TABLE IF EXISTS roles;

-- 4.7 roles（利用者区分マスタ）。コード値そのものを主キーとする（連番のidは持たない）
CREATE TABLE roles (
    code          SMALLINT    NOT NULL,
    -- 画面に表示する名称
    name          VARCHAR(20) NOT NULL,
    -- 一覧・選択肢に表示する際の順序（昇順）
    display_order INT         NOT NULL,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (code),
    CONSTRAINT uk_roles_name UNIQUE (name)
) ENGINE = InnoDB;

-- 4.8 application_statuses（申込状況マスタ）
CREATE TABLE application_statuses (
    code          SMALLINT    NOT NULL,
    -- 画面・CSVに表示する名称
    name          VARCHAR(20) NOT NULL,
    display_order INT         NOT NULL,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (code),
    CONSTRAINT uk_application_statuses_name UNIQUE (name)
) ENGINE = InnoDB;

-- 4.1 users（利用者）
CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    -- パスワードをBCryptでハッシュ化した値。パスワードそのものは保存しない。
    -- 退会（匿名化）時はNULLにし、以後ログインできないようにする
    password_hash VARCHAR(100) NULL,
    -- 利用者区分コード（1=一般利用者、2=管理者）。表示名はrolesで管理する
    role_code     SMALLINT     NOT NULL,
    -- 退会（匿名化）した日時。NULL＝退会していない（通常の利用者）
    anonymized_at DATETIME     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    CONSTRAINT fk_users_role
        FOREIGN KEY (role_code) REFERENCES roles (code)
        ON DELETE RESTRICT
) ENGINE = InnoDB;

-- 4.2 events（イベント）
CREATE TABLE events (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    name                  VARCHAR(100) NOT NULL,
    start_at              DATETIME     NOT NULL,
    place                 VARCHAR(100) NOT NULL,
    -- 区分（ticket_types）が無いイベントでは定員そのもの。区分がある場合は区分の定員合計をアプリ側で同期する
    capacity              INT          NOT NULL,
    -- application_deadlineがstart_atより前であることはアプリ側で担保する（DB制約にはしない）
    application_deadline  DATETIME     NOT NULL,
    description           VARCHAR(1000) NULL,
    -- 主催者名。任意項目のためNULL可
    organizer_name        VARCHAR(100) NULL,
    -- イベント画像のURL。任意項目のためNULL可
    image_url             VARCHAR(500) NULL,
    -- 申込時アンケートの質問文言。NULL＝アンケート無し
    extra_question        VARCHAR(200) NULL,
    -- 論理削除: NULL=有効、日時あり=削除済み（管理者の「削除済みイベント」画面から復元可能）
    deleted_at            DATETIME     NULL,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_events_capacity CHECK (capacity >= 1)
) ENGINE = InnoDB;

-- イベント一覧の開催日時昇順ソート用インデックス（テーブル定義書8章）
CREATE INDEX idx_events_start_at ON events (start_at);

-- 4.3 ticket_types（参加区分）
-- event_id列にはfk_ticket_types_eventの作成時にInnoDBが自動でインデックスを張るため、
-- 別途CREATE INDEXは行わない（同一列への重複インデックスを避けるため）
CREATE TABLE ticket_types (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    event_id   BIGINT       NOT NULL,
    name       VARCHAR(50)  NOT NULL,
    capacity   INT          NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_ticket_types_capacity CHECK (capacity >= 1),
    -- 同一イベント内での区分名の重複を禁止する（テーブル定義書4.3）
    CONSTRAINT uk_ticket_types_event_name UNIQUE (event_id, name),
    CONSTRAINT fk_ticket_types_event
        FOREIGN KEY (event_id) REFERENCES events (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

-- 4.4 applications（申込）
-- (user_id, event_id)にUNIQUE制約は付けない：キャンセル後の再申込を許すため
-- （二重申込チェックは status_code が 1（受付済）または 2（キャンセル待ち）の行の有無だけをアプリ側で見る）
CREATE TABLE applications (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    event_id        BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    -- 申し込んだ区分。対象イベントに区分が無い場合はNULL
    ticket_type_id  BIGINT       NULL,
    -- 申込時アンケートの回答。対象イベントにextra_questionが無い場合はNULL
    extra_answer    VARCHAR(500) NULL,
    -- 申込状況コード（1=受付済、2=キャンセル待ち、9=キャンセル済）。表示名はapplication_statusesで管理する
    status_code     SMALLINT     NOT NULL DEFAULT 1,
    applied_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 当日受付でチェックインされた日時。NULL＝未チェックイン
    checked_in_at   DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_applications_event
        FOREIGN KEY (event_id) REFERENCES events (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_applications_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT,
    -- RESTRICTは「申込が残っている区分は削除できない」という業務ルール（テーブル定義書5章）のため。
    -- events→ticket_types・events→applicationsは共にCASCADEだが、物理削除はAPI経由では発生しない
    -- （イベント削除は論理削除のみ。EventService.delete()参照）ため、CASCADE同士の処理順序に
    -- InnoDBの保証が無い点（子テーブル間の処理順は未規定）がこのRESTRICTと衝突することはない。
    CONSTRAINT fk_applications_ticket_type
        FOREIGN KEY (ticket_type_id) REFERENCES ticket_types (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_applications_status
        FOREIGN KEY (status_code) REFERENCES application_statuses (code)
        ON DELETE RESTRICT
) ENGINE = InnoDB;

-- 定員判定・充足率集計・二重申込の判定用（テーブル定義書8章）
CREATE INDEX idx_app_event_status_user ON applications (event_id, status_code, user_id);
-- 利用者ごとの申込一覧（申込日時順）用
CREATE INDEX idx_app_user_applied ON applications (user_id, applied_at);
-- 参加区分単位の定員判定・受付済件数の集計用
CREATE INDEX idx_app_ticket_type_status ON applications (ticket_type_id, status_code);

-- 4.5 favorites（お気に入り）
-- 登録（行の追加）と解除（行の削除）のみで、登録後に内容を更新することがないため、updated_atは持たない
CREATE TABLE favorites (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    event_id   BIGINT   NOT NULL,
    user_id    BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 同じ組み合わせの二重登録を防ぐ（重複時はアプリ側で冪等に処理する）
    CONSTRAINT uk_favorites_user_event UNIQUE (user_id, event_id),
    CONSTRAINT fk_favorites_event
        FOREIGN KEY (event_id) REFERENCES events (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_favorites_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT
) ENGINE = InnoDB;

-- 4.6 event_comments（イベントコメント）
CREATE TABLE event_comments (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    event_id           BIGINT       NOT NULL,
    user_id            BIGINT       NOT NULL,
    -- 返信先のコメント。通常の投稿（返信ではない）場合はNULL
    parent_comment_id  BIGINT       NULL,
    body               VARCHAR(500) NOT NULL,
    -- 返信が残っているため物理削除できないコメントの論理削除日時。NULL＝有効（削除されていない）
    deleted_at         DATETIME     NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_event_comments_event
        FOREIGN KEY (event_id) REFERENCES events (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_event_comments_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_event_comments_parent
        FOREIGN KEY (parent_comment_id) REFERENCES event_comments (id)
        ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE INDEX idx_event_comments_event_created ON event_comments (event_id, created_at);
CREATE INDEX idx_event_comments_parent ON event_comments (parent_comment_id);
