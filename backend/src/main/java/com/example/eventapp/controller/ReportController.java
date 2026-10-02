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

// 実行環境: サーバー側（JVM、localhost:8080）。申込実績出力API（AP-22、管理者のみ）。
/**
 * 申込実績の集計取得・CSV出力（AP-22）のHTTP入口を担当するController。
 * フロントエンドのcore/report-api.ts（ReportApiService#summary／#downloadCsv）から呼ばれ、
 * 内部ではReportServiceの各メソッド（summarize／toCsv）に処理を委譲する。
 */
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
    /**
     * 申込実績を取得する（AP-22）。フロントエンドのcore/report-api.ts（ReportApiService#summary／#downloadCsv）から呼ばれる。
     * authContext.requireAdmin()により管理者以外は403になる。
     * formatの値によって戻り値の型（JSON一覧／CSV文字列）が変わるため、戻り値の型を{@code ResponseEntity<Object>}として、
     * メソッド内でcontentTypeやContent-Dispositionヘッダーを組み立てている。
     * {@code ResponseEntity.ok()...header(...)...body(...)}の書き方は、ステータス・ヘッダー・ボディを
     * メソッドチェーンで順に設定していくビルダーパターン。
     * Content-Dispositionヘッダーの{@code attachment; filename=...}は、ブラウザに「レスポンスを画面表示ではなく
     * ファイルとしてダウンロードさせる」よう指示するもの（ReportService#toCsvに処理を委譲してCSV文字列を取得する）。
     *
     * @param format format=json（既定、ReportService#summarizeに委譲）またはformat=csv（ReportService#toCsvに委譲）
     * @param sort   format=jsonの場合の並び順（startAt＝開催日時順、accepted_desc＝受付数降順）
     * @return format=jsonならイベント別集計一覧、format=csvならCSVファイルのダウンロードレスポンス
     */
    @Operation(summary = "AP-22 申込実績取得",
            description = "format=json（既定）はイベントごとの受付数・充足率の一覧（sortでstartAt順／accepted_desc順を選択）。"
                    + "format=csvはBOM付きUTF-8の申込明細（イベント名・申込者名・申込日時・ステータス・アンケート回答）をダウンロードする。"
                    + "いずれも削除済みイベントは対象外。管理者のみ実行できる。")
    @GetMapping("/api/reports/applications")
    public ResponseEntity<Object> report(
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(defaultValue = "startAt") String sort) {
        // ログイン中の利用者が管理者かどうかを確認する（管理者でなければ、ここで例外が投げられる）
        authContext.requireAdmin();

        // formatが"csv"の場合はCSVダウンロードのレスポンスを組み立てる
        if ("csv".equals(format)) {
            // ReportServiceにCSV文字列の組み立てを依頼する
            String csv = reportService.toCsv();
            // レスポンスのcontentTypeをtext/csv（UTF-8）に設定し、
            // Content-Dispositionヘッダーでブラウザにファイルとしてダウンロードさせるよう指示し、
            // 本文（CSV文字列）をセットして返す
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=applications_report.csv")
                    .body(csv);
        }

        // （formatが"csv"以外＝既定の"json"の場合）ReportServiceに集計一覧の取得を依頼する
        List<EventReportResponse> summary = reportService.summarize(sort);
        // 取得した一覧を、HTTPステータス200のレスポンスとして返す
        return ResponseEntity.ok(summary);
    }
}
