# 31_API詳細設計書

## 1. 概要

本書は、`docs/20_基本設計/23_API基本設計書.md`に定めた各API（AP-010〜AP-900）について、リクエスト・レスポンスの項目、各項目とデータベースのカラムとの対応、処理の要点、発生するエラーを定義する詳細設計書である。

- 全APIに共通する仕様（認証、エラーレスポンスの形式、区分値の返し方、日時の形式）は`docs/20_基本設計/23_API基本設計書.md`2章に定める。
- 処理の順序・判定・排他制御は`docs/30_詳細設計/32_処理詳細設計書.md`、エラーIDとメッセージは`docs/30_詳細設計/33_共通詳細設計書.md`、テーブル・カラムは`docs/20_基本設計/22_テーブル定義書.md`に定める。
- 全APIで共通して発生するエラー（認証エラーE-A-001、リクエスト形式エラーE-V-022・E-V-023、リクエスト過多E-R-001、システムエラーE-S-001）は、各APIの「エラー」には記載しない。

### 1.1 記載ルール

| 表記 | 意味 |
|---|---|
| 型 | 文字列／数値／真偽値／日時（`yyyy-MM-ddTHH:mm:ss`）／配列 |
| 設定先（リクエスト） | その項目の値を保存するカラム。保存しない項目は用途を記載する |
| 取得元（レスポンス） | その項目の値の出所となるカラム。「計算値」はカラムをそのまま返すのではなく、記載の方法で算出することを表す |
| `テーブル.カラム` | `docs/20_基本設計/22_テーブル定義書.md`のテーブル名とカラム名 |

## 2. 共通のデータ構造

複数のAPIが同じ構造を返す場合は、ここで定義した名称で参照する。

### 2.1 利用者情報

| 項目 | 型 | 取得元 |
|---|---|---|
| `userId` | 数値 | `users.id` |
| `name` | 文字列 | `users.name` |
| `email` | 文字列 | `users.email` |
| `roleCode` | 数値 | `users.role_code` |
| `roleName` | 文字列 | `roles.name`（`users.role_code`に対応する行） |
| `anonymizedAt` | 日時 | `users.anonymized_at`。未退会の場合はnull |

パスワード・パスワードのハッシュ値は含めない。

### 2.2 イベント概要

削除済みのイベント（`events.deleted_at`に値がある）は、特記の無い限り対象外とする。

| 項目 | 型 | 取得元 |
|---|---|---|
| `id` | 数値 | `events.id` |
| `name` | 文字列 | `events.name` |
| `startAt` | 日時 | `events.start_at` |
| `place` | 文字列 | `events.place` |
| `capacity` | 数値 | `events.capacity` |
| `applicationDeadline` | 日時 | `events.application_deadline` |
| `acceptedCount` | 数値 | 計算値：`applications`のうち`event_id`が対象イベントで`status_code`が1（受付済）の件数 |
| `open` | 真偽値 | 計算値：現在時刻が`events.application_deadline`以前、かつ`events.start_at`より前であれば真 |
| `organizerName` | 文字列 | `events.organizer_name`。未設定の場合はnull |
| `imageUrl` | 文字列 | `events.image_url`。未設定の場合はnull |
| `favoriteCount` | 数値 | 計算値：`favorites`のうち`event_id`が対象イベントの件数 |

### 2.3 参加区分

| 項目 | 型 | 取得元 |
|---|---|---|
| `id` | 数値 | `ticket_types.id` |
| `name` | 文字列 | `ticket_types.name` |
| `capacity` | 数値 | `ticket_types.capacity` |
| `acceptedCount` | 数値 | 計算値：`applications`のうち`ticket_type_id`が対象の区分で`status_code`が1（受付済）の件数 |
| `remaining` | 数値 | 計算値：`capacity` − `acceptedCount` |

### 2.4 イベント詳細

イベント概要（2.2）の全項目に、次の項目を加えたもの。

