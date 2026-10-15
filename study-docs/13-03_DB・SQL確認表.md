# 13-03 DB・SQL確認表

[13_システム動作・実装対応チェックリスト.md](13_システム動作・実装対応チェックリスト.md)の補助資料。`docs/20_基本設計/22_テーブル定義書.md`に定義された8テーブルについて、DDL（`backend/src/main/resources/db/schema.sql`）・Entity（`backend/src/main/java/com/example/eventapp/entity/`）・Repository（`.../repository/`）を突き合わせ、各テーブルがどの機能からどう使われているかを整理する。

DBアクセスはSpring Data JPAのメソッド名からのクエリ自動生成が中心で、`@Query`でJPQLを直接書いているのは悲観ロック用の3か所（`EventRepository#findByIdForUpdate`、`TicketTypeRepository#findByIdAndEvent_IdForUpdate`・`#findByIdForUpdate`）のみである。実行されるSQLはメソッド単位の処理内容として記載する（`application.yml`の`show-sql`は`false`）。

`schema.sql`は、H2のMySQL互換モードで`schema.sql`→`seed.sql`を実行する`SchemaScriptTest`で、記述の誤りが無いことを確認している（他のテストはEntityからテーブルを自動生成するため、スクリプト自体は検証されない）。

---

## 1. roles（利用者区分マスタ）・application_statuses（申込状況マスタ）

- **用途**：区分値のコードと表示名の対応を持つコードマスタ。コード値そのものを主キーとする（`docs/20_基本設計/22_テーブル定義書.md`1.5・4.7・4.8）。
- **コード値**：roles＝1（一般利用者）・2（管理者）、application_statuses＝1（受付済）・2（キャンセル待ち）・9（キャンセル済）。
- **Entity**：`RoleMaster.java`・`ApplicationStatusMaster.java`。コード値はJava側でも`RoleCode`・`ApplicationStatus`の定数として持ち、判定に使う（表示名は持たない）。
- **Repository**：`RoleRepository`・`ApplicationStatusRepository`（`findById`のみ使用）。`CodeNameResolver`がコードから表示名を取得する。
- **INSERT**：`seed.sql`のみ。登録・変更を行う画面・APIは無い。
- **SELECT**：利用者区分・申込状況を返す全API（応答に`roleName`・`statusName`を付ける）、CSV出力のステータス列。
- **制約**：表示名に`UNIQUE`。`users.role_code`・`applications.status_code`から外部キーで参照され（`ON DELETE RESTRICT`）、コードマスタに無い値は登録できない。

## 2. users（利用者）

- **用途**：一般利用者・管理者。退会（匿名化）した利用者も行は残す。
- **Entity**：`User.java`。`role_code`は`Integer`。`isAdmin()`・`demote()`・`changePassword()`・`anonymize()`・`isAnonymized()`を持つ。
- **Repository**：`UserRepository.java`
  - `findById`：`AuthInterceptor`が毎リクエスト`X-User-Id`から利用者を特定する。AP-012・AP-013・AP-141〜AP-146の対象の取得
  - `findByEmail`：AP-010（正規化後のメールアドレスで検索）
  - `existsByEmail`：AP-011・AP-145の重複チェック（R-15）
  - `findAllByOrderByIdAsc`：AP-140
  - `countByRoleCode`：AP-146で管理者が対象者1人だけでないかを判定（R-17）
  - `getReferenceById`：`ApplicationService#apply`・`FavoriteService#add`・`EventCommentService#post`が、確認済みの利用者IDからDBに問い合わせずに参照を得る
- **INSERT**：AP-011（`role_code=1`）、AP-145（`role_code=2`）。初期データは`seed.sql`の3件（ID=1 一般ユーザー、ID=2 管理者、ID=3 退会確認用ユーザー。初期パスワードはいずれも`Test1234`のハッシュ値）。
- **UPDATE**：AP-012（`password_hash`）、AP-146（`role_code`を1に）、AP-013（`name`・`email`の置き換え、`password_hash`をNULL、`anonymized_at`を設定）。
- **DELETE**：無し（退会は匿名化で行い、物理削除はしない）。
- **制約**：`uk_users_email`（Serviceの`existsByEmail`との二重の防止）。退会時はメールアドレスを利用者IDにもとづく`withdrawn-{id}@invalid.example`に置き換えるため、一意性を保ったまま匿名化できる。`fk_users_role`（rolesへの外部キー）。
- **パスワード**：`password_hash`はBCryptのハッシュ値（`VARCHAR(100)`、退会時はNULL）。パスワードそのものはどこにも保存しない。

## 3. events（イベント）

