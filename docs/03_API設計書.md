# 03_API設計書

## 1. 概要

本書は、イベント申込システムのフロントエンドとバックエンドの間で行われるデータのやり取りを定義するAPI設計書である。`docs/01_要件定義書.md`の各機能を実現するために、システムが提供するAPIの仕様を定める。

- ベースURL：`/api`
- データ形式：リクエスト・レスポンスともにJSON形式とする（CSV出力を除く）。
- 通信方式：HTTPS/HTTPによるREST形式のAPIとする。

## 2. 共通仕様

### 2.1 認証

本システムは、ログイン時に確認された利用者を識別する情報を、以降の全てのAPIリクエストに付与することで利用者を認証する。パスワード・トークンによる認証は行わない。

- ログイン（`POST /api/login`）、利用者登録（`POST /api/users`）、稼働確認（`GET /api/ping`）は、認証情報が無くても呼び出せる。
- 上記以外の全APIは、利用者識別情報が付与されていない、または対応する利用者が存在しない場合、認証エラー（401）を返す。

### 2.2 認可

- 管理者専用のAPIに一般利用者からアクセスした場合、権限エラー（403）を返す。管理者専用のAPIは「3. API一覧」の認可列に示す。
- イベントへの申込（`POST /api/applications`）は一般利用者専用であり、管理者からのアクセスは権限エラー（403）とする。
- 自分自身の申込・お気に入り以外を操作しようとした場合も権限エラー（403）とする。

### 2.3 共通エラーレスポンス

エラー発生時は、以下の形式でレスポンスを返す。

```json
{
  "timestamp": "2026-09-24T17:04:23+09:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "認証が必要です",
  "errors": null
}
```

入力値エラーの場合は、`errors`にフィールドごとのエラー内容を配列で格納する。

```json
{
  "errors": [
    { "field": "name", "message": "名前を入力してください" }
  ]
}
```

### 2.4 エラー区分とHTTPステータス

| エラー区分 | HTTPステータス | 説明 |
|---|---|---|
| 認証エラー | 401 | 利用者を識別できない場合 |
| 権限エラー | 403 | 実行権限がない操作を要求した場合 |
| データ未検出エラー | 404 | 指定した対象（イベント・申込・区分・コメント等）が存在しない場合 |
| 業務ルール違反エラー | 400 | 申込締切超過、二重申込、削除不可等、業務ルールに反する場合 |
| 入力値エラー | 400 | 必須項目の未入力、桁数超過、形式不正等の場合 |
| リクエスト形式エラー | 400 | リクエストの形式が不正で処理できない場合（不正なJSON、パラメータの型不一致等） |

### 2.5 バリデーションと認可の優先順位

管理者専用のAPI（イベント登録・編集等）および一般利用者専用のAPI（申込）では、権限エラーの判定を入力値の検証より先に行う。したがって、権限を持たない利用者が不正な内容でリクエストを送った場合、返されるのは権限エラー（403）であり、入力値エラー（400）ではない。

## 3. API一覧

| ID | Method | URL | 概要 | 認証 | 認可 |
|---|---|---|---|---|---|
| AP-01 | POST | `/api/login` | ログイン | 不要 | - |
| AP-02 | POST | `/api/users` | 利用者登録 | 不要 | - |
| AP-03 | GET | `/api/users` | 利用者一覧取得 | 必要 | 管理者のみ |
| AP-04 | GET | `/api/events` | イベント一覧取得 | 必要 | 全利用者 |
| AP-05 | GET | `/api/events/{id}` | イベント詳細取得 | 必要 | 全利用者 |
| AP-06 | GET | `/api/events/deleted` | 削除済みイベント一覧取得 | 必要 | 管理者のみ |
| AP-07 | POST | `/api/events` | イベント登録 | 必要 | 管理者のみ |
| AP-08 | PUT | `/api/events/{id}` | イベント更新 | 必要 | 管理者のみ |
| AP-09 | DELETE | `/api/events/{id}` | イベント削除 | 必要 | 管理者のみ |
| AP-10 | POST | `/api/events/{id}/restore` | イベント復元 | 必要 | 管理者のみ |
| AP-11 | GET | `/api/events/{id}/attendees` | 申込者一覧取得（当日受付用） | 必要 | 管理者のみ |
| AP-12 | POST | `/api/applications` | イベント申込 | 必要 | 一般利用者のみ |
| AP-13 | GET | `/api/my/applications` | 自分の申込一覧取得 | 必要 | 全利用者（本人分） |
| AP-14 | DELETE | `/api/applications/{id}` | 申込キャンセル | 必要 | 全利用者（本人のみ） |
| AP-15 | PUT | `/api/applications/{id}/check-in` | チェックイン | 必要 | 管理者のみ |
| AP-16 | POST | `/api/favorites` | お気に入り登録 | 必要 | 全利用者 |
| AP-17 | DELETE | `/api/favorites/{eventId}` | お気に入り解除 | 必要 | 全利用者（本人分） |
| AP-18 | GET | `/api/my/favorites` | お気に入り一覧取得 | 必要 | 全利用者（本人分） |
| AP-19 | GET | `/api/events/{id}/comments` | コメント一覧取得 | 必要 | 全利用者 |
| AP-20 | POST | `/api/events/{id}/comments` | コメント投稿 | 必要 | 全利用者 |
| AP-21 | DELETE | `/api/comments/{id}` | コメント削除 | 必要 | 投稿者本人または管理者 |
| AP-22 | GET | `/api/reports/applications` | 申込実績取得（一覧・CSV） | 必要 | 管理者のみ |
| AP-23 | GET | `/api/whoami` | ログイン中利用者情報取得 | 必要 | - |
| AP-24 | GET | `/api/ping` | 稼働確認 | 不要 | - |
| AP-25 | POST | `/api/admins` | 管理者アカウント登録 | 必要 | 管理者のみ |