| 項目 | 型 | 取得元 |
|---|---|---|
| `description` | 文字列 | `events.description`。未設定の場合はnull |
| `remaining` | 数値 | 計算値：`capacity` − `acceptedCount` |
| `extraQuestion` | 文字列 | `events.extra_question`。未設定の場合はnull |
| `ticketTypes` | 配列 | 参加区分（2.3）の配列。`ticket_types`のうち`event_id`が対象イベントの行。区分が無い場合は空の配列 |

### 2.5 申込一覧の要素

| 項目 | 型 | 取得元 |
|---|---|---|
| `id` | 数値 | `applications.id` |
| `eventId` | 数値 | `applications.event_id` |
| `eventName` | 文字列 | `events.name`（`applications.event_id`のイベント） |
| `startAt` | 日時 | `events.start_at` |
| `statusCode` | 数値 | `applications.status_code` |
| `statusName` | 文字列 | `application_statuses.name`（`applications.status_code`に対応する行） |
| `appliedAt` | 日時 | `applications.applied_at` |
| `waitlistRank` | 数値 | 計算値：`status_code`が2（キャンセル待ち）の場合のみ、同じイベント（参加区分がある場合は同じ区分）で自分より`applied_at`が古いキャンセル待ちの件数＋1。それ以外はnull |

### 2.6 お気に入りイベント

イベント概要（2.2）の項目のうち`favoriteCount`を除いたものに、次の項目を加えたもの。削除済みのイベントも対象に含める。

| 項目 | 型 | 取得元 |
|---|---|---|
| `favoritedAt` | 日時 | `favorites.created_at` |

### 2.7 コメント

| 項目 | 型 | 取得元 |
|---|---|---|
| `id` | 数値 | `event_comments.id` |
| `userName` | 文字列 | `users.name`（`event_comments.user_id`の利用者） |
| `body` | 文字列 | `event_comments.body`。論理削除済みの場合は固定の文言「このコメントは削除されました」 |
| `createdAt` | 日時 | `event_comments.created_at` |
| `mine` | 真偽値 | 計算値：`event_comments.user_id`がログイン中の利用者IDと一致すれば真 |
| `parentCommentId` | 数値 | `event_comments.parent_comment_id`。返信でない場合はnull |
| `deleted` | 真偽値 | 計算値：`event_comments.deleted_at`に値があれば真 |

## 3. 認証・アカウント（010番台）

### AP-010 ログイン `POST /api/login`

- 機能：F-010／画面：SC-010／認証：不要

| 項目 | 型 | 必須 | 桁数・形式 | 設定先 |
|---|---|:--:|---|---|
| `email` | 文字列 | ○ | 最大255文字、メール形式 | 保存しない。前後の空白を除去し小文字にして`users.email`と照合する |
| `password` | 文字列 | ○ | 最大72文字 | 保存しない。`users.password_hash`と照合する |

- レスポンス（200）：利用者情報（2.1）。
- エラー：E-V-001、E-V-024（入力エラー）。E-A-006（401。該当する利用者が無い、退会済み、パスワードの不一致のいずれも同じ）。

### AP-011 利用者登録 `POST /api/users`

- 機能：F-011／画面：SC-010／認証：不要

| 項目 | 型 | 必須 | 桁数・形式 | 設定先 |
|---|---|:--:|---|---|
| `name` | 文字列 | ○ | 最大100文字 | `users.name` |
| `email` | 文字列 | ○ | 最大255文字、メール形式 | `users.email`（前後の空白を除去し小文字にして保存） |
| `password` | 文字列 | ○ | 8〜72文字。英字と数字を各1文字以上。半角の英字・数字・記号 | `users.password_hash`（BCryptでハッシュ化して保存） |

- 処理：`users.role_code`は常に1（一般利用者）を設定する。リクエストに利用者区分が含まれていても無視する。
- レスポンス（201）：登録した利用者の利用者情報（2.1）。
- エラー：E-V-002、E-V-003、E-V-025（入力エラー）。E-B-015（400。メールアドレスが登録済み。メッセージは重複を直接伝えない）。

### AP-012 パスワード変更 `PUT /api/my/password`

