package com.example.eventapp.controller;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.dto.AttendeeResponse;
import com.example.eventapp.dto.DeletedEventResponse;
import com.example.eventapp.dto.EventDetailResponse;
import com.example.eventapp.dto.EventSummaryResponse;
import com.example.eventapp.dto.EventUpsertRequest;
import com.example.eventapp.service.ApplicationService;
import com.example.eventapp.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 実行環境: サーバー側（JVM、localhost:8080）。
// イベント一覧・詳細API（AP-04・AP-05）、イベント登録・編集・削除API（AP-07〜09、管理者のみ）。
/**
 * イベント一覧・詳細取得（AP-04・AP-05）、削除済み一覧（AP-06）、登録・更新・削除・復元（AP-07〜10）、
 * 当日受付の申込者一覧（AP-11）のHTTP入口を担当するController。
 * フロントエンドのcore/event-api.ts（EventApiService）およびcore/checkin-api.ts（CheckInApiService#attendees）から呼ばれ、
 * 内部ではEventServiceの各メソッド、attendees()のみApplicationService#listAttendeesに処理を委譲する。
 * 他のController（ApplicationControllerなど）と同様、権限チェック・入力チェックをここで行い、
 * 実際の業務ロジックはService層に委ねる。
 */
@Tag(name = "イベント", description = "イベントの一覧・詳細・登録・編集・削除・復元、当日受付の申込者一覧（AP-04〜11）")
@RestController
public class EventController {

    private final EventService eventService;
    private final ApplicationService applicationService;
    private final AuthContext authContext;
    private final Validator validator;

    public EventController(EventService eventService, ApplicationService applicationService,
            AuthContext authContext, Validator validator) {
        this.eventService = eventService;
        this.applicationService = applicationService;
        this.authContext = authContext;
        this.validator = validator;
    }

    // AP-04 GET /api/events?status=all|open（既定all）
    /**
     * イベント一覧を取得する（AP-04）。フロントエンドのcore/event-api.ts（EventApiService#list）から呼ばれ、
     * EventService#listに処理を委譲する。認証は必要だが権限チェックは無く、一般利用者・管理者のどちらも呼び出せる。
     * {@code @RequestParam(defaultValue = "all")}は、クエリパラメータ（{@code ?status=...}）を受け取るアノテーションで、
     * リクエストにstatusが無い場合は既定値"all"を使う、という意味。
     *
     * @param status "all"（既定、全件）または"open"（受付中のみ）
     * @return イベント一覧（開催日時昇順）
     */
    @Operation(summary = "AP-04 イベント一覧取得",
            description = "開催日時昇順でイベント一覧を取得する。statusにopenを指定すると受付中のイベントのみに絞り込む。"
                    + "acceptedCountは受付済申込件数、favoriteCountはお気に入り登録件数（全利用者に返す）。削除済みイベントは常に対象外。")
    @GetMapping("/api/events")
    public List<EventSummaryResponse> list(@RequestParam(defaultValue = "all") String status) {
        // EventServiceに一覧の取得を依頼し（絞り込み条件としてstatusを渡す）、その結果をそのまま返す
        return eventService.list(status);
    }

    // AP-05 GET /api/events/{id}
    /**
     * 指定したイベントの詳細を取得する（AP-05）。フロントエンドのcore/event-api.ts（EventApiService#detail）から呼ばれ、
     * EventService#getDetailに処理を委譲する。
     *
     * @param id 対象イベントID
     * @return イベント詳細（残り枠・参加区分一覧等を含む）
     */
    @Operation(summary = "AP-05 イベント詳細取得",
            description = "指定したイベントの詳細情報（残り枠・申込締切・参加区分一覧・アンケート文言・お気に入り数等）を取得する。"
                    + "対象が存在しない、または削除済みの場合は404。")
    @GetMapping("/api/events/{id}")
    public EventDetailResponse detail(@PathVariable Long id) {
        // EventServiceに指定IDの詳細取得を依頼し、その結果をそのまま返す
        return eventService.getDetail(id);
    }