計25 API。各機能との対応は`docs/01_要件定義書.md`「5. システム機能」を参照。

## 4. API個別仕様

### AP-01 ログイン `POST /api/login`

- Request：`{ "email": string }`（必須、メール形式）
- Response（200）：`{ userId, name, email, role }`
- 異常時：該当する利用者が存在しない場合は401。

### AP-02 利用者登録 `POST /api/users`

- Request：`{ "name": string, "email": string }`（name：必須・最大100文字／email：必須・メール形式・最大255文字）
- Response（201）：`{ userId, name, email, role }`。`role`は常に`general`とする。
- 異常時：メールアドレスが登録済みの場合は400。

### AP-03 利用者一覧取得 `GET /api/users`

- Response（200）：`{ userId, name, email, role }`の配列（`userId`昇順）。

### AP-04 イベント一覧取得 `GET /api/events`

- Query：`status`（`all`｜`open`、省略時`all`）
- Response（200）：イベント概要（`id, name, startAt, place, capacity, applicationDeadline, acceptedCount, open, organizerName, imageUrl`）の配列（開催日時昇順）。
- `status=open`指定時は受付中のイベントのみを返す。
- `acceptedCount`は受付済申込件数を集計した値とする。
- 削除済みのイベントは、`status`の指定によらず常に対象外とする。

### AP-05 イベント詳細取得 `GET /api/events/{id}`

- Response（200）：一覧の項目に加え、`description, remaining, extraQuestion, ticketTypes[]`を含む。`remaining`は定員から受付済件数を差し引いた残り枠を表す。`ticketTypes`各要素は`{ id, name, capacity, acceptedCount, remaining }`。
- 異常時：対象が存在しない、または削除済みの場合は404。

### AP-06 削除済みイベント一覧取得 `GET /api/events/deleted`

- Response（200）：`{ id, name, startAt, place, capacity, deletedAt }`の配列。削除済みのイベントのみを対象とする。

### AP-07 イベント登録 `POST /api/events`

- Request：

  | フィールド | 型 | 必須 | 制約 |
  |---|---|---|---|
  | name | string | ○ | 最大100文字 |
  | startAt | 日時 | ○ | 未来日時 |
  | place | string | ○ | 最大100文字 |
  | capacity | number | ○ | 1以上 |
  | applicationDeadline | 日時 | ○ | `startAt`より前であること |
  | description | string | 任意 | 最大1000文字 |
  | organizerName | string | 任意 | 最大100文字 |
  | imageUrl | string | 任意 | 最大500文字 |
  | extraQuestion | string | 任意 | 最大200文字 |
  | ticketTypes | 配列 | 任意 | 各要素`{ name: 必須・最大50文字（イベント内で重複不可）, capacity: 必須・1以上 }` |

- Response（201）：イベント詳細（AP-05と同形式）。

### AP-08 イベント更新 `PUT /api/events/{id}`

- RequestはAP-07と同一形式。
- `ticketTypes`を指定しない場合は既存の参加区分を変更しない。空配列を指定した場合は既存の参加区分を全て解除する。参加区分を指定した場合、イベントの定員は区分の定員合計に自動的に同期する。
- 異常時：対象の参加区分に有効な申込が残っている状態での変更は400。同一イベント内で区分名が重複する場合は400。対象イベントが存在しない場合は404。

### AP-09 イベント削除 `DELETE /api/events/{id}`

- 論理削除（削除日時の設定）として処理する。
- 異常時：「受付済」の申込が1件でも存在する場合は400。対象が存在しない場合は404。
- 正常時：204。

### AP-10 イベント復元 `POST /api/events/{id}/restore`

- 削除済みのイベントのみを対象とする。
- Response（200）：復元後のイベント詳細。
- 異常時：対象が削除済みでない、または存在しない場合は404。

### AP-11 申込者一覧取得（当日受付用） `GET /api/events/{id}/attendees`

- Response（200）：`{ applicationId, userName, ticketTypeName, status, checkedInAt }`の配列（申込日時昇順）。状況を問わず全ての申込を対象とする。
- 異常時：対象イベントが存在しない場合は404。

### AP-12 イベント申込 `POST /api/applications`

