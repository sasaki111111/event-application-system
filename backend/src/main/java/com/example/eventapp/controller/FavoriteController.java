package com.example.eventapp.controller;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.dto.CountResponse;
import com.example.eventapp.dto.FavoriteCreateRequest;
import com.example.eventapp.dto.FavoriteEventResponse;
import com.example.eventapp.dto.FavoriteResponse;
import com.example.eventapp.service.FavoriteAddResult;
import com.example.eventapp.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 実行環境: サーバー側（JVM、localhost:8080）。
// お気に入り登録（AP-16）・解除（AP-17）・一覧（AP-18）。一般ユーザー・管理者の両方が使える（要件定義書§4）。
@Tag(name = "お気に入り", description = "イベントのお気に入り登録・解除・一覧（AP-16〜18）")
@RestController
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final AuthContext authContext;

    public FavoriteController(FavoriteService favoriteService, AuthContext authContext) {
        this.favoriteService = favoriteService;
        this.authContext = authContext;
    }

    // AP-16 POST /api/favorites。既に登録済みなら200、新規なら201（冪等、要件定義書E9）
    @Operation(summary = "AP-16 お気に入り登録",
            description = "対象イベントをお気に入り登録する。既に登録済みの場合は新規登録せず200で既存の登録内容を返す（冪等）。"
                    + "未登録の場合は新規登録し201を返す。対象イベントが存在しない場合は404。")
    @PostMapping("/api/favorites")
    public ResponseEntity<FavoriteResponse> add(@Valid @RequestBody FavoriteCreateRequest request) {
        Long userId = authContext.getCurrentUser().userId();
        FavoriteAddResult result = favoriteService.add(userId, request.eventId());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    // AP-17 DELETE /api/favorites/{eventId}。未登録でも204（冪等）
    @Operation(summary = "AP-17 お気に入り解除",
            description = "対象イベントのお気に入り登録を解除する。未登録のイベントに対して実行してもエラーにしない（冪等）。")
    @DeleteMapping("/api/favorites/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long eventId) {
        Long userId = authContext.getCurrentUser().userId();
        favoriteService.remove(userId, eventId);
    }

    // AP-18 GET /api/my/favorites（本人分のみ）
    @Operation(summary = "AP-18 お気に入り一覧取得",
            description = "ログイン中の利用者本人のお気に入り一覧（登録日時降順）を取得する。削除済みのイベントに対する登録も履歴として含める。")
    @GetMapping("/api/my/favorites")
    public List<FavoriteEventResponse> myFavorites() {
        Long userId = authContext.getCurrentUser().userId();
        return favoriteService.myFavorites(userId);
    }

    // AP-29 GET /api/favorites/count（管理者のみ、D-20）
    @Operation(summary = "AP-29 お気に入り総数取得（管理者用）",
            description = "全利用者・全イベントのお気に入り登録件数（削除済みイベントに対する登録も含む）を取得する。"
                    + "SC-07（管理者ダッシュボード）の指標表示に使う。管理者のみ実行できる。")
    @GetMapping("/api/favorites/count")
    public CountResponse count() {
        authContext.requireAdmin();
        return new CountResponse(favoriteService.countAll());
    }
}
