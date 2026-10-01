package com.example.eventapp.controller;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.common.CurrentUser;
import com.example.eventapp.dto.FavoriteEventResponse;
import com.example.eventapp.dto.LoginRequest;
import com.example.eventapp.dto.MyApplicationResponse;
import com.example.eventapp.dto.UserCommentResponse;
import com.example.eventapp.dto.UserRegisterRequest;
import com.example.eventapp.dto.UserResponse;
import com.example.eventapp.service.ApplicationService;
import com.example.eventapp.service.EventCommentService;
import com.example.eventapp.service.FavoriteService;
import com.example.eventapp.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
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

// 実行環境: サーバー側（JVM、localhost:8080）。ログイン（機能追加：メールアドレス方式）・
// 軽い会員登録（機能追加）・ユーザー一覧（機能追加：管理者向けマスタ確認用）・
// 管理者アカウント登録（AP-25、機能追加）・利用者詳細（AP-26〜28）・管理者権限の降格（AP-33）・利用者の匿名化（AP-34）。
// login・registerはログイン前（未認証）に呼ばれるため、認証不要（WebConfig／AuthInterceptor参照）。
// @Operationの説明文はdocs/03_API設計書.mdの記載と一致させる（食い違いが出たら実装＝Swagger UIを正とし本書を見直す）
@Tag(name = "利用者", description = "ログイン・利用者登録・利用者一覧・管理者アカウント登録・利用者詳細・管理者権限の降格・利用者の匿名化（AP-01〜03, AP-25〜28, AP-33, AP-34）")
@RestController
public class UserController {

    private final UserService userService;
    private final ApplicationService applicationService;
    private final FavoriteService favoriteService;
    private final EventCommentService eventCommentService;
    private final AuthContext authContext;
    private final Validator validator;

    public UserController(UserService userService, ApplicationService applicationService,
            FavoriteService favoriteService, EventCommentService eventCommentService,
            AuthContext authContext, Validator validator) {
        this.userService = userService;
        this.applicationService = applicationService;
        this.favoriteService = favoriteService;
        this.eventCommentService = eventCommentService;
        this.authContext = authContext;
        this.validator = validator;
    }

    // POST /api/login（認証不要）。メールアドレスからユーザーを特定し、そのロールを返す
    @Operation(summary = "AP-01 ログイン",
            description = "メールアドレスにより利用者を識別し、認証状態を確立する。パスワードによる照合は行わない。"
                    + "登録済みでないメールアドレスの場合は401を返す。認証不要で呼び出せる。")
    @PostMapping("/api/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request) {
        return userService.login(request.email());
    }

    // POST /api/users（認証不要。作成されるのは常に一般ユーザー）
    @Operation(summary = "AP-02 利用者登録",
            description = "名前・メールアドレスによる新規利用者登録。作成されるのは常に一般利用者（管理者としての登録はできない）。"
                    + "登録後はそのままログイン状態となる。メールアドレスが登録済みの場合は400を返す。認証不要で呼び出せる。")
    @PostMapping("/api/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody UserRegisterRequest request) {
        return userService.register(request.name(), request.email());
    }

    // GET /api/users（管理者のみ。マスタ確認用の一覧）
    @Operation(summary = "AP-03 利用者一覧取得",
            description = "登録済み利用者の一覧（利用者ID・名前・メールアドレス・利用者区分）を利用者ID昇順で取得する。管理者のみ実行できる。")
    @GetMapping("/api/users")
    public List<UserResponse> list() {
        authContext.requireAdmin();
        return userService.list();
    }

    // AP-25 POST /api/admins（管理者のみ。作成されるのは常に管理者）
    // API設計書§2.5: 権限チェックを先に行うため@Validは使わず、権限チェック後に手動でバリデーションする
    @Operation(summary = "AP-25 管理者アカウント登録",
            description = "既存の管理者が、名前・メールアドレスを指定して新たな管理者アカウントを登録する。作成されるのは常に管理者。"
                    + "一般利用者によるアクセスは403、メールアドレスが登録済みの場合は400を返す。管理者のみ実行できる。")
    @PostMapping("/api/admins")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse registerAdmin(@RequestBody UserRegisterRequest request) {
        authContext.requireAdmin();
        validate(request);
        return userService.registerAdmin(request.name(), request.email());
    }

