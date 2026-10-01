package com.example.eventapp.service;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.dto.EventReportResponse;
import com.example.eventapp.entity.Application;
import com.example.eventapp.entity.ApplicationStatus;
import com.example.eventapp.entity.Event;
import com.example.eventapp.repository.ApplicationRepository;
import com.example.eventapp.repository.EventRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 実行環境: サーバー側（JVM）。申込実績レポート（AP-22）の業務ロジック。
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    // UTF-8 BOM（U+FEFF）。ソースファイルに直接特殊文字を書かず、文字コードから生成する
    private static final String BOM = String.valueOf((char) 0xFEFF);

    private final EventRepository eventRepository;
    private final ApplicationRepository applicationRepository;
    private final AuthContext authContext;

    public ReportService(EventRepository eventRepository, ApplicationRepository applicationRepository,
            AuthContext authContext) {
        this.eventRepository = eventRepository;
        this.applicationRepository = applicationRepository;
        this.authContext = authContext;
    }

    // format=json: イベント別の受付済数・充足率（docs/01_要件定義書.md F-12）
    @Transactional(readOnly = true)
    public List<EventReportResponse> summarize(String sort) {
        List<EventReportResponse> reports = new ArrayList<>(
                eventRepository.findAllByDeletedAtIsNullOrderByStartAtAsc().stream().map(this::toReport).toList()
        );

        if ("accepted_desc".equals(sort)) {
            reports.sort(Comparator.comparingLong(EventReportResponse::acceptedCount).reversed());
        }
        return reports;
    }

    private EventReportResponse toReport(Event event) {
        long acceptedCount = applicationRepository.countByEvent_IdAndStatus(event.getId(), ApplicationStatus.ACCEPTED);
        double fillRate = Math.round(acceptedCount / (double) event.getCapacity() * 100) / 100.0;
        return new EventReportResponse(
                event.getId(),
                event.getName(),
                event.getStartAt(),
                event.getCapacity(),
                acceptedCount,
                fillRate
        );
    }

    // format=csv: 申込明細（イベント名／申込者名／申込日時／ステータス／アンケート回答／参加区分、docs/03_API設計書.md AP-22）
    // 集計一覧（summarize()）と同様、削除済みイベントに紐づく申込は対象外とする
    // アンケート回答列（アンケート未設定・未回答の申込は空欄）・参加区分列（区分の無いイベントへの申込は空欄）を含む
    @Transactional(readOnly = true)
    public String toCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append(BOM); // ExcelでUTF-8を文字化けせずに開けるようにするため付与
        csv.append("イベント名,申込者名,申込日時,ステータス,アンケート回答,参加区分\r\n");

        List<Application> applications = applicationRepository.findAllByEvent_DeletedAtIsNullOrderByEvent_StartAtAsc();
        for (Application application : applications) {
            String ticketTypeName = application.getTicketType() != null ? application.getTicketType().getName() : null;
            csv.append(csvField(application.getEvent().getName())).append(',')
                    .append(csvField(application.getUser().getName())).append(',')
                    .append(csvField(application.getAppliedAt().toString())).append(',')
                    .append(csvField(application.getStatus())).append(',')
                    .append(csvField(application.getExtraAnswer())).append(',')
                    .append(csvField(ticketTypeName)).append("\r\n");
        }

        log.info("申込実績CSV出力完了 userId={} 出力条件=開催日時順 件数={}",
                authContext.getCurrentUser().userId(), applications.size());
        return csv.toString();
    }

    // CSVインジェクション対策（docs/03_API設計書.md AP-22）対象の先頭文字。
    // Excel等の表計算ソフトが数式として解釈しうる文字（=, +, -, @）およびタブ・改行文字。
    private static final String FORMULA_TRIGGER_CHARS = "=+-@\t\r";

    private String csvField(String value) {
        if (value == null) {
            return "";
        }
        if (!value.isEmpty() && FORMULA_TRIGGER_CHARS.indexOf(value.charAt(0)) >= 0) {
            // 先頭にシングルクォートを付与し、表計算ソフトに数式ではなく文字列として読ませる
            value = "'" + value;
        }
        String escaped = value.replace("\"", "\"\"");
        boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        return needsQuoting ? "\"" + escaped + "\"" : escaped;
    }
}
