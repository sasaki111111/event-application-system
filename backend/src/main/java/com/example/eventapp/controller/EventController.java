package com.example.eventapp.controller;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.dto.AttendeeResponse;
import com.example.eventapp.dto.DeletedEventResponse;
import com.example.eventapp.dto.EventDetailResponse;
import com.example.eventapp.dto.EventSummaryResponse;
import com.example.eventapp.dto.EventUpsertRequest;
import com.example.eventapp.service.ApplicationService;
import com.example.eventapp.service.EventService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 実行環境: サーバー側（JVM、localhost:8080）。
// D-1: イベント一覧・詳細API（AP-04・AP-05）、D-2: イベント登録・編集・削除API（AP-07〜09、管理者のみ）。
@RestController
public class EventController {

    private final EventService eventService;
    private final ApplicationService applicationService;
    private final AuthContext authContext;
    private final Validator validator;

    public EventController(EventService eventService, ApplicationService applicationService,
            AuthContext authContext, Validator validator) {
        this.eventService = eventService;
        this.applicationService = applicationService;
        this.authContext = authContext;
        this.validator = validator;
    }

    // AP-04 GET /api/events?status=all|open（既定all）
    @GetMapping("/api/events")
    public List<EventSummaryResponse> list(@RequestParam(defaultValue = "all") String status) {
        return eventService.list(status);
    }

    // AP-05 GET /api/events/{id}
    @GetMapping("/api/events/{id}")
    public EventDetailResponse detail(@PathVariable Long id) {
        return eventService.getDetail(id);
    }

    // 機能追加（ソフトデリート） GET /api/events/deleted（管理者のみ）
    @GetMapping("/api/events/deleted")
    public List<DeletedEventResponse> listDeleted() {
        authContext.requireAdmin();
        return eventService.listDeleted();
    }

    // AP-07 POST /api/events（管理者のみ）
    // D-7: 権限チェックを先に行うため@Validは使わず、権限チェック後に手動でバリデーションする
    @PostMapping("/api/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventDetailResponse create(@RequestBody EventUpsertRequest request) {
        authContext.requireAdmin();
        validate(request);
        return eventService.create(request);
    }

    // AP-08 PUT /api/events/{id}（管理者のみ）
    @PutMapping("/api/events/{id}")
    public EventDetailResponse update(@PathVariable Long id, @RequestBody EventUpsertRequest request) {
        authContext.requireAdmin();
        validate(request);
        return eventService.update(id, request);
    }

    // AP-09 DELETE /api/events/{id}（管理者のみ）
    @DeleteMapping("/api/events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        authContext.requireAdmin();
        eventService.delete(id);
    }

    // 機能追加（ソフトデリートの復元） POST /api/events/{id}/restore（管理者のみ）
    @PostMapping("/api/events/{id}/restore")
    public EventDetailResponse restore(@PathVariable Long id) {
        authContext.requireAdmin();
        return eventService.restore(id);
    }

    // AP-11 GET /api/events/{id}/attendees（当日受付の申込者一覧、管理者のみ、機能追加）
    @GetMapping("/api/events/{id}/attendees")
    public List<AttendeeResponse> attendees(@PathVariable Long id) {
        authContext.requireAdmin();
        return applicationService.listAttendees(id);
    }

    private void validate(EventUpsertRequest request) {
        Set<ConstraintViolation<EventUpsertRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
