package com.example.eventapp.controller;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.dto.EventReportResponse;
import com.example.eventapp.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 実行環境: サーバー側（JVM、localhost:8080）。D-6: 申込実績出力API（AP-22、管理者のみ）。
@Tag(name = "申込実績", description = "申込実績の集計取得・CSV出力（AP-22）")
@RestController
public class ReportController {

    private final ReportService reportService;
    private final AuthContext authContext;

    public ReportController(ReportService reportService, AuthContext authContext) {
        this.reportService = reportService;
        this.authContext = authContext;
    }

    // AP-22 GET /api/reports/applications?format=json|csv&sort=startAt|accepted_desc（管理者のみ）
    @Operation(summary = "AP-22 申込実績取得",
            description = "format=json（既定）はイベントごとの受付数・充足率の一覧（sortでstartAt順／accepted_desc順を選択）。"
                    + "format=csvはBOM付きUTF-8の申込明細（イベント名・申込者名・申込日時・ステータス・アンケート回答、D-12）をダウンロードする。"
                    + "いずれも削除済みイベントは対象外。管理者のみ実行できる。")
    @GetMapping("/api/reports/applications")
    public ResponseEntity<Object> report(
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(defaultValue = "startAt") String sort) {
        authContext.requireAdmin();

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
}