- **用途**：イベントの開催情報。削除は論理削除（`deleted_at`）。
- **Entity**：`Event.java`。`isOpen()`（R-01）・`softDelete()`・`restore()`・`syncCapacityFromTicketTypes()`を持つ。
- **Repository**：`EventRepository.java`
  - `findAllByDeletedAtIsNullOrderByStartAtAsc`：AP-020、AP-130（集計）
  - `findAllByDeletedAtIsNotNullOrderByStartAtAsc`：AP-123
  - `findByIdAndDeletedAtIsNull`：AP-021・AP-121・AP-122・AP-125・AP-030・AP-040・AP-050・AP-051（有効なイベントに限った参照はすべてこれを使う）
  - `findByIdAndDeletedAtIsNotNull`：AP-124（R-08）
  - `findByIdForUpdate`（`@Lock(PESSIMISTIC_WRITE)`）：区分の無いイベントへの申込（AP-030）・キャンセル時の繰り上げ（AP-032）の排他制御
- **INSERT**：AP-120。
- **UPDATE**：AP-121（内容の変更）、AP-122（`deleted_at`を設定）、AP-124（`deleted_at`をNULLに）、AP-120・AP-121で参加区分を保存したときの`capacity`の同期。
- **DELETE**：API経由では発生しない（イベントの削除は論理削除のみ）。
- **制約**：`chk_events_capacity CHECK (capacity >= 1)`。申込締切が開催日時より前であることはDB制約にせず、`@ValidEventDates`で担保する。
- **インデックス**：`idx_events_start_at`（開催日時の昇順の一覧）。

## 4. ticket_types（参加区分）

- **用途**：イベントの定員を複数の区分に分ける。区分の無いイベントでは0件。
- **Entity**：`TicketType.java`（区分名の一意制約を`@UniqueConstraint`でも定義し、テスト用のH2にも同じ制約を付ける）。
- **Repository**：`TicketTypeRepository.java`
  - `findByEvent_Id`：AP-021・AP-123の区分一覧、AP-120・AP-121での既存区分の定員合計
  - `existsByEvent_Id`：AP-030で区分の有無を判定（C-03）
  - `deleteByEvent_Id`：AP-120・AP-121での全置換（削除→再登録）
  - `findByIdAndEvent_IdForUpdate`（`@Lock`）：AP-030で、対象イベントの区分かの確認と排他ロックを同時に行う
  - `findByIdForUpdate`（`@Lock`）：AP-032の繰り上げ前のロック
- **INSERT**：AP-120・AP-121で`ticketTypes`を指定した場合。
- **UPDATE**：無し（変更は全置換で行う）。
- **DELETE**：AP-120・AP-121で`ticketTypes`を指定した場合（受付済・キャンセル待ちの申込が残っていれば、削除の前に400で拒否。R-09）。キャンセル済の申込が参照している場合は`fk_applications_ticket_type`（RESTRICT）の違反を400（E-B-017）に変換する。
- **制約**：`chk_ticket_types_capacity CHECK (capacity >= 1)`、`uk_ticket_types_event_name UNIQUE(event_id, name)`（Serviceの重複チェックとの二重の防止、R-10）、`fk_ticket_types_event ON DELETE CASCADE`。

## 5. applications（申込）

- **用途**：イベントへの参加申込。申込状況は`status_code`（1受付済・2キャンセル待ち・9キャンセル済）。
- **Entity**：`Application.java`。`cancel()`・`promote()`・`checkIn()`を持つ。
- **Repository**：`ApplicationRepository.java`
  - `countByEvent_IdAndStatus`・`countByTicketType_IdAndStatus`：定員判定（AP-030）、受付済数（AP-020・AP-021・AP-042・AP-130）、削除可否（AP-122、R-07）
  - `existsByUser_IdAndEvent_IdAndStatusIn`：二重申込の判定（AP-030、R-03）
  - `existsByEvent_IdAndStatusIn`：区分の変更可否（AP-120・AP-121、R-09）
  - `findFirstByEvent_IdAndTicketTypeIsNullAndStatusOrderByAppliedAtAsc`・`findFirstByTicketType_IdAndStatusOrderByAppliedAtAsc`：繰り上げ対象（AP-032、R-05）
  - `countByEvent_IdAndTicketTypeIsNullAndStatusAndAppliedAtLessThan`・`countByTicketType_IdAndStatusAndAppliedAtLessThan`：キャンセル待ちの順位（AP-031・AP-142）
  - `findByUser_IdOrderByAppliedAtDesc`：AP-031・AP-142
  - `findByEvent_IdOrderByAppliedAtAsc`：AP-125
  - `findAllByEvent_DeletedAtIsNullOrderByEvent_StartAtAsc`：AP-130のCSV（削除済みイベントの申込を除く）
  - `existsByUser_IdAndEvent_IdAndStatus`：呼び出し元が無い（[13-06](13-06_問題・確認事項.md)No.2）
- **INSERT**：AP-030。
- **UPDATE**：AP-032（対象をキャンセル済に、繰り上げがあれば別の1行を受付済に。1トランザクション内で最大2行）、AP-126（`checked_in_at`。再実行時は上書き）。
- **DELETE**：無し（キャンセルは申込状況の変更で表す）。
- **制約**：`(user_id, event_id)`にはUNIQUE制約を設けない（キャンセル後の再申込を許すため）。二重申込はServiceの判定と悲観ロックによる直列化で防ぐ。`fk_applications_ticket_type`（RESTRICT）、`fk_applications_status`（application_statusesへの外部キー）。
- **インデックス**：`idx_app_event_status_user`（定員判定・充足率・二重申込）、`idx_app_user_applied`（申込一覧）、`idx_app_ticket_type_status`（区分単位の判定）。
- **排他制御**：`applications`自体はロックせず、親の行（区分があれば`ticket_types`、無ければ`events`）を`SELECT ... FOR UPDATE`でロックして、同じ対象への申込・キャンセルを直列化する（`docs/30_詳細設計/32_処理詳細設計書.md`7章）。

