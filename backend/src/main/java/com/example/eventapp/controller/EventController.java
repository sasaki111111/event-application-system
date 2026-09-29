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
// D-1: イベント一覧・詳細API（AP-04・AP-05）、D-2: イベント登録・編集・削除API（AP-07〜09、管理者のみ）。
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
    @Operation(summary = "AP-04 イベント一覧取得",
            description = "開催日時昇順でイベント一覧を取得する。statusにopenを指定すると受付中のイベントのみに絞り込む。"
                    + "acceptedCountは受付済申込件数、favoriteCountはお気に入り登録件数（D-14、全利用者に返す）。削除済みイベントは常に対象外。")
    @GetMapping("/api/events")
    public List<EventSummaryResponse> list(@RequestParam(defaultValue = "all") String status) {
        return eventService.list(status);
    }

    // AP-05 GET /api/events/{id}
    @Operation(summary = "AP-05 イベント詳細取得",
            description = "指定したイベントの詳細情報（残り枠・申込締切・参加区分一覧・アンケート文言・お気に入り数等）を取得する。"
                    + "対象が存在しない、または削除済みの場合は404。")
    @GetMapping("/api/events/{id}")
    public EventDetailResponse detail(@PathVariable Long id) {
        return eventService.getDetail(id);
    }

    // 機能追加（ソフトデリート） GET /api/events/deleted（管理者のみ）
    @Operation(summary = "AP-06 削除済みイベント一覧取得",
            description = "削除済み（論理削除）のイベント一覧を取得する。管理者のみ実行できる。")
    @GetMapping("/api/events/deleted")
    public List<DeletedEventResponse> listDeleted() {
        authContext.requireAdmin();
        return eventService.listDeleted();
    }

    // AP-07 POST /api/events（管理者のみ）
    // D-7: 権限チェックを先に行うため@Validは使わず、権限チェック後に手動でバリデーションする
    @Operation(summary = "AP-07 イベント登録",
            description = "イベント情報および参加区分（任意）を新規登録する。参加区分を登録した場合、イベント全体の定員は区分の定員合計に同期する。"
                    + "管理者のみ実行できる。一般利用者によるアクセスは403、入力値エラーは400。")
    @PostMapping("/api/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventDetailResponse create(@RequestBody EventUpsertRequest request) {
        authContext.requireAdmin();
        validate(request);
        return eventService.create(request);
    }

    // AP-08 PUT /api/events/{id}（管理者のみ）
    @Operation(summary = "AP-08 イベント更新",
            description = "イベント情報および参加区分を編集する。参加区分は変更のたびに全体を置き換える。"
                    + "対象の区分に有効な申込が残っている状態での変更、区分名の重複は400。対象イベントが存在しない場合は404。管理者のみ実行できる。")
    @PutMapping("/api/events/{id}")
    public EventDetailResponse update(@PathVariable Long id, @RequestBody EventUpsertRequest request) {
        authContext.requireAdmin();
        validate(request);
        return eventService.update(id, request);
    }

    // AP-09 DELETE /api/events/{id}（管理者のみ）
    @Operation(summary = "AP-09 イベント削除",
            description = "イベントを論理削除する（deleted_atを設定、履歴は保持）。受付済の申込が1件でも存在する場合は400。"
                    + "対象が存在しない場合は404。管理者のみ実行できる。")
    @DeleteMapping("/api/events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        authContext.requireAdmin();
        eventService.delete(id);
    }

    // 機能追加（ソフトデリートの復元） POST /api/events/{id}/restore（管理者のみ）
    @Operation(summary = "AP-10 イベント復元",
            description = "削除済みのイベントを復元する（deleted_atを解除）。削除済みでない、または存在しない場合は404。管理者のみ実行できる。")
    @PostMapping("/api/events/{id}/restore")
    public EventDetailResponse restore(@PathVariable Long id) {
        authContext.requireAdmin();
        return eventService.restore(id);
    }

    // AP-11 GET /api/events/{id}/attendees（当日受付の申込者一覧、管理者のみ、機能追加）
    @Operation(summary = "AP-11 申込者一覧取得（当日受付用）",
            description = "当日受付画面で使う申込者一覧（申込日時昇順、状況・区分名・チェックイン日時・アンケート回答を含む、D-12）を取得する。"
                    + "状況を問わず全ての申込が対象。対象イベントが存在しない場合は404。管理者のみ実行できる。")
    @GetMapping("/api/events/{id}/attendees")
    public List<AttendeeResponse> attendees(@PathVariable Long id) {
        authContext.requireAdmin();
        return applicationService.listAttendees(id);
    }

    private void validate(EventUpsertRequest request) {
        Set<ConstraintViolation<EventUpsertRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