- 機能：F-012／画面：SC-011／認可：全利用者（本人のパスワードのみ）

| 項目 | 型 | 必須 | 桁数・形式 | 設定先 |
|---|---|:--:|---|---|
| `currentPassword` | 文字列 | ○ | 最大72文字 | 保存しない。ログイン中の利用者の`users.password_hash`と照合する |
| `newPassword` | 文字列 | ○ | 8〜72文字。英字と数字を各1文字以上。半角の英字・数字・記号 | `users.password_hash`（BCryptでハッシュ化して上書き） |

- 処理：対象の利用者は認証情報から特定する（リクエストでは指定しない）。
- レスポンス（204）：本文なし。
- エラー：E-V-026、E-V-027（入力エラー）。E-B-023（400。現在のパスワードが一致しない）。

### AP-013 利用者の退会（匿名化） `DELETE /api/users/{id}`

- 機能：F-013／画面：SC-030、SC-141／認可：本人または管理者
- パス：`id`＝対象の利用者ID（`users.id`）。リクエスト本文なし。
- 処理：対象の利用者の行を次のとおり更新する（行は削除しない）。

  | カラム | 設定する値 |
  |---|---|
  | `users.name` | 固定の文言「退会済み利用者」 |
  | `users.email` | `withdrawn-{利用者ID}@invalid.example` |
  | `users.password_hash` | NULL |
  | `users.anonymized_at` | 実行時の日時 |

- レスポンス（200）：更新後の利用者情報（2.1）。
- エラー：E-B-018（404。利用者が存在しない）、E-B-021（400。対象が管理者）、E-B-022（400。退会済み）、E-A-007（403。本人でも管理者でもない）。
- 退会後、対象の利用者IDによるAPI呼び出しは全て認証エラー（E-A-001）となる。

### AP-014 ログイン中利用者情報取得 `GET /api/whoami`

- 動作確認用／認可：全利用者
- レスポンス（200）：

  | 項目 | 型 | 取得元 |
  |---|---|---|
  | `userId` | 数値 | `users.id`（ログイン中の利用者） |
  | `name` | 文字列 | `users.name` |
  | `roleCode` | 数値 | `users.role_code` |
  | `roleName` | 文字列 | `roles.name` |
  | `admin` | 真偽値 | 計算値：`users.role_code`が2（管理者）であれば真 |

## 4. イベントの閲覧（020番台）

### AP-020 イベント一覧取得 `GET /api/events`

- 機能：F-020、F-110、F-120／画面：SC-020、SC-021、SC-110、SC-120／認可：全利用者

| クエリ | 型 | 必須 | 値 | 用途 |
|---|---|:--:|---|---|
| `status` | 文字列 | - | `all`（省略時）／`open` | `open`の場合、受付中（`open`が真）のイベントのみ返す |

- レスポンス（200）：イベント概要（2.2）の配列。`events.start_at`の昇順。
- 削除済みのイベントは、`status`の指定によらず対象外とする。
- 並び替え（申込数順等）・絞り込み（キーワード等）は提供しない。画面が取得結果に対して行う。

### AP-021 イベント詳細取得 `GET /api/events/{id}`

- 機能：F-021、F-120、F-122／画面：SC-020、SC-022、SC-120、SC-121、SC-123／認可：全利用者
- パス：`id`＝イベントID（`events.id`）。
- レスポンス（200）：イベント詳細（2.4）。
- エラー：E-B-001（404。存在しない、または削除済み）。

## 5. 申込（030番台）

### AP-030 イベント申込 `POST /api/applications`

- 機能：F-022／画面：SC-020、SC-022／認可：一般利用者のみ

| 項目 | 型 | 必須 | 桁数・形式 | 設定先 |
|---|---|:--:|---|---|
| `eventId` | 数値 | ○ | - | `applications.event_id` |
| `ticketTypeId` | 数値 | ○（参加区分が設定されたイベントの場合） | 対象イベントの参加区分のID | `applications.ticket_type_id`。区分の無いイベントでは指定しない（NULLで登録する）。区分の無いイベントに指定した場合は、対象イベントに存在しない区分としてエラー（E-B-005）にする |
| `extraAnswer` | 文字列 | - | 最大500文字 | `applications.extra_answer` |

