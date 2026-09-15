package com.example.eventapp.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.eventapp.common.exception.BusinessException;
import com.example.eventapp.common.exception.NotFoundException;
import com.example.eventapp.entity.ApplicationStatus;
import com.example.eventapp.entity.Event;
import com.example.eventapp.repository.ApplicationRepository;
import com.example.eventapp.repository.EventRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 実行環境: サーバー側（JVM）。G-1: EventService.delete()の業務ロジック（要件定義書§8）のユニットテスト。
// D-8時点で「削除はカスケードされる」と誤認していたが、実際は受付済の申込が残っていると削除を拒否する
// 仕様であることが分かったため、そのことを回帰確認できるようテストとして残す。
class EventServiceTest {

    private EventRepository eventRepository;
    private ApplicationRepository applicationRepository;
    private EventService eventService;

    private static final Long EVENT_ID = 10L;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        eventService = new EventService(eventRepository, applicationRepository);
    }

    // 正常系: 受付済の申込が無いイベントは削除できる
    @Test
    void delete_正常系_申込が無ければ削除できる() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(applicationRepository.countByEvent_IdAndStatus(EVENT_ID, ApplicationStatus.ACCEPTED)).thenReturn(0L);

        eventService.delete(EVENT_ID);

        verify(eventRepository).delete(event);
    }

    // 異常系: 受付済の申込が1件でもあれば削除は拒否される（要件定義書§8 E5）。カスケード削除はしない
    @Test
    void delete_異常系_受付済の申込があれば削除できない() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(applicationRepository.countByEvent_IdAndStatus(EVENT_ID, ApplicationStatus.ACCEPTED)).thenReturn(1L);

        assertThatThrownBy(() -> eventService.delete(EVENT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("申込があるため削除できません");
        verify(eventRepository, never()).delete(event);
    }

    // 異常系: 存在しないイベントの削除は404相当
    @Test
    void delete_異常系_イベントが存在しなければNotFoundException() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.delete(EVENT_ID))
                .isInstanceOf(NotFoundException.class);
    }
}
