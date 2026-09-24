package com.example.eventapp.controller;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.common.CurrentUser;
import com.example.eventapp.dto.EventCommentCreateRequest;
import com.example.eventapp.dto.EventCommentResponse;
import com.example.eventapp.service.EventCommentService;
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
// イベントコメントの一覧（API-20）・投稿（API-21）・削除（API-22）。一般ユーザー・管理者の両方が使える（要件定義書§4）。
@RestController
public class CommentController {

    private final EventCommentService eventCommentService;
    private final AuthContext authContext;

    public CommentController(EventCommentService eventCommentService, AuthContext authContext) {
        this.eventCommentService = eventCommentService;
        this.authContext = authContext;
    }

    // API-20 GET /api/events/{id}/comments
    @GetMapping("/api/events/{id}/comments")
    public List<EventCommentResponse> list(@PathVariable Long id) {
        Long userId = authContext.getCurrentUser().userId();
        return eventCommentService.list(id, userId);
    }

    // API-21 POST /api/events/{id}/comments
    @PostMapping("/api/events/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public EventCommentResponse post(@PathVariable Long id, @Valid @RequestBody EventCommentCreateRequest request) {
        Long userId = authContext.getCurrentUser().userId();
        return eventCommentService.post(userId, id, request.body());
    }

    // API-22 DELETE /api/comments/{id}
    @DeleteMapping("/api/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        CurrentUser currentUser = authContext.getCurrentUser();
        eventCommentService.delete(currentUser.userId(), currentUser.isAdmin(), id);
    }
}