- 処理：申込者（`applications.user_id`）は認証情報から特定し、リクエストでは指定しない。`applications.applied_at`には実行時の日時を設定する。`applications.status_code`は、対象（参加区分がある場合はその区分、無い場合はイベント全体）の受付済の件数が定員未満なら1（受付済）、定員に達していれば2（キャンセル待ち）とする。定員の判定から登録までは排他制御を行う（`docs/30_詳細設計/32_処理詳細設計書.md`7章）。
- レスポンス（201）：

  | 項目 | 型 | 取得元 |
  |---|---|---|
  | `id` | 数値 | `applications.id` |
  | `eventId` | 数値 | `applications.event_id` |
  | `ticketTypeId` | 数値 | `applications.ticket_type_id` |
  | `userId` | 数値 | `applications.user_id` |
  | `statusCode` | 数値 | `applications.status_code` |
  | `statusName` | 文字列 | `application_statuses.name` |
  | `appliedAt` | 日時 | `applications.applied_at` |

- エラー：E-A-005（403。管理者による申込）、E-V-004、E-V-006（入力エラー）、E-B-001（404。イベントが無い）、E-B-002（400。受付期間外）、E-B-003（400。有効な申込が既にある）、E-B-004（400。区分が未選択）、E-B-005（404。区分が対象イベントに無い）。

### AP-031 自分の申込一覧取得 `GET /api/my/applications`

- 機能：F-023／画面：SC-030／認可：全利用者（本人分）
- レスポンス（200）：申込一覧の要素（2.5）の配列。`applications`のうち`user_id`がログイン中の利用者の行。キャンセル済を含む全件。`applications.applied_at`の降順。

### AP-032 申込キャンセル `DELETE /api/applications/{id}`

- 機能：F-023／画面：SC-030／認可：全利用者（本人の申込のみ）
- パス：`id`＝申込ID（`applications.id`）。
- 処理：対象の申込の`applications.status_code`を9（キャンセル済）に更新する（行は削除しない）。更新前が1（受付済）であった場合、同じイベント（参加区分がある場合は同じ区分）で`status_code`が2（キャンセル待ち）の申込のうち`applied_at`が最も古い1件を、1（受付済）に更新する。この一連の処理は排他制御を行う。
- レスポンス（204）：本文なし。
- エラー：E-B-009（404。申込が無い）、E-A-003（403。本人の申込でない）、E-B-010（400。キャンセル済、または開催後）。

## 6. お気に入り（040番台）

### AP-040 お気に入り登録 `POST /api/favorites`

- 機能：F-030／画面：SC-020、SC-021、SC-022／認可：全利用者

| 項目 | 型 | 必須 | 桁数・形式 | 設定先 |
|---|---|:--:|---|---|
| `eventId` | 数値 | ○ | - | `favorites.event_id` |

- 処理：`favorites.user_id`にはログイン中の利用者IDを設定する。同じ利用者・同じイベントの行が既にある場合は、新たに登録せず既存の行を返す。
- レスポンス：新規登録の場合は201、登録済みの場合は200。本文は`{ id, eventId, createdAt }`（それぞれ`favorites.id`・`favorites.event_id`・`favorites.created_at`）。
- エラー：E-V-007（入力エラー）、E-B-001（404。イベントが無い、または削除済み）。

### AP-041 お気に入り解除 `DELETE /api/favorites/{eventId}`

- 機能：F-030／画面：SC-020、SC-021、SC-022、SC-030／認可：全利用者（本人分）
- パス：`eventId`＝イベントID。
- 処理：`favorites`のうち、`user_id`がログイン中の利用者で`event_id`が指定のイベントの行を削除する（物理削除）。該当する行が無い場合もエラーとしない。
- レスポンス（204）：本文なし。

### AP-042 お気に入り一覧取得 `GET /api/my/favorites`

