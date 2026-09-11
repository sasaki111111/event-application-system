package com.example.eventapp.controller;

import com.example.eventapp.dto.EventDetailResponse;
import com.example.eventapp.dto.EventSummaryResponse;
import com.example.eventapp.service.EventService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 実行環境: サーバー側（JVM、localhost:8080）。D-1: イベント一覧・詳細API（API設計書 API-01・API-02）。
@RestController
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
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
}