    // AP-26 GET /api/users/{id}（管理者のみ。SC-15利用者詳細の基本情報）
    @Operation(summary = "AP-26 利用者情報取得（管理者用）",
            description = "指定した利用者の基本情報（利用者ID・名前・メールアドレス・利用者区分）を取得する。"
                    + "SC-15（利用者詳細）の表示に使う。対象の利用者が存在しない場合は404。管理者のみ実行できる。")
    @GetMapping("/api/users/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        authContext.requireAdmin();
        return userService.getById(id);
    }

    // AP-27 GET /api/users/{id}/applications（管理者のみ。SC-15利用者詳細の申込一覧）
    // Service層はAP-13（自分の申込一覧）と共通のmyApplications()をそのまま利用する
    @Operation(summary = "AP-27 利用者の申込一覧取得（管理者用）",
            description = "指定した利用者の申込一覧（キャンセル済みを含む全件、AP-13と同一形式）を取得する。"
                    + "SC-15（利用者詳細）の表示に使う。対象の利用者が存在しない場合は404。管理者のみ実行できる。")
    @GetMapping("/api/users/{id}/applications")
    public List<MyApplicationResponse> applicationsOf(@PathVariable Long id) {
        authContext.requireAdmin();
        userService.getById(id);
        return applicationService.myApplications(id);
    }

    // AP-28 GET /api/users/{id}/favorites（管理者のみ。SC-15利用者詳細のお気に入り一覧）
    // Service層はAP-18（自分のお気に入り一覧）と共通のmyFavorites()をそのまま利用する
    @Operation(summary = "AP-28 利用者のお気に入り一覧取得（管理者用）",
            description = "指定した利用者のお気に入り一覧（AP-18と同一形式、削除済みイベントへの登録も含む）を取得する。"
                    + "SC-15（利用者詳細）の表示に使う。対象の利用者が存在しない場合は404。管理者のみ実行できる。")
    @GetMapping("/api/users/{id}/favorites")
    public List<FavoriteEventResponse> favoritesOf(@PathVariable Long id) {
        authContext.requireAdmin();
        userService.getById(id);
        return favoriteService.myFavorites(id);
    }

    // AP-31 GET /api/users/{id}/comments（管理者のみ。SC-15利用者詳細のコメント履歴）
    @Operation(summary = "AP-31 利用者のコメント履歴取得（管理者用）",
            description = "指定した利用者が投稿したコメント履歴（返信を含む、投稿日時降順）を取得する。"
                    + "論理削除済みのコメントも履歴として含める（本文は固定の削除済み表示文言になる）。"
                    + "SC-15（利用者詳細）の表示に使う。対象の利用者が存在しない場合は404。管理者のみ実行できる。")
    @GetMapping("/api/users/{id}/comments")
    public List<UserCommentResponse> commentsOf(@PathVariable Long id) {
        authContext.requireAdmin();
        userService.getById(id);
        return eventCommentService.listByUser(id);
    }

    // AP-33 PUT /api/users/{id}/demote（管理者のみ）
    @Operation(summary = "AP-33 管理者権限の降格",
            description = "指定した利用者（管理者）を一般利用者に変更する。対象が存在しない場合は404、既に一般利用者の場合、"
                    + "または管理者が1人のみの状態で実行しようとした場合は400を返す。管理者のみ実行できる。")
    @PutMapping("/api/users/{id}/demote")
    public UserResponse demote(@PathVariable Long id) {
        authContext.requireAdmin();
        return userService.demote(id);
    }

    // AP-34 DELETE /api/users/{id}（本人または管理者）
    @Operation(summary = "AP-34 利用者の匿名化（退会）",
            description = "指定した利用者を匿名化（退会）する。名前・メールアドレスを固定の文言・形式に置き換え、"
                    + "申込等の履歴は残したまま行を保持する（物理削除ではない）。本人、または管理者が実行できる。"
                    + "対象が存在しない場合は404、対象が管理者の場合（先に管理者権限の降格が必要）、"
                    + "または既に退会済みの場合は400、本人・管理者以外によるアクセスは403を返す。")
    @DeleteMapping("/api/users/{id}")
    public UserResponse anonymize(@PathVariable Long id) {
        CurrentUser currentUser = authContext.getCurrentUser();
        return userService.anonymize(id, currentUser.userId(), currentUser.isAdmin());
    }

    private void validate(UserRegisterRequest request) {
        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
