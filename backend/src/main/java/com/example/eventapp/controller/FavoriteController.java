package com.example.eventapp.controller;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.dto.FavoriteCreateRequest;
import com.example.eventapp.dto.FavoriteEventResponse;
import com.example.eventapp.dto.FavoriteResponse;
import com.example.eventapp.service.FavoriteAddResult;
import com.example.eventapp.service.FavoriteService;
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
// お気に入り登録（API-15）・解除（API-16）・一覧（API-17）。一般ユーザー・管理者の両方が使える（要件定義書§4）。
@RestController
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final AuthContext authContext;

    public FavoriteController(FavoriteService favoriteService, AuthContext authContext) {
        this.favoriteService = favoriteService;
        this.authContext = authContext;
    }

    // API-15 POST /api/favorites。既に登録済みなら200、新規なら201（冪等、要件定義書E9）
    @PostMapping("/api/favorites")
    public ResponseEntity<FavoriteResponse> add(@Valid @RequestBody FavoriteCreateRequest request) {
        Long userId = authContext.getCurrentUser().userId();
        FavoriteAddResult result = favoriteService.add(userId, request.eventId());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    // API-16 DELETE /api/favorites/{eventId}。未登録でも204（冪等）
    @DeleteMapping("/api/favorites/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long eventId) {
        Long userId = authContext.getCurrentUser().userId();
        favoriteService.remove(userId, eventId);
    }

    // API-17 GET /api/my/favorites（本人分のみ）
    @GetMapping("/api/my/favorites")
    public List<FavoriteEventResponse> myFavorites() {
        Long userId = authContext.getCurrentUser().userId();
        return favoriteService.myFavorites(userId);
    }
}
