package com.example.eventapp.controller;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.common.exception.ForbiddenException;
import com.example.eventapp.dto.EventDetailResponse;
import com.example.eventapp.dto.EventSummaryResponse;
import com.example.eventapp.dto.EventUpsertRequest;
import com.example.eventapp.service.EventService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 実行環境: サーバー側（JVM、localhost:8080）。
// D-1: イベント一覧・詳細API（API-01・API-02）、D-2: イベント登録・編集・削除API（API-06〜08、管理者のみ）。
@RestController
public class EventController {

    private final EventService eventService;
    private final AuthContext authContext;

    public EventController(EventService eventService, AuthContext authContext) {
        this.eventService = eventService;
        this.authContext = authContext;
    }

    // API-01 GET /api/events?status=all|open（既定all）
    @GetMapping("/api/events")
    public List<EventSummaryResponse> list(@RequestParam(defaultValue = "all") String status) {
        return eventService.list(status);
    }

    // API-02 GET /api/events/{id}
    @GetMapping("/api/events/{id}")
    public EventDetailResponse detail(@PathVariable Long id) {
        return eventService.getDetail(id);
    }

    // API-06 POST /api/events（管理者のみ）
    @PostMapping("/api/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventDetailResponse create(@Valid @RequestBody EventUpsertRequest request) {
        requireAdmin();
        return eventService.create(request);
    }

    // API-07 PUT /api/events/{id}（管理者のみ）
    @PutMapping("/api/events/{id}")
    public EventDetailResponse update(@PathVariable Long id, @Valid @RequestBody EventUpsertRequest request) {
        requireAdmin();
        return eventService.update(id, request);
    }

    // API-08 DELETE /api/events/{id}（管理者のみ）
    @DeleteMapping("/api/events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        requireAdmin();
        eventService.delete(id);
    }

    private void requireAdmin() {
        if (!authContext.getCurrentUser().isAdmin()) {
            throw new ForbiddenException("権限がありません");
        }
    }
}
