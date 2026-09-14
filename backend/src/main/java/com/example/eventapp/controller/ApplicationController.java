package com.example.eventapp.controller;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.common.exception.ForbiddenException;
import com.example.eventapp.dto.ApplicationCreateRequest;
import com.example.eventapp.dto.ApplicationResponse;
import com.example.eventapp.dto.MyApplicationResponse;
import com.example.eventapp.service.ApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 実行環境: サーバー側（JVM、localhost:8080）。D-3: イベント申込API（API-03）、D-4: 自分の申込一覧API（API-04）。
@RestController
public class ApplicationController {

    private final ApplicationService applicationService;
    private final AuthContext authContext;

    public ApplicationController(ApplicationService applicationService, AuthContext authContext) {
        this.applicationService = applicationService;
        this.authContext = authContext;
    }

    // API-03 POST /api/applications（一般のみ、本人のuserIdに紐付け。管理者は403 要件定義書E7）
    @PostMapping("/api/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse apply(@Valid @RequestBody ApplicationCreateRequest request) {
        requireGeneral();
        Long userId = authContext.getCurrentUser().userId();
        return applicationService.apply(userId, request.eventId());
    }

    // API-04 GET /api/my/applications（一般以上、本人分のみ）
    @GetMapping("/api/my/applications")
    public List<MyApplicationResponse> myApplications() {
        Long userId = authContext.getCurrentUser().userId();
        return applicationService.myApplications(userId);
    }

    private void requireGeneral() {
        if (authContext.getCurrentUser().isAdmin()) {
            throw new ForbiddenException("管理者は申込できません");
        }
    }
}