    // 機能追加（ソフトデリート） GET /api/events/deleted（管理者のみ）
    /**
     * 削除済み（論理削除）のイベント一覧を取得する（AP-06）。フロントエンドのcore/event-api.ts（EventApiService#listDeleted）
     * から呼ばれ、EventService#listDeletedに処理を委譲する。authContext.requireAdmin()により、
     * 管理者以外がアクセスすると403（Forbidden）になる。
     *
     * @return 削除済みイベント一覧
     */
    @Operation(summary = "AP-06 削除済みイベント一覧取得",
            description = "削除済み（論理削除）のイベント一覧を取得する。管理者のみ実行できる。")
    @GetMapping("/api/events/deleted")
    public List<DeletedEventResponse> listDeleted() {
        // ログイン中の利用者が管理者かどうかを確認する（管理者でなければ、ここで例外が投げられる）
        authContext.requireAdmin();
        // EventServiceに削除済み一覧の取得を依頼し、その結果をそのまま返す
        return eventService.listDeleted();
    }

    // AP-07 POST /api/events（管理者のみ）
    // 権限チェックを先に行うため@Validは使わず、権限チェック後に手動でバリデーションする
    /**
     * イベントを新規登録する（AP-07）。フロントエンドのcore/event-api.ts（EventApiService#create）から呼ばれ、
     * EventService#createに処理を委譲する。authContext.requireAdmin()により管理者以外は403になる。
     *
     * @param request 登録するイベント情報・参加区分（任意）
     * @return 登録されたイベントの詳細
     */
    @Operation(summary = "AP-07 イベント登録",
            description = "イベント情報および参加区分（任意）を新規登録する。参加区分を登録した場合、イベント全体の定員は区分の定員合計に同期する。"
                    + "管理者のみ実行できる。一般利用者によるアクセスは403、入力値エラーは400。")
    @PostMapping("/api/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventDetailResponse create(@RequestBody EventUpsertRequest request) {
        // ログイン中の利用者が管理者かどうかを確認する（管理者でなければ、ここで例外が投げられる）
        authContext.requireAdmin();
        // リクエスト内容を手動でバリデーションする（違反があれば、ここで例外が投げられる）
        validate(request);
        // EventServiceに登録処理を依頼し、その結果（登録されたイベント詳細）をそのまま返す
        return eventService.create(request);
    }

    // AP-08 PUT /api/events/{id}（管理者のみ）
    /**
     * イベント情報・参加区分を編集する（AP-08）。フロントエンドのcore/event-api.ts（EventApiService#update）から呼ばれ、
     * EventService#updateに処理を委譲する。authContext.requireAdmin()により管理者以外は403になる。
     *
     * @param id      編集対象イベントID
     * @param request 編集後の内容（参加区分は常に全体を置き換える）
     * @return 編集後のイベント詳細
     */
    @Operation(summary = "AP-08 イベント更新",
            description = "イベント情報および参加区分を編集する。参加区分は変更のたびに全体を置き換える。"
                    + "対象の区分に有効な申込が残っている状態での変更、区分名の重複は400。対象イベントが存在しない場合は404。管理者のみ実行できる。")
    @PutMapping("/api/events/{id}")
    public EventDetailResponse update(@PathVariable Long id, @RequestBody EventUpsertRequest request) {
        // ログイン中の利用者が管理者かどうかを確認する（管理者でなければ、ここで例外が投げられる）
        authContext.requireAdmin();
        // リクエスト内容を手動でバリデーションする（違反があれば、ここで例外が投げられる）
        validate(request);
        // EventServiceに更新処理を依頼し、その結果（更新後のイベント詳細）をそのまま返す
        return eventService.update(id, request);
    }