- 機能：F-030／画面：SC-020、SC-021、SC-022、SC-030／認可：全利用者（本人分）
- レスポンス（200）：お気に入りイベント（2.6）の配列。`favorites`のうち`user_id`がログイン中の利用者の行。`favorites.created_at`の降順。削除済みのイベントに対する登録も含める。

## 7. コメント（050番台）

### AP-050 コメント一覧取得 `GET /api/events/{id}/comments`

- 機能：F-031／画面：SC-022／認可：全利用者
- パス：`id`＝イベントID。
- レスポンス（200）：コメント（2.7）の配列。`event_comments`のうち`event_id`が対象イベントの行（論理削除済みを含む）。`event_comments.created_at`の昇順。返信も同じ配列に含め、親子関係は`parentCommentId`で表す（木構造への組み立ては画面が行う）。
- エラー：E-B-001（404。イベントが無い、または削除済み）。

### AP-051 コメント投稿・返信 `POST /api/events/{id}/comments`

- 機能：F-031／画面：SC-022／認可：全利用者
- パス：`id`＝イベントID（`event_comments.event_id`に設定）。

| 項目 | 型 | 必須 | 桁数・形式 | 設定先 |
|---|---|:--:|---|---|
| `body` | 文字列 | ○ | 最大500文字 | `event_comments.body` |
| `parentCommentId` | 数値 | - | 対象イベントのコメントのID | `event_comments.parent_comment_id`。指定しない場合はNULL（通常の投稿） |

- 処理：`event_comments.user_id`にはログイン中の利用者IDを設定する。受付期間の内外を問わず投稿できる。論理削除済みのコメントも返信先に指定できる。
- レスポンス（201）：登録したコメント（2.7）。
- エラー：E-V-008（入力エラー）、E-B-001（404。イベントが無い）、E-B-016（404。返信先のコメントが無い、または別のイベントのコメント）。

### AP-052 コメント削除 `DELETE /api/comments/{id}`

- 機能：F-031、F-150／画面：SC-022、SC-150／認可：投稿者本人または管理者
- パス：`id`＝コメントID（`event_comments.id`）。
- 処理：対象のコメントを`parent_comment_id`に持つ行（返信）が1件も無い場合は、行を削除する（物理削除）。1件以上ある場合は、行を残して`event_comments.deleted_at`に実行時の日時を設定する（論理削除）。
- レスポンス（204）：本文なし（物理削除・論理削除とも同じ）。
- エラー：E-B-011（404。コメントが無い）、E-A-004（403。投稿者本人でも管理者でもない）。

## 8. 管理：イベント・当日受付（120番台）

### AP-120 イベント登録 `POST /api/events`

- 機能：F-120／画面：SC-121／認可：管理者のみ

| 項目 | 型 | 必須 | 桁数・形式 | 設定先 |
|---|---|:--:|---|---|
| `name` | 文字列 | ○ | 最大100文字 | `events.name` |
| `startAt` | 日時 | ○ | 未来の日時 | `events.start_at` |
| `place` | 文字列 | ○ | 最大100文字 | `events.place` |
| `capacity` | 数値 | ○（`ticketTypes`が無い場合） | 1以上 | `events.capacity`。`ticketTypes`を1件以上指定した場合は、区分の定員の合計を設定する |
| `applicationDeadline` | 日時 | ○ | `startAt`より前 | `events.application_deadline` |
| `description` | 文字列 | - | 最大1000文字 | `events.description` |
| `organizerName` | 文字列 | - | 最大100文字 | `events.organizer_name` |
| `imageUrl` | 文字列 | - | 最大500文字。`http://`または`https://`で始まる | `events.image_url` |
| `extraQuestion` | 文字列 | - | 最大200文字 | `events.extra_question` |
| `ticketTypes` | 配列 | - | 0件以上 | `ticket_types`に、要素ごとに1行を登録する（`event_id`は登録したイベントのID） |
| `ticketTypes[].name` | 文字列 | ○ | 最大50文字。同じリクエスト内で重複不可 | `ticket_types.name` |
| `ticketTypes[].capacity` | 数値 | ○ | 1以上 | `ticket_types.capacity` |

