package com.example.eventapp.controller;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.common.exception.ForbiddenException;
import com.example.eventapp.dto.ApplicationCreateRequest;
import com.example.eventapp.dto.ApplicationResponse;
import com.example.eventapp.dto.CheckInResponse;
import com.example.eventapp.dto.MyApplicationResponse;
import com.example.eventapp.service.ApplicationService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 実行環境: サーバー側（JVM、localhost:8080）。
// イベント申込API（AP-12）、自分の申込一覧API（AP-13）、申込キャンセルAPI（AP-14）。
@Tag(name = "申込", description = "イベント申込・自分の申込一覧・キャンセル・チェックイン（AP-12〜15）")
@RestController
public class ApplicationController {

    private final ApplicationService applicationService;
    private final AuthContext authContext;
    private final Validator validator;

    public ApplicationController(ApplicationService applicationService, AuthContext authContext, Validator validator) {
        this.applicationService = applicationService;
        this.authContext = authContext;
        this.validator = validator;
    }

    // AP-12 POST /api/applications（一般のみ、本人のuserIdに紐付け。管理者は403 要件定義書E7）
    // 権限チェックを先に行うため@Validは使わず、権限チェック後に手動でバリデーションする
    @Operation(summary = "AP-12 イベント申込",
            description = "対象イベントへの参加を申し込む。参加区分が設定されたイベントではticketTypeIdが必須。"
                    + "定員に空きがあれば「受付済」、無ければ「キャンセル待ち」として登録する（エラーにはしない）。"
                    + "管理者による申込、受付期間外、区分未選択、二重申込はエラー（管理者は403、それ以外は400）。")
    @PostMapping("/api/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse apply(@RequestBody ApplicationCreateRequest request) {
        requireGeneral();
        validate(request);
        Long userId = authContext.getCurrentUser().userId();
        return applicationService.apply(userId, request.eventId(), request.ticketTypeId(), request.extraAnswer());
    }

    // AP-13 GET /api/my/applications（一般以上、本人分のみ）
    @Operation(summary = "AP-13 自分の申込一覧取得",
            description = "ログイン中の利用者本人の申込一覧（申込日時降順、キャンセル済みを含む全件）を取得する。"
                    + "キャンセル待ちの場合はwaitlistRankに順位を設定する。")
    @GetMapping("/api/my/applications")
    public List<MyApplicationResponse> myApplications() {
        Long userId = authContext.getCurrentUser().userId();
        return applicationService.myApplications(userId);
    }

    // AP-14 DELETE /api/applications/{id}（一般以上、本人の申込のみ）
    @Operation(summary = "AP-14 申込キャンセル",
            description = "本人の申込のみキャンセルできる。受付済の申込をキャンセルした場合、同一イベント（区分があれば同一区分）で"
                    + "最も申込日時が古いキャンセル待ちの申込を自動的に繰り上げる。他人の申込指定は403、キャンセル不可の状態は400、対象が存在しない場合は404。")
    @DeleteMapping("/api/applications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id) {
        Long userId = authContext.getCurrentUser().userId();
        applicationService.cancel(userId, id);
    }

    // AP-15 PUT /api/applications/{id}/check-in（管理者のみ、機能追加）
    @Operation(summary = "AP-15 チェックイン",
            description = "当日受付でのチェックインを記録する。状況が「受付済」の申込のみ対象（それ以外は400）。"
                    + "既にチェックイン済みの申込に再実行した場合は、画面側の確認を経てチェックイン日時を更新する。管理者のみ実行できる。")
    @PutMapping("/api/applications/{id}/check-in")
    public CheckInResponse checkIn(@PathVariable Long id) {
        authContext.requireAdmin();
        return applicationService.checkIn(id);
    }

    private void requireGeneral() {
        if (authContext.getCurrentUser().isAdmin()) {
            throw new ForbiddenException("管理者は申込できません");
        }
    }

    private void validate(ApplicationCreateRequest request) {
        Set<ConstraintViolation<ApplicationCreateRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
