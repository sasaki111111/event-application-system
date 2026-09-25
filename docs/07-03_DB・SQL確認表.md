# 07-03 DB・SQL確認表

[07_システム動作・実装対応チェックリスト.md](07_システム動作・実装対応チェックリスト.md) の補助資料。`docs/02_テーブル定義書.md`に定義された6テーブルについて、実際のDDL（`backend/src/main/resources/db/schema.sql`）・Entity（`backend/src/main/java/.../entity/`）・Repository（`.../repository/`）を突き合わせ、各テーブルがどの機能からどう使われているかを整理する。

DBアクセスはSpring Data JPAのメソッド名からのクエリ自動生成が中心で、生SQL（`@Query`）は悲観ロック用の3箇所のみ。ORM層で自動生成されるSQLは実行時ログ（`show-sql`は本番`application.yml`では`false`）で確認する運用のため、本表ではメソッド単位でSQL相当の処理内容を記載する。

---

## 1. users（利用者）

- **用途**：一般利用者・管理者の識別。パスワードは保持しない（ダミー認証、要件定義書§8）。
- **Entity**：`entity/User.java`（`role`は`"general"`/`"admin"`の文字列、DBに列挙型制約は無くアプリ側で担保）
- **Repository**：`repository/UserRepository.java`
  - `findById`（JPA標準）：`AuthInterceptor.preHandle()`が`X-User-Id`ヘッダの値でログインユーザーを解決する際に使用（毎リクエスト、認証対象APIすべて）
  - `existsByEmail`：`UserService.register()`／`registerAdmin()`（AP-02/AP-25）の重複チェック
  - `findByEmail`：`UserService.login()`（AP-01）
  - `findAllByOrderByIdAsc`：`UserService.list()`（AP-03）
  - `getReferenceById`：`ApplicationService.apply()`／`FavoriteService.add()`／`EventCommentService.post()`が、既に存在確認済みのuserIdからDBに問い合わせずプロキシ参照を得るために使用（N+1回避）
- **INSERTタイミング**：AP-02（一般ユーザー登録）／AP-25（管理者登録）、初期データは`db/seed.sql`（id=1一般／id=2管理者）
- **SELECTタイミング**：毎リクエストの認証（`findById`）、ログイン（`findByEmail`）、一覧（AP-03）
- **UPDATE/DELETEタイミング**：無し（利用者の更新・削除機能は提供しない、テーブル定義書§5と一致）
- **制約**：`email`に`UNIQUE`（`uk_users_email`）→アプリ側の`existsByEmail`チェックとDB制約の二重防御。`role`はNOT NULLのみでCHECK制約は無い（アプリ側で`"general"`/`"admin"`固定値のみ書き込む設計により担保）。
- **他テーブルとの関連**：`applications.user_id`／`favorites.user_id`／`event_comments.user_id`から参照（いずれも`ON DELETE RESTRICT`。利用者削除機能自体が存在しないため実運用で発火しない）。
- **結果**：✓ 確認済み

## 2. events（イベント）

- **用途**：イベント開催情報。ソフトデリート方式（`deleted_at`）。
- **Entity**：`entity/Event.java`（`isOpen()`, `softDelete()`, `restore()`, `syncCapacityFromTicketTypes()`という業務ロジックをEntity自身が持つ＝ドメインモデル的設計）
- **Repository**：`repository/EventRepository.java`
  - `findAllByDeletedAtIsNullOrderByStartAtAsc`：AP-04（一覧、`idx_events_start_at`使用想定）
  - `findAllByDeletedAtIsNotNullOrderByStartAtAsc`：AP-06（削除済み一覧）
  - `findByIdAndDeletedAtIsNull`：AP-05詳細取得、AP-08更新、AP-12申込時のイベント存在確認、AP-16お気に入り登録時、AP-19〜21コメント関連のイベント存在確認、AP-11当日受付一覧のイベント存在確認 — 有効イベントに限定した参照はすべてこのメソッドを経由
  - `findByIdAndDeletedAtIsNotNull`：AP-10復元
  - `findByIdForUpdate`（`@Lock(PESSIMISTIC_WRITE)`、`SELECT ... FOR UPDATE`相当）：区分の無いイベントへの申込（AP-12）・キャンセル時の繰り上げ（AP-14）で、定員判定から更新までの間の排他制御に使用（D-10／O-01）
