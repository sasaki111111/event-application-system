package com.example.eventapp.controller;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.common.exception.ForbiddenException;
import com.example.eventapp.dto.EventReportResponse;
import com.example.eventapp.service.ReportService;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 実行環境: サーバー側（JVM、localhost:8080）。D-6: 申込実績出力API（API-09、管理者のみ）。
@RestController
public class ReportController {

    private final ReportService reportService;
    private final AuthContext authContext;

    public ReportController(ReportService reportService, AuthContext authContext) {
        this.reportService = reportService;
        this.authContext = authContext;
    }

    // API-09 GET /api/reports/applications?format=json|csv&sort=startAt|accepted_desc（管理者のみ）
    @GetMapping("/api/reports/applications")
    public ResponseEntity<Object> report(
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(defaultValue = "startAt") String sort) {
        requireAdmin();

        if ("csv".equals(format)) {
            String csv = reportService.toCsv();
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=applications_report.csv")
                    .body(csv);
        }

        List<EventReportResponse> summary = reportService.summarize(sort);
        return ResponseEntity.ok(summary);
    }

    private void requireAdmin() {
        if (!authContext.getCurrentUser().isAdmin()) {
            throw new ForbiddenException("権限がありません");
        }
    }
}
