package com.example.eventapp.controller;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.common.CurrentUser;
import com.example.eventapp.dto.CommentModerationResponse;
import com.example.eventapp.dto.CountResponse;
import com.example.eventapp.dto.EventCommentCreateRequest;
import com.example.eventapp.dto.EventCommentResponse;
import com.example.eventapp.service.EventCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 実行環境: サーバー側（JVM、localhost:8080）。
// イベントコメントの一覧（AP-050）・投稿（AP-051）・削除（AP-052）。一般ユーザー・管理者の両方が使える（要件定義書§4）。
/**
 * Controller（コントローラー）層のクラス。フロントエンド（Angular）のCommentApiServiceから
 * 呼び出され、受け取ったHTTPリクエストの内容をEventCommentService（Service層）に渡す「入口」の役割を持つ。
 */
@Tag(name = "イベントコメント", description = "イベントへのコメント投稿・閲覧・削除（AP-050〜21）")
@RestController
public class CommentController {

    private final EventCommentService eventCommentService;
    private final AuthContext authContext;

    public CommentController(EventCommentService eventCommentService, AuthContext authContext) {
        this.eventCommentService = eventCommentService;
        this.authContext = authContext;
    }

    // AP-050 GET /api/events/{id}/comments
    /**
     * AP-050: 対象イベントのコメント一覧を取得する。フロントエンドのCommentApiService.list()から呼ばれ、
     * 内部ではEventCommentService.list()に処理を委ねる。
     *
     * @param id 対象イベントのID（{@code @PathVariable}でURLから取り出す）
     * @return 投稿日時昇順のコメント一覧（フラットな配列。親子関係の組み立てはフロントエンド側で行う）
     */
    @Operation(summary = "AP-050 コメント一覧取得",
            description = "対象イベントのコメント一覧（投稿日時昇順）をフラットな配列で取得する。mineは要求元利用者本人の投稿かどうかを表す。"
                    + "parentCommentIdは返信先コメントID（返信でなければNULL）で、木構造への組み立てはフロントエンド側が行う。"
                    + "deletedがtrueの場合、返信が残っているため論理削除されたコメントであり、bodyは固定の削除済み表示文言になる。"
                    + "対象イベントが存在しない場合は404。")
    @GetMapping("/api/events/{id}/comments")
    public List<EventCommentResponse> list(@PathVariable Long id) {
        // ログイン中の利用者IDを取り出す（各コメントが本人の投稿かどうかの判定にServiceが使う）
        Long userId = authContext.getCurrentUser().userId();
        // EventCommentServiceにコメント一覧の取得を依頼し、その結果をそのまま返す
        return eventCommentService.list(id, userId);
    }

    // AP-051 POST /api/events/{id}/comments
    /**
     * AP-051: 対象イベントにコメントを投稿する。フロントエンドのCommentApiService.post()から呼ばれ、
     * 内部ではEventCommentService.post()に処理を委ねる。
     * 引数の{@code @Valid}は、リクエストボディ（EventCommentCreateRequest）に付けたBean Validationの
     * アノテーション（文字数制限など）をSpring Bootが自動でチェックすることを表す。本人確認や権限チェックが
     * 不要なメソッドでは、このように{@code @Valid}を付けるだけで検証できる（ApplicationControllerのように
     * 権限チェックを先に行いたい場合は、@Validを使わず手動でバリデーションしている）。
     *
     * @param id 対象イベントのID
     * @param request コメント本文と、返信の場合は返信先コメントID
     * @return 登録されたコメントの内容
     */
    @Operation(summary = "AP-051 コメント投稿",
            description = "対象イベントにコメントを投稿する。parentCommentIdを指定すると、そのコメントへの返信として登録する"
                    + "（返信できる階層数に制限は無い。削除済みのコメントへの返信も可能）。受付期間の内外を問わず投稿できる。"
                    + "対象イベントが存在しない場合、またはparentCommentIdが同一イベントのコメントでない場合は404。")
    @PostMapping("/api/events/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public EventCommentResponse post(@PathVariable Long id, @Valid @RequestBody EventCommentCreateRequest request) {
        // ログイン中の利用者IDを取り出す（この投稿の投稿者として使う）
        Long userId = authContext.getCurrentUser().userId();
        // EventCommentServiceに、本文・返信先コメントIDを渡して投稿処理を依頼し、その結果をそのまま返す
        return eventCommentService.post(userId, id, request.body(), request.parentCommentId());
    }