- **INSERTタイミング**：AP-07（管理者のイベント登録）
- **SELECTタイミング**：AP-04/05/06/11/12/14/16/19/20/21ほぼ全機能から参照
- **UPDATEタイミング**：AP-08（内容変更）、AP-09（`deleted_at`設定＝論理削除）、AP-10（`deleted_at`解除＝復元）、AP-07/AP-08の区分保存時に`capacity`を区分合計へ同期（`syncCapacityFromTicketTypes()`経由）
- **DELETEタイミング**：物理削除は通常運用で発生しない（`EventService`に物理削除の呼び出し経路が無い。テストの`eventRepository.deleteAll()`はテスト用の後始末のみ）
- **制約**：`chk_events_capacity CHECK (capacity >= 1)`。`application_deadline`が`start_at`以前であることはDB制約に無くアプリ側（`@ValidEventDates`/`EventDatesValidator`）で担保（`schema.sql`のコメントに明記）。
- **インデックス**：`idx_events_start_at`（一覧の開催日時昇順ソート用）
- **結果**：✓ 確認済み

## 3. ticket_types（参加区分）

- **用途**：イベントを複数枠に分けた定員管理（機能追加）。区分未設定イベントでは0件。
- **Entity**：`entity/TicketType.java`
- **Repository**：`repository/TicketTypeRepository.java`
  - `findByEvent_Id`：AP-05詳細表示、AP-09編集フォーム初期値
  - `existsByEvent_Id`：AP-12申込時「区分が1件以上あるか」の判定（`ApplicationService.resolveTicketType()`）
  - `findByIdAndEvent_Id`（非ロック版は未使用、ロック版のみ実使用）
  - `deleteByEvent_Id`：AP-07/AP-08保存時の全置換（削除→再作成）
  - `findByIdAndEvent_IdForUpdate`（`@Lock`）：AP-12申込時、対象イベントに属する区分かの検証と排他ロックを同時に行う
  - `findByIdForUpdate`（`@Lock`）：AP-14キャンセル時の繰り上げ対象決定前のロック取得
- **INSERTタイミング**：AP-07/AP-08で`ticketTypes`が指定された場合（`EventService.saveTicketTypes()`）
- **SELECTタイミング**：AP-05/09/12/14
- **UPDATEタイミング**：無し（内容変更は全置換＝DELETE→INSERTで実現、個別UPDATEは行わない）
- **DELETEタイミング**：AP-08で区分の内容を変更するたび（既存の対象イベントの区分を全削除してから再作成）。ただし対象イベントに有効な申込（受付済／キャンセル待ち）が残っていれば`EventService.saveTicketTypes()`が事前に`BusinessException`を投げ、削除自体を実行しない。
- **制約**：`chk_ticket_types_capacity CHECK (capacity >= 1)`、`uk_ticket_types_event_name UNIQUE(event_id, name)`（同一イベント内の区分名重複をDB制約でも防止、アプリ側の`distinctNames`チェックと二重防御）。`fk_ticket_types_event ON DELETE CASCADE`。
- **結果**：✓ 確認済み

## 4. applications（申込）

- **用途**：イベントへの参加申込。`status`は`受付済`/`キャンセル待ち`/`キャンセル済`の3値（`entity/ApplicationStatus.java`で定数化）。
- **Entity**：`entity/Application.java`（`cancel()`, `promote()`, `checkIn()`という状態遷移メソッドを持つ）
- **Repository**：`repository/ApplicationRepository.java`（メソッド一覧は[07-01_設計・コード対応表.md](07-01_設計・コード対応表.md)参照）。主なクエリ：
  - `countByEvent_IdAndStatus` / `countByTicketType_IdAndStatus`：定員判定（AP-12）・一覧のacceptedCount（AP-04/05/18）・削除可否（AP-09）
  - `existsByUser_IdAndEvent_IdAndStatusIn`：二重申込チェック（AP-12）
  - `existsByEvent_IdAndStatusIn`：区分変更可否チェック（AP-07/08）
  - `findFirstBy..._IdAndStatusOrderByAppliedAtAsc`（区分あり/無し2種）：繰り上げ対象取得（AP-14）
  - `countBy..._StatusAndAppliedAtLessThan`（区分あり/無し2種）：キャンセル待ち順位計算（AP-13）
  - `findByUser_IdOrderByAppliedAtDesc`：マイページ（AP-13）
  - `findByEvent_IdOrderByAppliedAtAsc`：当日受付一覧（AP-11）
  - `findAllByEvent_DeletedAtIsNullOrderByEvent_StartAtAsc`：CSV出力（AP-22、削除済みイベント紐づき分を除外）
