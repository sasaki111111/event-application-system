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
// イベントコメントの一覧（AP-19）・投稿（AP-20）・削除（AP-21）。一般ユーザー・管理者の両方が使える（要件定義書§4）。
@Tag(name = "イベントコメント", description = "イベントへのコメント投稿・閲覧・削除（AP-19〜21）")
@RestController
public class CommentController {

    private final EventCommentService eventCommentService;
    private final AuthContext authContext;

    public CommentController(EventCommentService eventCommentService, AuthContext authContext) {
        this.eventCommentService = eventCommentService;
        this.authContext = authContext;
    }

    // AP-19 GET /api/events/{id}/comments
    @Operation(summary = "AP-19 コメント一覧取得",
            description = "対象イベントのコメント一覧（投稿日時昇順）をフラットな配列で取得する。mineは要求元利用者本人の投稿かどうかを表す。"
                    + "parentCommentIdは返信先コメントID（返信でなければNULL）で、木構造への組み立てはフロントエンド側が行う。"
                    + "deletedがtrueの場合、返信が残っているため論理削除されたコメントであり、bodyは固定の削除済み表示文言になる。"
                    + "対象イベントが存在しない場合は404。")
    @GetMapping("/api/events/{id}/comments")
    public List<EventCommentResponse> list(@PathVariable Long id) {
        Long userId = authContext.getCurrentUser().userId();
        return eventCommentService.list(id, userId);
    }

    // AP-20 POST /api/events/{id}/comments
    @Operation(summary = "AP-20 コメント投稿",
            description = "対象イベントにコメントを投稿する。parentCommentIdを指定すると、そのコメントへの返信として登録する"
                    + "（返信できる階層数に制限は無い。削除済みのコメントへの返信も可能）。受付期間の内外を問わず投稿できる。"
                    + "対象イベントが存在しない場合、またはparentCommentIdが同一イベントのコメントでない場合は404。")
    @PostMapping("/api/events/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public EventCommentResponse post(@PathVariable Long id, @Valid @RequestBody EventCommentCreateRequest request) {
        Long userId = authContext.getCurrentUser().userId();
        return eventCommentService.post(userId, id, request.body(), request.parentCommentId());
    }

    // AP-21 DELETE /api/comments/{id}
    @Operation(summary = "AP-21 コメント削除",
            description = "投稿者本人、または管理者のみコメントを削除できる。返信が1件も無ければ物理削除（一覧から除去）、"
                    + "1件以上あれば論理削除（一覧には残り、本文が削除済み表示に置き換わる）。権限がない場合は403、対象が存在しない場合は404。")
    @DeleteMapping("/api/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        CurrentUser currentUser = authContext.getCurrentUser();
        eventCommentService.delete(currentUser.userId(), currentUser.isAdmin(), id);
    }

    // AP-30 GET /api/comments/count（管理者のみ）
    @Operation(summary = "AP-30 コメント総数取得（管理者用）",
            description = "全イベントの有効なコメント数（返信が残っているため論理削除されたコメントは対象外）を取得する。"
                    + "SC-07（管理者ダッシュボード）の指標表示に使う。管理者のみ実行できる。")
    @GetMapping("/api/comments/count")
    public CountResponse count() {
        authContext.requireAdmin();
        return new CountResponse(eventCommentService.countActive());
    }

    // AP-32 GET /api/comments（管理者のみ、SC-16コメントモデレーション）
    @Operation(summary = "AP-32 全コメント一覧取得（管理者用）",
            description = "全イベントを横断した、有効な（論理削除されていない）コメント一覧（投稿日時降順）を取得する。"
                    + "イベント別・投稿者別の絞り込みは提供しない。SC-16（コメントモデレーション）の一覧表示に使う。管理者のみ実行できる。")
    @GetMapping("/api/comments")
    public List<CommentModerationResponse> listAll() {
        authContext.requireAdmin();
        return eventCommentService.listAllActive();
    }
}