- レスポンス（201）：登録したイベントのイベント詳細（2.4）。
- エラー：E-A-002（403。管理者でない）、E-V-010〜E-V-021（入力エラー）、E-B-006（400。定員も参加区分も指定が無い）、E-B-008（400。区分名の重複）。

### AP-121 イベント更新 `PUT /api/events/{id}`

- 機能：F-120／画面：SC-121／認可：管理者のみ
- パス：`id`＝イベントID。リクエストの項目・設定先はAP-120と同じ。
- 参加区分の扱い：

  | `ticketTypes`の指定 | 処理 |
  |---|---|
  | 指定しない | `ticket_types`の対象イベントの行を変更しない。既存の区分がある場合、`events.capacity`は既存の区分の定員の合計とする |
  | 空の配列 | `ticket_types`の対象イベントの行を全て削除する。`capacity`の指定が必須となる |
  | 1件以上 | `ticket_types`の対象イベントの行を全て削除し、指定された内容で登録し直す。`events.capacity`は区分の定員の合計とする |

- レスポンス（200）：更新後のイベント詳細（2.4）。
- エラー：AP-120のエラーに加え、E-B-001（404。イベントが無い、または削除済み）、E-B-007（400。有効な申込が残っている区分の変更、または有効な申込があるイベントへの区分の追加）、E-B-017（400。キャンセル済の申込が参照している区分の変更）。

### AP-122 イベント削除 `DELETE /api/events/{id}`

- 機能：F-121／画面：SC-120／認可：管理者のみ
- 処理：`events.deleted_at`に実行時の日時を設定する（論理削除。関連する申込・お気に入り・コメント・参加区分は変更しない）。
- レスポンス（204）：本文なし。
- エラー：E-A-002（403）、E-B-001（404。イベントが無い、または削除済み）、E-B-013（400。`status_code`が1（受付済）の申込が1件以上ある）。

### AP-123 削除済みイベント一覧取得 `GET /api/events/deleted`

- 機能：F-121、F-120（複製）／画面：SC-122／認可：管理者のみ
- レスポンス（200）：`events`のうち`deleted_at`に値がある行の配列。`events.start_at`の昇順。

  | 項目 | 型 | 取得元 |
  |---|---|---|
  | `id`、`name`、`startAt`、`place`、`capacity` | - | イベント概要（2.2）と同じ |
  | `deletedAt` | 日時 | `events.deleted_at` |
  | `description`、`organizerName`、`imageUrl`、`extraQuestion` | 文字列 | `events`の各カラム（複製用） |
  | `ticketTypes` | 配列 | 参加区分（2.3）の配列（複製用） |

- エラー：E-A-002（403）。

### AP-124 イベント復元 `POST /api/events/{id}/restore`

- 機能：F-121／画面：SC-122／認可：管理者のみ
- 処理：`events.deleted_at`をNULLに戻す。
- レスポンス（200）：復元後のイベント詳細（2.4）。
- エラー：E-A-002（403）、E-B-014（404。イベントが無い、または削除済みでない）。

### AP-125 申込者一覧取得 `GET /api/events/{id}/attendees`

- 機能：F-122／画面：SC-123／認可：管理者のみ
- レスポンス（200）：`applications`のうち`event_id`が対象イベントの行の配列（申込状況を問わず全件）。`applications.applied_at`の昇順。

  | 項目 | 型 | 取得元 |
  |---|---|---|
  | `applicationId` | 数値 | `applications.id` |
  | `userName` | 文字列 | `users.name`（`applications.user_id`の利用者） |
  | `ticketTypeName` | 文字列 | `ticket_types.name`（`applications.ticket_type_id`の区分）。区分が無い場合はnull |
  | `statusCode` | 数値 | `applications.status_code` |
  | `statusName` | 文字列 | `application_statuses.name` |
  | `checkedInAt` | 日時 | `applications.checked_in_at`。未実施の場合はnull |
  | `extraAnswer` | 文字列 | `applications.extra_answer`。未回答の場合はnull |

