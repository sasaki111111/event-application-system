package com.example.eventapp.controller;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.common.exception.ForbiddenException;
import com.example.eventapp.dto.ApplicationCreateRequest;
import com.example.eventapp.dto.ApplicationResponse;
import com.example.eventapp.dto.CheckInResponse;
import com.example.eventapp.dto.MyApplicationResponse;
import com.example.eventapp.service.ApplicationService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 実行環境: サーバー側（JVM、localhost:8080）。
// D-3: イベント申込API（API-03）、D-4: 自分の申込一覧API（API-04）、D-5: 申込キャンセルAPI（API-05）。
@RestController
public class ApplicationController {

    private final ApplicationService applicationService;
    private final AuthContext authContext;
    private final Validator validator;

    public ApplicationController(ApplicationService applicationService, AuthContext authContext, Validator validator) {
        this.applicationService = applicationService;
        this.authContext = authContext;
        this.validator = validator;
    }

    // API-03 POST /api/applications（一般のみ、本人のuserIdに紐付け。管理者は403 要件定義書E7）
    // D-7: 権限チェックを先に行うため@Validは使わず、権限チェック後に手動でバリデーションする
    @PostMapping("/api/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse apply(@RequestBody ApplicationCreateRequest request) {
        requireGeneral();
        validate(request);
        Long userId = authContext.getCurrentUser().userId();
        return applicationService.apply(userId, request.eventId());
    }

    // API-04 GET /api/my/applications（一般以上、本人分のみ）
    @GetMapping("/api/my/applications")
    public List<MyApplicationResponse> myApplications() {
        Long userId = authContext.getCurrentUser().userId();
        return applicationService.myApplications(userId);
    }

    // API-05 DELETE /api/applications/{id}（一般以上、本人の申込のみ）
    @DeleteMapping("/api/applications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id) {
        Long userId = authContext.getCurrentUser().userId();
        applicationService.cancel(userId, id);
    }

    // API-19 PUT /api/applications/{id}/check-in（管理者のみ、機能追加）
    @PutMapping("/api/applications/{id}/check-in")
    public CheckInResponse checkIn(@PathVariable Long id) {
        authContext.requireAdmin();
        return applicationService.checkIn(id);
    }

    private void requireGeneral() {
        if (authContext.getCurrentUser().isAdmin()) {
            throw new ForbiddenException("管理者は申込できません");
        }
    }

    private void validate(ApplicationCreateRequest request) {
        Set<ConstraintViolation<ApplicationCreateRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