    // AP-09 DELETE /api/events/{id}（管理者のみ）
    /**
     * イベントを削除（論理削除）する（AP-09）。フロントエンドのcore/event-api.ts（EventApiService#remove）から呼ばれ、
     * EventService#deleteに処理を委譲する。authContext.requireAdmin()により管理者以外は403になる。
     *
     * @param id 削除対象イベントID
     */
    @Operation(summary = "AP-09 イベント削除",
            description = "イベントを論理削除する（deleted_atを設定、履歴は保持）。受付済の申込が1件でも存在する場合は400。"
                    + "対象が存在しない場合は404。管理者のみ実行できる。")
    @DeleteMapping("/api/events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        // ログイン中の利用者が管理者かどうかを確認する（管理者でなければ、ここで例外が投げられる）
        authContext.requireAdmin();
        // EventServiceに削除（論理削除）処理を依頼する
        eventService.delete(id);
    }

    // 機能追加（ソフトデリートの復元） POST /api/events/{id}/restore（管理者のみ）
    /**
     * 削除済みのイベントを復元する（AP-10）。フロントエンドのcore/event-api.ts（EventApiService#restore）から呼ばれ、
     * EventService#restoreに処理を委譲する。authContext.requireAdmin()により管理者以外は403になる。
     *
     * @param id 復元対象イベントID
     * @return 復元後のイベント詳細
     */
    @Operation(summary = "AP-10 イベント復元",
            description = "削除済みのイベントを復元する（deleted_atを解除）。削除済みでない、または存在しない場合は404。管理者のみ実行できる。")
    @PostMapping("/api/events/{id}/restore")
    public EventDetailResponse restore(@PathVariable Long id) {
        // ログイン中の利用者が管理者かどうかを確認する（管理者でなければ、ここで例外が投げられる）
        authContext.requireAdmin();
        // EventServiceに復元処理を依頼し、その結果（復元後のイベント詳細）をそのまま返す
        return eventService.restore(id);
    }

    // AP-11 GET /api/events/{id}/attendees（当日受付の申込者一覧、管理者のみ、機能追加）
    /**
     * 当日受付画面で使う申込者一覧を取得する（AP-11）。フロントエンドのcore/checkin-api.ts（CheckInApiService#attendees）
     * から呼ばれ、ApplicationService#listAttendeesに処理を委譲する（EventServiceではない点に注意）。
     * authContext.requireAdmin()により管理者以外は403になる。
     *
     * @param id 対象イベントID
     * @return 申込者一覧（状況を問わず全件、申込日時昇順）
     */
    @Operation(summary = "AP-11 申込者一覧取得（当日受付用）",
            description = "当日受付画面で使う申込者一覧（申込日時昇順、状況・区分名・チェックイン日時・アンケート回答を含む）を取得する。"
                    + "状況を問わず全ての申込が対象。対象イベントが存在しない場合は404。管理者のみ実行できる。")
    @GetMapping("/api/events/{id}/attendees")
    public List<AttendeeResponse> attendees(@PathVariable Long id) {
        // ログイン中の利用者が管理者かどうかを確認する（管理者でなければ、ここで例外が投げられる）
        authContext.requireAdmin();
        // ApplicationServiceに申込者一覧の取得を依頼し、その結果をそのまま返す（EventServiceではない点に注意）
        return applicationService.listAttendees(id);
    }

    // AP-07／AP-08で、権限チェックの後に手動でBean Validationを実行するためのヘルパー
    // （ApplicationControllerのvalidate()と同じ考え方。@Validを使うと権限チェックより先にバリデーションが走ってしまうため）
    private void validate(EventUpsertRequest request) {
        // requestの内容をBean Validationのルールに従って検証し、違反があれば一覧として取得する
        Set<ConstraintViolation<EventUpsertRequest>> violations = validator.validate(request);
        // 違反が1件でもあれば
        if (!violations.isEmpty()) {
            // 違反内容を持つ例外を投げる
            throw new ConstraintViolationException(violations);
        }
    }
}
