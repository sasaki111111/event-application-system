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
// お気に入り登録（AP-040）・解除（AP-041）・一覧（AP-042）。一般ユーザー・管理者の両方が使える（要件定義書§4）。
/**
 * イベントのお気に入り登録（AP-040）・解除（AP-041）・一覧（AP-042）、お気に入り総数取得（AP-131）のHTTP入口を担当するController。
 * フロントエンドのcore/favorite-api.ts（FavoriteApiService）から呼ばれ、内部ではFavoriteServiceの各メソッドに処理を委譲する。
 */
@Tag(name = "お気に入り", description = "イベントのお気に入り登録・解除・一覧（AP-040〜18）")
@RestController
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final AuthContext authContext;

    public FavoriteController(FavoriteService favoriteService, AuthContext authContext) {
        this.favoriteService = favoriteService;
        this.authContext = authContext;
    }

    // AP-040 POST /api/favorites。既に登録済みなら200、新規なら201（冪等、要件定義書E9）
    /**
     * イベントをお気に入り登録する（AP-040）。フロントエンドのcore/favorite-api.ts（FavoriteApiService#add）から呼ばれ、
     * FavoriteService#addに処理を委譲する。
     * 既存の登録があったか（result.created()）によって返すHTTPステータスを200／201のいずれかに動的に変えたいため、
     * 他の多くのメソッドのように{@code @ResponseStatus}の固定指定ではなく、{@link ResponseEntity}を使って
     * ステータスコードとボディの両方をメソッド内で組み立てて返している。
     *
     * @param request お気に入り対象のイベントID
     * @return 登録済みのお気に入り内容（新規登録時は201、登録済みの場合は200）
     */
    @Operation(summary = "AP-040 お気に入り登録",
            description = "対象イベントをお気に入り登録する。既に登録済みの場合は新規登録せず200で既存の登録内容を返す（冪等）。"
                    + "未登録の場合は新規登録し201を返す。対象イベントが存在しない場合は404。")
    @PostMapping("/api/favorites")
    public ResponseEntity<FavoriteResponse> add(@Valid @RequestBody FavoriteCreateRequest request) {
        // ログイン中の利用者IDを取り出す
        Long userId = authContext.getCurrentUser().userId();
        // FavoriteServiceに登録処理を依頼する。戻り値には登録内容と「新規作成したかどうか」の両方が入っている
        FavoriteAddResult result = favoriteService.add(userId, request.eventId());
        // 新規作成なら201（CREATED）、既存のお気に入りをそのまま返した場合は200（OK）を選ぶ
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        // 選んだステータスコードと、お気に入りの内容をレスポンスとして組み立てて返す
        return ResponseEntity.status(status).body(result.response());
    }

    // AP-041 DELETE /api/favorites/{eventId}。未登録でも204（冪等）
    /**
     * イベントのお気に入り登録を解除する（AP-041）。フロントエンドのcore/favorite-api.ts（FavoriteApiService#remove）から呼ばれ、
     * FavoriteService#removeに処理を委譲する。未登録のイベントに対して実行してもエラーにしない（冪等）。
     *
     * @param eventId 対象イベントID
     */
    @Operation(summary = "AP-041 お気に入り解除",
            description = "対象イベントのお気に入り登録を解除する。未登録のイベントに対して実行してもエラーにしない（冪等）。")
    @DeleteMapping("/api/favorites/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long eventId) {
        // ログイン中の利用者IDを取り出す
        Long userId = authContext.getCurrentUser().userId();
        // FavoriteServiceに解除処理を依頼する
        favoriteService.remove(userId, eventId);
    }

    // AP-042 GET /api/my/favorites（本人分のみ）
    /**
     * ログイン中の利用者本人のお気に入り一覧を取得する（AP-042）。フロントエンドのcore/favorite-api.ts
     * （FavoriteApiService#myFavorites）から呼ばれ、FavoriteService#myFavoritesに処理を委譲する。
     *
     * @return お気に入り一覧（登録日時降順、削除済みイベントへの登録も含む）
     */
    @Operation(summary = "AP-042 お気に入り一覧取得",
            description = "ログイン中の利用者本人のお気に入り一覧（登録日時降順）を取得する。削除済みのイベントに対する登録も履歴として含める。")
    @GetMapping("/api/my/favorites")
    public List<FavoriteEventResponse> myFavorites() {
        // ログイン中の利用者IDを取り出す
        Long userId = authContext.getCurrentUser().userId();
        // FavoriteServiceに本人のお気に入り一覧の取得を依頼し、その結果をそのまま返す
        return favoriteService.myFavorites(userId);
    }

    // AP-131 GET /api/favorites/count（管理者のみ）
    /**
     * 全利用者・全イベントのお気に入り登録総数を取得する（AP-131、管理者ダッシュボードSC-110向け）。
     * フロントエンドのcore/favorite-api.ts（FavoriteApiService#count）から呼ばれ、FavoriteService#countAllに処理を委譲する。
     * authContext.requireAdmin()により管理者以外は403になる。
     *
     * @return お気に入り登録件数
     */
    @Operation(summary = "AP-131 お気に入り総数取得（管理者用）",
            description = "全利用者・全イベントのお気に入り登録件数（削除済みイベントに対する登録も含む）を取得する。"
                    + "SC-110（管理者ダッシュボード）の指標表示に使う。管理者のみ実行できる。")
    @GetMapping("/api/favorites/count")
    public CountResponse count() {
        // ログイン中の利用者が管理者かどうかを確認する（管理者でなければ、ここで例外が投げられる）
        authContext.requireAdmin();
        // FavoriteServiceから件数を取得し、レスポンス用のCountResponseに詰めて返す
        return new CountResponse(favoriteService.countAll());
    }
}
