package com.example.eventapp.service;

import com.example.eventapp.dto.EventReportResponse;
import com.example.eventapp.entity.Application;
import com.example.eventapp.entity.ApplicationStatus;
import com.example.eventapp.entity.Event;
import com.example.eventapp.repository.ApplicationRepository;
import com.example.eventapp.repository.EventRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 実行環境: サーバー側（JVM）。申込実績レポート（D-6、API-09）の業務ロジック。
@Service
public class ReportService {

    // UTF-8 BOM（U+FEFF）。ソースファイルに直接特殊文字を書かず、文字コードから生成する
    private static final String BOM = String.valueOf((char) 0xFEFF);

    private final EventRepository eventRepository;
    private final ApplicationRepository applicationRepository;

    public ReportService(EventRepository eventRepository, ApplicationRepository applicationRepository) {
        this.eventRepository = eventRepository;
        this.applicationRepository = applicationRepository;
    }

    // format=json: イベント別の受付済数・充足率（チーム独自機能、要件定義書§2）
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

    // format=csv: 申込明細（イベント名／申込者名／申込日時／ステータス）。技術仕様書§4.2 型7の必須部分
    @Transactional(readOnly = true)
    public String toCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append(BOM); // ExcelでUTF-8を文字化けせずに開けるようにするため付与
        csv.append("イベント名,申込者名,申込日時,ステータス\r\n");

        for (Application application : applicationRepository.findAllByOrderByEvent_StartAtAsc()) {
            csv.append(csvField(application.getEvent().getName())).append(',')
                    .append(csvField(application.getUser().getName())).append(',')
                    .append(csvField(application.getAppliedAt().toString())).append(',')
                    .append(csvField(application.getStatus())).append("\r\n");
        }

        return csv.toString();
    }

    private String csvField(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        return needsQuoting ? "\"" + escaped + "\"" : escaped;
    }
}
