package com.example.eventapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.eventapp.common.exception.BusinessException;
import com.example.eventapp.common.exception.ForbiddenException;
import com.example.eventapp.common.exception.NotFoundException;
import com.example.eventapp.dto.ApplicationResponse;
import com.example.eventapp.entity.Application;
import com.example.eventapp.entity.ApplicationStatus;
import com.example.eventapp.entity.Event;
import com.example.eventapp.entity.User;
import com.example.eventapp.repository.ApplicationRepository;
import com.example.eventapp.repository.EventRepository;
import com.example.eventapp.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// 実行環境: サーバー側（JVM）。G-1: ApplicationServiceの業務ロジック（要件定義書§8）のユニットテスト。
// JUnit5＋Mockito。Repositoryは全てモック化し、DBに触れずにビジネスロジックだけを検証する。
class ApplicationServiceTest {

    private ApplicationRepository applicationRepository;
    private EventRepository eventRepository;
    private UserRepository userRepository;
    private ApplicationService applicationService;

    private static final Long USER_ID = 1L;
    private static final Long EVENT_ID = 10L;

    @BeforeEach
    void setUp() {
        applicationRepository = mock(ApplicationRepository.class);
        eventRepository = mock(EventRepository.class);
        userRepository = mock(UserRepository.class);
        applicationService = new ApplicationService(applicationRepository, eventRepository, userRepository);
    }

    private Event openEvent(int capacity) {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getCapacity()).thenReturn(capacity);
        when(event.isOpen(any(LocalDateTime.class))).thenReturn(true);
        return event;
    }

    // 正常系: 定員に余裕があり、未申込、受付中のイベントなら申込が成功する
    @Test
    void apply_正常系_定員に余裕があれば申込できる() {
        Event event = openEvent(5);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(applicationRepository.countByEvent_IdAndStatus(EVENT_ID, ApplicationStatus.ACCEPTED)).thenReturn(0L);
        when(applicationRepository.existsByUser_IdAndEvent_IdAndStatus(USER_ID, EVENT_ID, ApplicationStatus.ACCEPTED))
                .thenReturn(false);
        User user = mock(User.class);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        Application saved = new Application(user, event);
        when(applicationRepository.save(captor.capture())).thenReturn(saved);

        ApplicationResponse response = applicationService.apply(USER_ID, EVENT_ID);

        assertThat(response.eventId()).isEqualTo(EVENT_ID);
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.status()).isEqualTo(ApplicationStatus.ACCEPTED);
        assertThat(captor.getValue().getUser()).isEqualTo(user);
    }

    // 異常系: 存在しないイベントへの申込は404相当の例外
    @Test
    void apply_異常系_イベントが存在しなければNotFoundException() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.apply(USER_ID, EVENT_ID))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(applicationRepository);
    }

    // 異常系: 申込締切または開催日時を過ぎたイベントへの申込は拒否される（要件定義書§8 E2）
    @Test
    void apply_異常系_受付終了のイベントには申込できない() {
        Event closedEvent = mock(Event.class);
        when(closedEvent.isOpen(any(LocalDateTime.class))).thenReturn(false);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(closedEvent));

        assertThatThrownBy(() -> applicationService.apply(USER_ID, EVENT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("申込受付は終了しました");
        verifyNoInteractions(applicationRepository);
    }

    // 異常系: 受付済の申込数が定員に達しているイベントには申込できない（要件定義書§8 E1）
    @Test
    void apply_異常系_定員に達していれば申込できない() {
        Event fullEvent = openEvent(3);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(fullEvent));
        when(applicationRepository.countByEvent_IdAndStatus(EVENT_ID, ApplicationStatus.ACCEPTED)).thenReturn(3L);

        assertThatThrownBy(() -> applicationService.apply(USER_ID, EVENT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("定員に達しています");
        verify(applicationRepository, org.mockito.Mockito.never()).save(any());
    }

    // 異常系: 同一ユーザーが同一イベントにすでに「受付済」で申し込んでいれば二重申込は拒否される（要件定義書§8 E3）
    @Test
    void apply_異常系_二重申込はできない() {
        Event event = openEvent(5);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(applicationRepository.countByEvent_IdAndStatus(EVENT_ID, ApplicationStatus.ACCEPTED)).thenReturn(1L);
        when(applicationRepository.existsByUser_IdAndEvent_IdAndStatus(USER_ID, EVENT_ID, ApplicationStatus.ACCEPTED))
                .thenReturn(true);

        assertThatThrownBy(() -> applicationService.apply(USER_ID, EVENT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("すでに申し込み済みです");
    }

    // 正常系: 受付済かつ開催日時前の申込は取消できる（要件定義書§8）
    @Test
    void cancel_正常系_受付済かつ開催前なら取消できる() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(USER_ID);
        Event futureEvent = mock(Event.class);
        when(futureEvent.getStartAt()).thenReturn(LocalDateTime.now().plusDays(1));
        Application application = new Application(owner, futureEvent);
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        applicationService.cancel(USER_ID, 100L);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
    }

    // 異常系: 他人の申込は取消できない（403相当）
    @Test
    void cancel_異常系_他人の申込は取消できない() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(999L);
        Event futureEvent = mock(Event.class);
        Application application = new Application(owner, futureEvent);
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> applicationService.cancel(USER_ID, 100L))
                .isInstanceOf(ForbiddenException.class);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
    }

    // 異常系: すでにキャンセル済み、または開催日時を過ぎた申込は取消できない（要件定義書§8 E4）
    @Test
    void cancel_異常系_開催日時を過ぎた申込は取消できない() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(USER_ID);
        Event pastEvent = mock(Event.class);
        when(pastEvent.getStartAt()).thenReturn(LocalDateTime.now().minusDays(1));
        Application application = new Application(owner, pastEvent);
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> applicationService.cancel(USER_ID, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("取消できません");
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
    }

    // 異常系: 存在しない申込IDの取消は404相当
    @Test
    void cancel_異常系_申込が存在しなければNotFoundException() {
        when(applicationRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.cancel(USER_ID, 100L))
                .isInstanceOf(NotFoundException.class);
    }
}