- エラー：E-A-002（403）、E-B-001（404。イベントが無い、または削除済み）。

### AP-126 チェックイン `PUT /api/applications/{id}/check-in`

- 機能：F-122／画面：SC-123／認可：管理者のみ
- 処理：`applications.checked_in_at`に実行時の日時を設定する。既に値がある場合も、最新の実行時刻で上書きする（再実行の確認は画面が行う）。
- レスポンス（200）：`{ applicationId, checkedInAt }`（`applications.id`・`applications.checked_in_at`）。
- エラー：E-A-002（403）、E-B-009（404。申込が無い）、E-B-012（400。`status_code`が1（受付済）でない）。

## 9. 管理：実績・集計（130番台）

### AP-130 申込実績取得 `GET /api/reports/applications`

- 機能：F-130、F-110／画面：SC-130、SC-110／認可：管理者のみ

| クエリ | 型 | 必須 | 値 | 用途 |
|---|---|:--:|---|---|
| `format` | 文字列 | - | `json`（省略時）／`csv` | レスポンスの形式 |
| `sort` | 文字列 | - | `startAt`（省略時）／`accepted_desc` | `json`の並び順。`startAt`は開催日時の昇順、`accepted_desc`は受付済の件数の降順。`csv`では開催日時順に固定 |

- レスポンス（200、`format=json`）：削除されていないイベントごとに1要素の配列。

  | 項目 | 型 | 取得元 |
  |---|---|---|
  | `eventId` | 数値 | `events.id` |
  | `eventName` | 文字列 | `events.name` |
  | `startAt` | 日時 | `events.start_at` |
  | `capacity` | 数値 | `events.capacity` |
  | `acceptedCount` | 数値 | 計算値：イベント概要（2.2）の`acceptedCount`と同じ |
  | `fillRate` | 数値 | 計算値：`acceptedCount` ÷ `capacity`（小数第2位まで。第3位を四捨五入） |

- レスポンス（200、`format=csv`）：`Content-Type: text/csv;charset=UTF-8`。文字コードはUTF-8（BOM付き）。削除されていないイベントの申込1件につき1行（申込状況を問わず全件）。イベントの開催日時順。

  | 列（ヘッダ） | 取得元 |
  |---|---|
  | イベント名 | `events.name` |
  | 申込者名 | `users.name`（`applications.user_id`の利用者） |
  | 申込日時 | `applications.applied_at` |
  | ステータス | `application_statuses.name`（`applications.status_code`に対応する行） |
  | アンケート回答 | `applications.extra_answer`。未回答は空欄 |
  | 参加区分 | `ticket_types.name`。区分が無い場合は空欄 |

  - 値にカンマ・改行・ダブルクォートを含む場合は、ダブルクォートで囲む（値の中のダブルクォートは2つ重ねる）。
  - 値の先頭が`=`・`+`・`-`・`@`・タブ・復帰のいずれかの場合は、先頭に`'`を付ける。
- エラー：E-A-002（403）。

### AP-131 お気に入り総数取得 `GET /api/favorites/count`

- 機能：F-110／画面：SC-110／認可：管理者のみ
- レスポンス（200）：`{ count }`。計算値：`favorites`の全件数（削除済みのイベントに対する登録を含む）。
- エラー：E-A-002（403）。

### AP-132 コメント総数取得 `GET /api/comments/count`

- 機能：F-110／画面：SC-110／認可：管理者のみ
- レスポンス（200）：`{ count }`。計算値：`event_comments`のうち`deleted_at`がNULLの件数。
- エラー：E-A-002（403）。

## 10. 管理：利用者（140番台）

### AP-140 利用者一覧取得 `GET /api/users`

- 機能：F-140、F-110／画面：SC-140、SC-110／認可：管理者のみ
- レスポンス（200）：利用者情報（2.1）の配列。`users`の全行（退会済みを含む）。`users.id`の昇順。
- エラー：E-A-002（403）。

### AP-141 利用者情報取得 `GET /api/users/{id}`