    // AP-052 DELETE /api/comments/{id}
    /**
     * AP-052: コメントを削除する。フロントエンドのCommentApiService.delete()から呼ばれ、
     * 内部ではEventCommentService.delete()に処理を委ねる。投稿者本人か管理者でない場合は403。
     *
     * @param id 削除対象のコメントID
     */
    @Operation(summary = "AP-052 コメント削除",
            description = "投稿者本人、または管理者のみコメントを削除できる。返信が1件も無ければ物理削除（一覧から除去）、"
                    + "1件以上あれば論理削除（一覧には残り、本文が削除済み表示に置き換わる）。権限がない場合は403、対象が存在しない場合は404。")
    @DeleteMapping("/api/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        // ログイン中の利用者情報（利用者ID・管理者かどうか）を取り出す
        CurrentUser currentUser = authContext.getCurrentUser();
        // EventCommentServiceに、本人・管理者どちらでの削除かを渡して削除処理を依頼する
        eventCommentService.delete(currentUser.userId(), currentUser.isAdmin(), id);
    }

    // AP-132 GET /api/comments/count（管理者のみ）
    /**
     * AP-132: 管理者ダッシュボード（SC-110）に表示する、有効なコメントの総数を取得する。
     * フロントエンドのCommentApiService.count()から呼ばれ、内部ではEventCommentService.countActive()
     * に処理を委ねる。管理者以外がアクセスすると{@code authContext.requireAdmin()}が403を返す。
     *
     * @return コメント総数（論理削除されたものは除く）
     */
    @Operation(summary = "AP-132 コメント総数取得（管理者用）",
            description = "全イベントの有効なコメント数（返信が残っているため論理削除されたコメントは対象外）を取得する。"
                    + "SC-110（管理者ダッシュボード）の指標表示に使う。管理者のみ実行できる。")
    @GetMapping("/api/comments/count")
    public CountResponse count() {
        // ログイン中の利用者が管理者かどうかを確認する（管理者でなければ、ここで例外が投げられる）
        authContext.requireAdmin();
        // EventCommentServiceから件数を取得し、レスポンス用のCountResponseに詰めて返す
        return new CountResponse(eventCommentService.countActive());
    }

    // AP-150 GET /api/comments（管理者のみ、SC-150コメントモデレーション）
    /**
     * AP-150: SC-150（コメントモデレーション画面）向けに、全イベントを横断したコメント一覧を取得する。
     * フロントエンドのCommentApiService.listAll()から呼ばれ、内部ではEventCommentService.listAllActive()
     * に処理を委ねる。
     *
     * @return 有効なコメント一覧（投稿日時降順）
     */
    @Operation(summary = "AP-150 全コメント一覧取得（管理者用）",
            description = "全イベントを横断した、有効な（論理削除されていない）コメント一覧（投稿日時降順）を取得する。"
                    + "イベント別・投稿者別の絞り込みは提供しない。SC-150（コメントモデレーション）の一覧表示に使う。管理者のみ実行できる。")
    @GetMapping("/api/comments")
    public List<CommentModerationResponse> listAll() {
        // ログイン中の利用者が管理者かどうかを確認する（管理者でなければ、ここで例外が投げられる）
        authContext.requireAdmin();
        // EventCommentServiceに全コメント一覧の取得を依頼し、その結果をそのまま返す
        return eventCommentService.listAllActive();
    }
}