- **INSERTタイミング**：AP-12（申込）
- **SELECTタイミング**：ほぼ全機能（一覧の受付数、詳細、マイページ、当日受付、実績集計/CSV）
- **UPDATEタイミング**：AP-14（キャンセル：対象を`キャンセル済`に、かつ繰り上げ対象を`受付済`に＝1トランザクション内で最大2行UPDATE）、AP-15（チェックイン：`checked_in_at`更新、再実行時は上書き）
- **DELETEタイミング**：無し（物理削除経路は存在しない。取消は論理的な状態変更のみ）
- **制約**：`(user_id, event_id)`にUNIQUE制約を意図的に設けていない（テーブル定義書§4.4のとおり、キャンセル後の再申込を許容するため。二重申込防止はアプリ側の`status IN ('受付済','キャンセル待ち')`存在チェックのみで担保＝DB制約による最終防御が無い設計であることに留意）。`fk_applications_ticket_type ON DELETE RESTRICT`（申込が残る区分は削除不可というルールをDBレベルでも保証）。
- **インデックス**：`idx_app_event_status_user`（定員判定・二重申込チェック）、`idx_app_user_applied`（マイページ）、`idx_app_ticket_type_status`（区分単位の定員判定）
- **同時実行制御**：D-10で悲観ロック方式が確定（`docs/06_設計確定時の確認事項.md`）。`applications`テーブル自体はロックせず、親（`events`または`ticket_types`）の行をロックすることで同一対象への同時書き込みを直列化する設計（`EventRepository.findByIdForUpdate`/`TicketTypeRepository.findByIdAndEvent_IdForUpdate`/`findByIdForUpdate`）。`ApiIntegrationTest.o01_同時に申し込んでも定員を超えて受付済にならない`で2スレッド同時実行を検証し合格。
- **結果**：✓* 確認済み（自動テストで同時実行含め検証済み）

## 5. favorites（お気に入り）

- **用途**：利用者のイベントお気に入り登録（機能追加）。
- **Entity**：`entity/Favorite.java`
- **Repository**：`repository/FavoriteRepository.java`
  - `findByUser_IdAndEvent_Id`：AP-16登録時の冪等判定
  - `deleteByUser_IdAndEvent_Id`：AP-17解除（0件でもエラーなし）
  - `findByUser_IdOrderByCreatedAtDesc`：AP-18一覧
- **INSERTタイミング**：AP-16（未登録時のみ）
- **SELECTタイミング**：AP-16（既存確認）、AP-18
- **DELETEタイミング**：AP-17
- **制約**：`uk_favorites_user_event UNIQUE(user_id, event_id)`（重複防止をDB制約でも保証、アプリ側の`findByUser_IdAndEvent_Id`チェックと二重防御）。`fk_favorites_event ON DELETE CASCADE`／`fk_favorites_user ON DELETE RESTRICT`。
- **結果**：✓ 確認済み

## 6. event_comments（イベントコメント）

- **用途**：イベントに対するコメント（機能追加）。編集機能なし（投稿・削除のみ）。
- **Entity**：`entity/EventComment.java`（`isOwnedBy(userId)`で削除可否判定用の本人確認）
- **Repository**：`repository/EventCommentRepository.java`
  - `findByEvent_IdOrderByCreatedAtAscIdAsc`：AP-19一覧（`created_at`が秒単位のため同一秒内の順序を`id`で確定させる設計、コメントに明記）
- **INSERTタイミング**：AP-20
- **SELECTタイミング**：AP-19
- **DELETEタイミング**：AP-21（投稿者本人または管理者のみ、`EventCommentService.delete()`のロールチェック後）
- **UPDATEタイミング**：無し（編集機能自体が存在しない、テーブル定義書§4.6の記載と一致）
- **インデックス**：`idx_event_comments_event_created`（`event_id, created_at`複合）
- **制約**：追加の一意制約等は無し（テーブル定義書と一致）。`fk_event_comments_event ON DELETE CASCADE`／`fk_event_comments_user ON DELETE RESTRICT`。
- **結果**：✓ 確認済み

---

## テーブル間リレーションの実装確認

| 親 | 子 | 定義書の方針 | schema.sqlでの実装 | 結果 |
|---|---|---|---|---|
| users | applications/favorites/event_comments | RESTRICT | `ON DELETE RESTRICT`（3箇所） | ✓ |
| events | ticket_types/applications/favorites/event_comments | CASCADE | `ON DELETE CASCADE`（4箇所） | ✓ |
| ticket_types | applications | RESTRICT | `fk_applications_ticket_type ... ON DELETE RESTRICT` | ✓ |

events→子テーブルのCASCADEは、イベントの物理削除がAPI経由では発生しない（論理削除のみ）ため、通常運用では発火しない設定である点はテーブル定義書の備考と実装コメント（`schema.sql`）の双方に明記されており、整合している。

## 初期データ（`db/seed.sql`）

- 固定ユーザー2件（id=1一般／id=2管理者）：`AuthInterceptor`のダミー認証、ログイン画面（`general@example.com`/`admin@example.com`）で使用する前提とREADMEの記載が一致。
- サンプルイベント約50件＋定員1名の動作確認用イベント1件：カテゴリ・開催日時を分散させた学内サークル・イベント運営を想定した内容（要件定義書§3/§4の想定利用シーンと整合）。
- 参加区分・お気に入り・コメントの初期データは無し（0件から機能確認する設計）。
- **結果**：✓ 確認済み（内容を読解し、README記載のログイン用メールアドレスと整合することを確認）