## 6. favorites（お気に入り）

- **用途**：利用者のイベントのお気に入り登録。登録と解除のみで更新しないため`updated_at`を持たない。
- **Entity**：`Favorite.java`（`created_at`はAP-040の応答に使うためJava側で設定する）。
- **Repository**：`FavoriteRepository.java`
  - `findByUser_IdAndEvent_Id`：AP-040の冪等の判定（R-12）
  - `deleteByUser_IdAndEvent_Id`：AP-041（0件でもエラーにしない）
  - `findByUser_IdOrderByCreatedAtDesc`：AP-042・AP-143
  - `countByEvent_Id`：AP-020・AP-021の`favoriteCount`
  - `count`（JPA標準）：AP-131
- **INSERT**：AP-040（未登録の場合のみ）。**DELETE**：AP-041（物理削除）。**UPDATE**：無し。
- **制約**：`uk_favorites_user_event UNIQUE(user_id, event_id)`、`fk_favorites_event ON DELETE CASCADE`、`fk_favorites_user ON DELETE RESTRICT`。

## 7. event_comments（イベントコメント）

- **用途**：イベントへのコメントと返信（`parent_comment_id`、階層数の制限なし）。編集機能は無い。
- **Entity**：`EventComment.java`。`isOwnedBy()`（R-13）・`softDelete()`・`isDeleted()`を持つ。
- **Repository**：`EventCommentRepository.java`
  - `findByEvent_IdOrderByCreatedAtAscIdAsc`：AP-050（同じ秒の投稿の順序を`id`で確定させる）
  - `findByIdAndEvent_Id`：AP-051の返信先の確認（C-06）
  - `existsByParentComment_Id`：AP-052で物理削除か論理削除かを判定（R-14）
  - `countByDeletedAtIsNull`：AP-132
  - `findByUser_IdOrderByCreatedAtDescIdDesc`：AP-144（論理削除済みを含む）
  - `findByDeletedAtIsNullOrderByCreatedAtDesc`：AP-150
- **INSERT**：AP-051。
- **UPDATE**：AP-052で返信があるコメントを論理削除（`deleted_at`を設定）。復元は無い。
- **DELETE**：AP-052で返信が無いコメントを物理削除。
- **制約**：`fk_event_comments_event ON DELETE CASCADE`、`fk_event_comments_user ON DELETE RESTRICT`、`fk_event_comments_parent ON DELETE RESTRICT`。
- **インデックス**：`idx_event_comments_event_created`、`idx_event_comments_parent`。

---

## テーブル間リレーションの実装確認

| 親 | 子 | テーブル定義書5章 | schema.sql | 結果 |
|---|---|---|---|---|
| roles | users | RESTRICT | `fk_users_role ON DELETE RESTRICT` | ✓ |
| application_statuses | applications | RESTRICT | `fk_applications_status ON DELETE RESTRICT` | ✓ |
| users | applications・favorites・event_comments | RESTRICT | `ON DELETE RESTRICT`（3か所） | ✓ |
| events | ticket_types・applications・favorites・event_comments | CASCADE | `ON DELETE CASCADE`（4か所） | ✓ |
| ticket_types | applications | RESTRICT | `fk_applications_ticket_type ON DELETE RESTRICT` | ✓ |
| event_comments（親） | event_comments（返信） | RESTRICT | `fk_event_comments_parent ON DELETE RESTRICT` | ✓ |

eventsからのCASCADEは、イベントの物理削除がAPI経由では発生しないため、通常の運用では働かない（テーブル定義書5章の備考と`schema.sql`のコメントの両方に記載がある）。

## カラムの並び順

`schema.sql`は、テーブル定義書1.3の並び順（主キー → 外部キー〔イベント→利用者→その他〕 → 業務項目 → 区分コード → 業務上の日時 → 削除・匿名化の日時 → 監査用の日時）に従っている。

## 初期データ（`db/seed.sql`）

- コードマスタ：利用者区分2件、申込状況3件。
- 利用者3件：ID=1 一般ユーザー（`general@example.com`）、ID=2 管理者（`admin@example.com`）、ID=3 退会確認用ユーザー（`withdraw-test@example.com`）。初期パスワードはいずれも`Test1234`（`UserServiceTest`のUT-USR-N09で、記載したハッシュ値と照合できることを確認）。
- イベント51件：開催日時をばらつかせたサンプル50件と、定員超過（キャンセル待ち）の確認用に定員1名のイベント1件。
- 参加区分・申込・お気に入り・コメントの初期データは無い。