- Request：`{ eventId: number（必須）, ticketTypeId: number（区分が設定されたイベントでは必須）, extraAnswer: string（任意・最大500文字） }`
- 申込者は認証情報から特定し、リクエスト本文には含めない。
- Response（201）：`{ id, eventId, ticketTypeId, userId, status, appliedAt }`
- 異常時：管理者による申込は403。受付期間外・区分未選択・二重申込は400。業務ルールの詳細は`docs/01_要件定義書.md`「7. 業務ルール」を参照。

### AP-13 自分の申込一覧取得 `GET /api/my/applications`

- Response（200）：`{ id, eventId, eventName, startAt, status, appliedAt, waitlistRank }`の配列（申込日時降順）。`waitlistRank`は状況が「キャンセル待ち」の場合のみ数値を設定し、それ以外はNULLとする。

### AP-14 申込キャンセル `DELETE /api/applications/{id}`

- 本人の申込のみを対象とする。
- 正常時：204。状況が「受付済」であった申込をキャンセルした場合、同一イベント（区分がある場合は同一区分）で最も申込日時が古い「キャンセル待ち」の申込を自動的に「受付済」へ繰り上げる。
- 異常時：他人の申込を指定した場合は403。キャンセル可能な状態にない場合は400。対象が存在しない場合は404。

### AP-15 チェックイン `PUT /api/applications/{id}/check-in`

- Response（200）：`{ applicationId, checkedInAt }`
- 状況が「受付済」の申込のみを対象とする。既にチェックイン済みの申込に対して再度実行した場合は、チェックイン日時を最新の実行時刻に更新する。再チェックインの実行可否を利用者に確認する画面上の制御は`docs/04_画面設計書.md`SC-11に定める。
- 異常時：状況が「受付済」以外の場合は400。

### AP-16 お気に入り登録 `POST /api/favorites`

- Request：`{ eventId: number }`（必須）
- 登録済みのイベントに対して実行した場合は、新規登録は行わず既存の登録内容を200で返す。未登録の場合は新規登録し201で返す。
- 異常時：対象イベントが存在しない場合は404。

### AP-17 お気に入り解除 `DELETE /api/favorites/{eventId}`

- 正常時：204。未登録のイベントに対して実行した場合もエラーとしない。

### AP-18 お気に入り一覧取得 `GET /api/my/favorites`

- Response（200）：イベント概要（AP-04と同項目）に`favoritedAt`を加えた配列（登録日時降順）。削除済みのイベントに対する登録も対象に含める。

### AP-19 コメント一覧取得 `GET /api/events/{id}/comments`

- Response（200）：`{ id, userName, body, createdAt, mine }`の配列（投稿日時昇順）。`mine`は要求元利用者本人の投稿かどうかを表す。
- 異常時：対象イベントが存在しない場合は404。

### AP-20 コメント投稿 `POST /api/events/{id}/comments`

- Request：`{ body: string }`（必須、最大500文字）
- Response（201）：投稿されたコメント。
- 受付期間の内外を問わず投稿できる。
- 異常時：対象イベントが存在しない場合は404。

### AP-21 コメント削除 `DELETE /api/comments/{id}`

- 投稿者本人、または管理者のみが実行できる。
- 正常時：204。
- 異常時：権限がない場合は403。対象が存在しない場合は404。

### AP-22 申込実績取得 `GET /api/reports/applications`

- Query：`format`（`json`｜`csv`、省略時`json`）、`sort`（`startAt`｜`accepted_desc`、省略時`startAt`。`format=csv`の場合は開催日時順に固定する）
- `format=json`のResponse（200）：`{ eventId, eventName, startAt, capacity, acceptedCount, fillRate }`の配列。`fillRate`は受付済件数を定員で除した充足率（小数第2位まで）。削除済みのイベントは対象外とする。
- `format=csv`のResponse（200）：`Content-Type: text/csv;charset=UTF-8`、ヘッダ行「イベント名,申込者名,申込日時,ステータス」。状況（受付済・キャンセル待ち・キャンセル済）を問わず全ての申込明細を開催日時順に出力するが、削除済みのイベントに紐づく申込は対象外とする。文字コードはUTF-8（BOM付き）とする。

### AP-23 ログイン中利用者情報取得 `GET /api/whoami`

- Response（200）：`{ userId, name, role, admin }`。`admin`は`role`が管理者であるかを表す真偽値とする。
- 開発・動作確認時に、現在の認証状態を確認する用途で使用する。

### AP-24 稼働確認 `GET /api/ping`

- Response（200）：`"pong"`（文字列）。
- システムの起動状態を確認する用途で使用する。認証を要しない。

### AP-25 管理者アカウント登録 `POST /api/admins`

- 認可：管理者のみ。
- Request：`{ "name": string, "email": string }`（name：必須・最大100文字／email：必須・メール形式・最大255文字）
- Response（201）：`{ userId, name, email, role }`。`role`は常に`admin`とする。
- 異常時：メールアドレスが登録済みの場合は400。一般利用者によるアクセスは403。