- 機能：F-140／画面：SC-141／認可：管理者のみ
- レスポンス（200）：`id`で指定した利用者の利用者情報（2.1）。
- エラー：E-A-002（403）、E-B-018（404。利用者が無い）。

### AP-142 利用者の申込一覧取得 `GET /api/users/{id}/applications`

- 機能：F-140／画面：SC-141／認可：管理者のみ
- レスポンス（200）：申込一覧の要素（2.5）の配列。`applications`のうち`user_id`が指定の利用者の行（キャンセル済を含む全件）。`applications.applied_at`の降順。
- エラー：E-A-002（403）、E-B-018（404）。

### AP-143 利用者のお気に入り一覧取得 `GET /api/users/{id}/favorites`

- 機能：F-140／画面：SC-141／認可：管理者のみ
- レスポンス（200）：お気に入りイベント（2.6）の配列。`favorites`のうち`user_id`が指定の利用者の行。`favorites.created_at`の降順。
- エラー：E-A-002（403）、E-B-018（404）。

### AP-144 利用者のコメント履歴取得 `GET /api/users/{id}/comments`

- 機能：F-140／画面：SC-141／認可：管理者のみ
- レスポンス（200）：`event_comments`のうち`user_id`が指定の利用者の行の配列（返信・論理削除済みを含む）。`event_comments.created_at`の降順。

  | 項目 | 型 | 取得元 |
  |---|---|---|
  | `id` | 数値 | `event_comments.id` |
  | `eventId` | 数値 | `event_comments.event_id` |
  | `eventName` | 文字列 | `events.name` |
  | `body` | 文字列 | `event_comments.body`。論理削除済みの場合は固定の文言「このコメントは削除されました」 |
  | `createdAt` | 日時 | `event_comments.created_at` |
  | `deleted` | 真偽値 | 計算値：`event_comments.deleted_at`に値があれば真 |

- エラー：E-A-002（403）、E-B-018（404）。

### AP-145 管理者アカウント登録 `POST /api/admins`

- 機能：F-141／画面：SC-140／認可：管理者のみ
- リクエスト：AP-011と同じ項目（`name`、`email`、`password`）。設定先も同じ。
- 処理：`users.role_code`は常に2（管理者）を設定する。
- レスポンス（201）：登録した利用者の利用者情報（2.1）。
- エラー：E-A-002（403）、E-V-002、E-V-003、E-V-025（入力エラー）、E-B-015（400。メールアドレスが登録済み。メッセージは重複をそのまま伝える）。

### AP-146 管理者権限の降格 `PUT /api/users/{id}/demote`

- 機能：F-142／画面：SC-141／認可：管理者のみ
- 処理：対象の利用者の`users.role_code`を1（一般利用者）に更新する。
- レスポンス（200）：更新後の利用者情報（2.1）。
- エラー：E-A-002（403）、E-B-018（404）、E-B-019（400。既に一般利用者）、E-B-020（400。`role_code`が2の利用者が対象の1人のみ）。

## 11. 管理：コメント（150番台）

### AP-150 全コメント一覧取得 `GET /api/comments`

- 機能：F-150／画面：SC-150／認可：管理者のみ
- レスポンス（200）：`event_comments`のうち`deleted_at`がNULLの行の配列（全イベント）。`event_comments.created_at`の降順。

  | 項目 | 型 | 取得元 |
  |---|---|---|
  | `id` | 数値 | `event_comments.id` |
  | `eventId` | 数値 | `event_comments.event_id` |
  | `eventName` | 文字列 | `events.name` |
  | `userName` | 文字列 | `users.name`（`event_comments.user_id`の利用者） |
  | `body` | 文字列 | `event_comments.body` |
  | `createdAt` | 日時 | `event_comments.created_at` |

- エラー：E-A-002（403）。削除はAP-052を使用する。

## 12. システム共通（900番台）

### AP-900 稼働確認 `GET /api/ping`

- 動作確認用／認証：不要
- レスポンス（200）：文字列`pong`。データベースは参照しない。
