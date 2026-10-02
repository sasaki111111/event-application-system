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
/**
 * ログイン・利用者登録（AP-01・AP-02）、利用者一覧（AP-03）、管理者アカウント登録（AP-25）、
 * 利用者詳細（AP-26〜28・AP-31）、管理者権限の降格（AP-33）、利用者の匿名化／退会（AP-34）のHTTP入口を担当するController。
 * フロントエンドのcore/login-api.ts（LoginApiService）・core/user-api.ts（UserApiService）から呼ばれ、
 * 内部ではUserServiceのほか、ApplicationService・FavoriteService・EventCommentServiceの各メソッドにも処理を委譲する
 * （SC-15利用者詳細画面で、申込・お気に入り・コメントの履歴を他のAPIと共通のロジックで取得するため）。
 */
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
    /**
     * メールアドレスでログインする（AP-01）。フロントエンドのcore/login-api.ts（LoginApiService#login）から呼ばれ、
     * UserService#loginに処理を委譲する。このAPIはWebConfig／AuthInterceptorの対象外として設定されており、
     * ログイン前（未認証）でも呼び出せる。
     *
     * @param request ログインに使うメールアドレス
     * @return ログインした利用者の情報
     */
    @Operation(summary = "AP-01 ログイン",
            description = "メールアドレスにより利用者を識別し、認証状態を確立する。パスワードによる照合は行わない。"
                    + "登録済みでないメールアドレスの場合は401を返す。認証不要で呼び出せる。")
    @PostMapping("/api/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request) {
        // UserServiceに、リクエストのメールアドレスを渡してログイン処理を依頼し、その結果をそのまま返す
        return userService.login(request.email());
    }

    // POST /api/users（認証不要。作成されるのは常に一般ユーザー）
    /**
     * 新規利用者登録を行う（AP-02）。フロントエンドのcore/login-api.ts（LoginApiService#register）から呼ばれ、
     * UserService#registerに処理を委譲する。loginと同様、認証不要で呼び出せる。
     *
     * @param request 名前・メールアドレス
     * @return 登録された利用者の情報
     */
    @Operation(summary = "AP-02 利用者登録",
            description = "名前・メールアドレスによる新規利用者登録。作成されるのは常に一般利用者（管理者としての登録はできない）。"
                    + "登録後はそのままログイン状態となる。メールアドレスが登録済みの場合は400を返す。認証不要で呼び出せる。")
    @PostMapping("/api/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody UserRegisterRequest request) {
        // UserServiceに、名前・メールアドレスを渡して登録処理を依頼し、その結果をそのまま返す
        return userService.register(request.name(), request.email());
    }

    // GET /api/users（管理者のみ。マスタ確認用の一覧）
    /**
     * 登録済み利用者の一覧を取得する（AP-03）。フロントエンドのcore/user-api.ts（UserApiService#list）から呼ばれ、
     * UserService#listに処理を委譲する。authContext.requireAdmin()により管理者以外は403になる。
     *
     * @return 利用者一覧（利用者ID昇順）
     */
    @Operation(summary = "AP-03 利用者一覧取得",
            description = "登録済み利用者の一覧（利用者ID・名前・メールアドレス・利用者区分）を利用者ID昇順で取得する。管理者のみ実行できる。")
    @GetMapping("/api/users")
    public List<UserResponse> list() {
        // ログイン中の利用者が管理者かどうかを確認する（管理者でなければ、ここで例外が投げられる）
        authContext.requireAdmin();
        // UserServiceに利用者一覧の取得を依頼し、その結果をそのまま返す
        return userService.list();
    }

    // AP-25 POST /api/admins（管理者のみ。作成されるのは常に管理者）
    // API設計書§2.5: 権限チェックを先に行うため@Validは使わず、権限チェック後に手動でバリデーションする
    /**
     * 新たな管理者アカウントを登録する（AP-25）。フロントエンドのcore/user-api.ts（UserApiService#registerAdmin）
     * から呼ばれ、UserService#registerAdminに処理を委譲する。authContext.requireAdmin()により
     * 管理者以外は403になる（一般利用者は管理者アカウントを作れない）。
     *
     * @param request 名前・メールアドレス
     * @return 登録された管理者アカウントの情報
     */
    @Operation(summary = "AP-25 管理者アカウント登録",
            description = "既存の管理者が、名前・メールアドレスを指定して新たな管理者アカウントを登録する。作成されるのは常に管理者。"
                    + "一般利用者によるアクセスは403、メールアドレスが登録済みの場合は400を返す。管理者のみ実行できる。")
    @PostMapping("/api/admins")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse registerAdmin(@RequestBody UserRegisterRequest request) {
        // ログイン中の利用者が管理者かどうかを確認する（管理者でなければ、ここで例外が投げられる）
        authContext.requireAdmin();
        // リクエスト内容を手動でバリデーションする（違反があれば、ここで例外が投げられる）
        validate(request);
        // UserServiceに、名前・メールアドレスを渡して管理者登録を依頼し、その結果をそのまま返す
        return userService.registerAdmin(request.name(), request.email());
    }

    // AP-26 GET /api/users/{id}（管理者のみ。SC-15利用者詳細の基本情報）
    /**
     * 指定した利用者の基本情報を取得する（AP-26）。フロントエンドのcore/user-api.ts（UserApiService#getById）
     * から呼ばれ、UserService#getByIdに処理を委譲する。SC-15（利用者詳細）の表示に使う。
     *
     * @param id 対象利用者ID
     * @return 利用者の基本情報
     */
    @Operation(summary = "AP-26 利用者情報取得（管理者用）",
            description = "指定した利用者の基本情報（利用者ID・名前・メールアドレス・利用者区分）を取得する。"
                    + "SC-15（利用者詳細）の表示に使う。対象の利用者が存在しない場合は404。管理者のみ実行できる。")
    @GetMapping("/api/users/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        // ログイン中の利用者が管理者かどうかを確認する（管理者でなければ、ここで例外が投げられる）
        authContext.requireAdmin();
        // UserServiceに対象利用者の基本情報取得を依頼し、その結果をそのまま返す
        return userService.getById(id);
    }

    // AP-27 GET /api/users/{id}/applications（管理者のみ。SC-15利用者詳細の申込一覧）
    // Service層はAP-13（自分の申込一覧）と共通のmyApplications()をそのまま利用する
    /**
     * 指定した利用者の申込一覧を取得する（AP-27）。フロントエンドのcore/user-api.ts（UserApiService#applicationsOf）
     * から呼ばれる。{@code userService.getById(id)}で対象利用者の存在確認（存在しなければ404）をしたうえで、
     * ApplicationService#myApplications（AP-13と共通）に処理を委譲する。
     *
     * @param id 対象利用者ID
     * @return 対象利用者の申込一覧（AP-13と同一形式）
     */
    @Operation(summary = "AP-27 利用者の申込一覧取得（管理者用）",
            description = "指定した利用者の申込一覧（キャンセル済みを含む全件、AP-13と同一形式）を取得する。"
                    + "SC-15（利用者詳細）の表示に使う。対象の利用者が存在しない場合は404。管理者のみ実行できる。")
    @GetMapping("/api/users/{id}/applications")
    public List<MyApplicationResponse> applicationsOf(@PathVariable Long id) {
        // ログイン中の利用者が管理者かどうかを確認する（管理者でなければ、ここで例外が投げられる）
        authContext.requireAdmin();
        // 対象利用者が存在するかどうかを確認する（存在しなければ、ここで例外が投げられる。戻り値は使わない）
        userService.getById(id);
        // ApplicationServiceに、対象利用者の申込一覧の取得を依頼し、その結果をそのまま返す
        return applicationService.myApplications(id);
    }

    // AP-28 GET /api/users/{id}/favorites（管理者のみ。SC-15利用者詳細のお気に入り一覧）
    // Service層はAP-18（自分のお気に入り一覧）と共通のmyFavorites()をそのまま利用する
    /**
     * 指定した利用者のお気に入り一覧を取得する（AP-28）。フロントエンドのcore/user-api.ts（UserApiService#favoritesOf）
     * から呼ばれる。対象利用者の存在確認後、FavoriteService#myFavorites（AP-18と共通）に処理を委譲する。
     *
     * @param id 対象利用者ID
     * @return 対象利用者のお気に入り一覧（AP-18と同一形式）
     */
    @Operation(summary = "AP-28 利用者のお気に入り一覧取得（管理者用）",
            description = "指定した利用者のお気に入り一覧（AP-18と同一形式、削除済みイベントへの登録も含む）を取得する。"
                    + "SC-15（利用者詳細）の表示に使う。対象の利用者が存在しない場合は404。管理者のみ実行できる。")
    @GetMapping("/api/users/{id}/favorites")
    public List<FavoriteEventResponse> favoritesOf(@PathVariable Long id) {
        // ログイン中の利用者が管理者かどうかを確認する（管理者でなければ、ここで例外が投げられる）
        authContext.requireAdmin();
        // 対象利用者が存在するかどうかを確認する（存在しなければ、ここで例外が投げられる。戻り値は使わない）
        userService.getById(id);
        // FavoriteServiceに、対象利用者のお気に入り一覧の取得を依頼し、その結果をそのまま返す
        return favoriteService.myFavorites(id);
    }

    // AP-31 GET /api/users/{id}/comments（管理者のみ。SC-15利用者詳細のコメント履歴）
    /**
     * 指定した利用者が投稿したコメント履歴を取得する（AP-31）。フロントエンドのcore/user-api.ts
     * （UserApiService#commentsOf）から呼ばれる。対象利用者の存在確認後、EventCommentService#listByUser
     * に処理を委譲する。
     *
     * @param id 対象利用者ID
     * @return 対象利用者のコメント履歴（投稿日時降順、論理削除済みのコメントも含む）
     */
    @Operation(summary = "AP-31 利用者のコメント履歴取得（管理者用）",
            description = "指定した利用者が投稿したコメント履歴（返信を含む、投稿日時降順）を取得する。"
                    + "論理削除済みのコメントも履歴として含める（本文は固定の削除済み表示文言になる）。"
                    + "SC-15（利用者詳細）の表示に使う。対象の利用者が存在しない場合は404。管理者のみ実行できる。")
    @GetMapping("/api/users/{id}/comments")
    public List<UserCommentResponse> commentsOf(@PathVariable Long id) {
        // ログイン中の利用者が管理者かどうかを確認する（管理者でなければ、ここで例外が投げられる）
        authContext.requireAdmin();
        // 対象利用者が存在するかどうかを確認する（存在しなければ、ここで例外が投げられる。戻り値は使わない）
        userService.getById(id);
        // EventCommentServiceに、対象利用者のコメント履歴の取得を依頼し、その結果をそのまま返す
        return eventCommentService.listByUser(id);
    }

    // AP-33 PUT /api/users/{id}/demote（管理者のみ）
    /**
     * 指定した利用者（管理者）を一般利用者に変更する（AP-33）。フロントエンドのcore/user-api.ts
     * （UserApiService#demote）から呼ばれ、UserService#demoteに処理を委譲する。
     * authContext.requireAdmin()により管理者以外は403になる。
     *
     * @param id 降格対象の利用者ID
     * @return 降格後の利用者情報
     */
    @Operation(summary = "AP-33 管理者権限の降格",
            description = "指定した利用者（管理者）を一般利用者に変更する。対象が存在しない場合は404、既に一般利用者の場合、"
                    + "または管理者が1人のみの状態で実行しようとした場合は400を返す。管理者のみ実行できる。")
    @PutMapping("/api/users/{id}/demote")
    public UserResponse demote(@PathVariable Long id) {
        // ログイン中の利用者が管理者かどうかを確認する（管理者でなければ、ここで例外が投げられる）
        authContext.requireAdmin();
        // UserServiceに降格処理を依頼し、その結果（降格後の利用者情報）をそのまま返す
        return userService.demote(id);
    }

    // AP-34 DELETE /api/users/{id}（本人または管理者）
    /**
     * 指定した利用者を匿名化（退会）する（AP-34）。フロントエンドのcore/user-api.ts（UserApiService#anonymize）
     * から呼ばれ、UserService#anonymizeに処理を委譲する。本人か管理者かの判定は、権限チェック用の
     * authContext.requireAdmin()ではなくUserService側で行う（本人にも実行を許すため）。
     *
     * @param id 退会対象の利用者ID
     * @return 匿名化後の利用者情報
     */
    @Operation(summary = "AP-34 利用者の匿名化（退会）",
            description = "指定した利用者を匿名化（退会）する。名前・メールアドレスを固定の文言・形式に置き換え、"
                    + "申込等の履歴は残したまま行を保持する（物理削除ではない）。本人、または管理者が実行できる。"
                    + "対象が存在しない場合は404、対象が管理者の場合（先に管理者権限の降格が必要）、"
                    + "または既に退会済みの場合は400、本人・管理者以外によるアクセスは403を返す。")
    @DeleteMapping("/api/users/{id}")
    public UserResponse anonymize(@PathVariable Long id) {
        // ログイン中の利用者情報（利用者ID・管理者かどうか）を取り出す
        CurrentUser currentUser = authContext.getCurrentUser();
        // UserServiceに、実行者の情報を渡して匿名化処理を依頼する（本人か管理者かの判定はService側で行う）
        return userService.anonymize(id, currentUser.userId(), currentUser.isAdmin());
    }

    // AP-25で、権限チェックの後に手動でBean Validationを実行するためのヘルパー
    // （ApplicationController・EventControllerのvalidate()と同じ考え方）
    private void validate(UserRegisterRequest request) {
        // requestの内容をBean Validationのルールに従って検証し、違反があれば一覧として取得する
        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);
        // 違反が1件でもあれば
        if (!violations.isEmpty()) {
            // 違反内容を持つ例外を投げる
            throw new ConstraintViolationException(violations);
        }
    }
}
