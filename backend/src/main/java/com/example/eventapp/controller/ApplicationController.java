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
/**
 * Controller（コントローラー）層のクラス。Controllerは、ブラウザ（Angular）から送られてくる
 * HTTPリクエスト（GET/POST/PUT/DELETEなど）を最初に受け取る「入口」の役割を持つ。
 * ここでリクエストの内容を取り出し、権限チェックや入力チェックを行ったうえで、実際の業務処理は
 * Service層（{@link com.example.eventapp.service.ApplicationService}など）に委ねる。
 * Controller自身は「DBに保存する」「定員を数える」といった業務ロジックは持たない。
 *
 * <p>このクラスは、フロントエンド（Angular）のApplicationApiService（申込・自分の申込一覧・キャンセル）
 * およびCheckInApiService（チェックイン）から呼び出される。クラスに付けた{@code @RestController}は、
 * このクラスのメソッドの戻り値をそのままJSONに変換してレスポンスにする、という意味のSpring Bootの
 * アノテーション。コンストラクタの引数にServiceやAuthContextを書くだけで、Spring Bootが自動的に
 * そのインスタンスを生成して渡してくれる（コンストラクタインジェクション、DI＝Dependency Injection）。
 * {@code new ApplicationService(...)}のようなコードをどこにも書く必要がない。
 */
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
    /**
     * AP-12: イベントへの参加を申し込む。フロントエンドのApplicationApiService.apply()から呼ばれ、
     * 内部ではApplicationService.apply()に処理を委ねる。
     * {@code @PostMapping}はHTTP POSTリクエストを受け付けるメソッドであることを表し、
     * {@code @RequestBody}はリクエストボディ（JSON）をApplicationCreateRequestに変換して受け取ることを表す。
     * {@code @ResponseStatus(HttpStatus.CREATED)}は、正常終了時に常にHTTPステータス201（Created）を
     * 返すことを表す（Serviceの戻り値そのものはステータスに関係しない）。
     * {@code @Operation}はSwagger（API仕様を自動生成する仕組み）向けの説明であり、実際の処理には影響しない。
     *
     * @param request 申込対象のイベントID・参加区分ID・アンケート回答を含むリクエストボディ
     * @return 登録された申込の内容（申込ID・ステータス等）
     */
    @Operation(summary = "AP-12 イベント申込",
            description = "対象イベントへの参加を申し込む。参加区分が設定されたイベントではticketTypeIdが必須。"
                    + "定員に空きがあれば「受付済」、無ければ「キャンセル待ち」として登録する（エラーにはしない）。"
                    + "管理者による申込、受付期間外、区分未選択、二重申込はエラー（管理者は403、それ以外は400）。")
    @PostMapping("/api/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse apply(@RequestBody ApplicationCreateRequest request) {
        // 管理者による申込でないかを確認する（管理者であれば、ここで例外が投げられ以降の処理は実行されない）
        requireGeneral();
        // リクエスト内容を手動でバリデーションする（違反があれば、ここで例外が投げられ以降の処理は実行されない）
        validate(request);
        // ログイン中の利用者IDを取り出す（この申込の申込者として使う）
        Long userId = authContext.getCurrentUser().userId();
        // ApplicationServiceに申込の登録を依頼し、その結果（登録された申込の内容）をそのままレスポンスとして返す
        return applicationService.apply(userId, request.eventId(), request.ticketTypeId(), request.extraAnswer());
    }

    // AP-13 GET /api/my/applications（一般以上、本人分のみ）
    /**
     * AP-13: ログイン中の利用者本人の申込一覧を取得する。フロントエンドのApplicationApiService.myApplications()
     * から呼ばれ、内部ではApplicationService.myApplications()に処理を委ねる。
     *
     * @return 申込日時降順の申込一覧（キャンセル済みも含む全件）
     */
    @Operation(summary = "AP-13 自分の申込一覧取得",
            description = "ログイン中の利用者本人の申込一覧（申込日時降順、キャンセル済みを含む全件）を取得する。"
                    + "キャンセル待ちの場合はwaitlistRankに順位を設定する。")
    @GetMapping("/api/my/applications")
    public List<MyApplicationResponse> myApplications() {
        // ログイン中の利用者IDを取り出す
        Long userId = authContext.getCurrentUser().userId();
        // ApplicationServiceに本人の申込一覧の取得を依頼し、その結果をそのまま返す
        return applicationService.myApplications(userId);
    }

    // AP-14 DELETE /api/applications/{id}（一般以上、本人の申込のみ）
    /**
     * AP-14: 本人の申込をキャンセルする。フロントエンドのApplicationApiService.cancel()から呼ばれ、
     * 内部ではApplicationService.cancel()に処理を委ねる。
     * {@code @PathVariable}は、URLの一部（ここでは{@code /api/applications/{id}}の{@code id}）を
     * メソッドの引数として受け取ることを表す。{@code @ResponseStatus(HttpStatus.NO_CONTENT)}は、
     * 戻り値を返さない（void）正常終了時にHTTPステータス204を返すことを表す。
     *
     * @param id キャンセル対象の申込ID
     */
    @Operation(summary = "AP-14 申込キャンセル",
            description = "本人の申込のみキャンセルできる。受付済の申込をキャンセルした場合、同一イベント（区分があれば同一区分）で"
                    + "最も申込日時が古いキャンセル待ちの申込を自動的に繰り上げる。他人の申込指定は403、キャンセル不可の状態は400、対象が存在しない場合は404。")
    @DeleteMapping("/api/applications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id) {
        // ログイン中の利用者IDを取り出す（本人の申込かどうかの判定にServiceが使う）
        Long userId = authContext.getCurrentUser().userId();
        // ApplicationServiceに、本人確認を含むキャンセル処理を依頼する
        applicationService.cancel(userId, id);
    }

    // AP-15 PUT /api/applications/{id}/check-in（管理者のみ、機能追加）
    /**
     * AP-15: 当日受付でのチェックインを記録する。フロントエンドのCheckInApiService.checkIn()から呼ばれ、
     * 内部ではApplicationService.checkIn()に処理を委ねる。{@code @PutMapping}はHTTP PUTリクエストを
     * 受け付けるメソッドであることを表す。
     * {@code authContext.requireAdmin()}は、ログイン中の利用者が管理者でない場合に例外
     * （ForbiddenException）を投げるチェックで、最終的にGlobalExceptionHandlerがキャッチして
     * HTTPステータス403に変換したレスポンスを返す。
     *
     * @param id チェックイン対象の申込ID
     * @return チェックイン結果（申込ID・チェックイン日時）
     */
    @Operation(summary = "AP-15 チェックイン",
            description = "当日受付でのチェックインを記録する。状況が「受付済」の申込のみ対象（それ以外は400）。"
                    + "既にチェックイン済みの申込に再実行した場合は、画面側の確認を経てチェックイン日時を更新する。管理者のみ実行できる。")
    @PutMapping("/api/applications/{id}/check-in")
    public CheckInResponse checkIn(@PathVariable Long id) {
        // ログイン中の利用者が管理者かどうかを確認する（管理者でなければ、ここで例外が投げられる）
        authContext.requireAdmin();
        // ApplicationServiceにチェックイン処理を依頼し、その結果をそのまま返す
        return applicationService.checkIn(id);
    }

    // 管理者による申込を防ぐ（AP-12、要件定義書E7）。ここで例外を投げると、最終的にGlobalExceptionHandlerが
    // キャッチしてHTTPステータス403として応答する
    private void requireGeneral() {
        // ログイン中の利用者が管理者かどうかを判定する
        if (authContext.getCurrentUser().isAdmin()) {
            // 管理者であれば、ここで例外を投げて以降の処理を中断する
            throw new ForbiddenException("管理者は申込できません");
        }
    }

    // 権限チェックの後に手動でBean Validation（jakarta.validation）を実行するためのヘルパー。
    // @RequestBodyの引数に@Validを付けない代わりに、権限チェックが終わってからControllerが自身で
    // チェックする（@Validを付けると権限チェックより先にバリデーションが走ってしまうため）。
    // 違反があればConstraintViolationExceptionを投げ、GlobalExceptionHandlerがHTTPステータス400に変換する
    private void validate(ApplicationCreateRequest request) {
        // requestの内容をBean Validationのルールに従って検証し、違反があれば一覧として取得する
        Set<ConstraintViolation<ApplicationCreateRequest>> violations = validator.validate(request);
        // 違反が1件でもあれば
        if (!violations.isEmpty()) {
            // 違反内容を持つ例外を投げる
            throw new ConstraintViolationException(violations);
        }
    }
}
