package com.example.eventapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.eventapp.common.exception.ForbiddenException;
import com.example.eventapp.common.exception.NotFoundException;
import com.example.eventapp.dto.EventCommentResponse;
import com.example.eventapp.entity.Event;
import com.example.eventapp.entity.EventComment;
import com.example.eventapp.entity.User;
import com.example.eventapp.repository.EventCommentRepository;
import com.example.eventapp.repository.EventRepository;
import com.example.eventapp.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 実行環境: サーバー側（JVM）。O-2: EventCommentServiceの業務ロジック（要件定義書§8 E10）のユニットテスト。
// JUnit5＋Mockito。Repositoryは全てモック化し、DBに触れずにビジネスロジックだけを検証する。
class EventCommentServiceTest {

    private EventCommentRepository eventCommentRepository;
    private EventRepository eventRepository;
    private UserRepository userRepository;
    private EventCommentService eventCommentService;

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long EVENT_ID = 10L;
    private static final Long COMMENT_ID = 100L;

    @BeforeEach
    void setUp() {
        eventCommentRepository = mock(EventCommentRepository.class);
        eventRepository = mock(EventRepository.class);
        userRepository = mock(UserRepository.class);
        eventCommentService = new EventCommentService(eventCommentRepository, eventRepository, userRepository);
    }

    // 正常系: 一覧取得時、本人の投稿にはmine=trueが付く
    @Test
    void list_正常系_本人の投稿はmineがtrue() {
        Event event = mock(Event.class);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        User author = mock(User.class);
        when(author.getId()).thenReturn(USER_ID);
        when(author.getName()).thenReturn("投稿者");
        EventComment comment = new EventComment(event, author, "コメント本文");
        when(eventCommentRepository.findByEvent_IdOrderByCreatedAtAsc(EVENT_ID)).thenReturn(List.of(comment));

        List<EventCommentResponse> result = eventCommentService.list(EVENT_ID, USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).mine()).isTrue();
        assertThat(result.get(0).userName()).isEqualTo("投稿者");
    }

    // 正常系: 他人の投稿はmine=false
    @Test
    void list_正常系_他人の投稿はmineがfalse() {
        Event event = mock(Event.class);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        User author = mock(User.class);
        when(author.getId()).thenReturn(OTHER_USER_ID);
        EventComment comment = new EventComment(event, author, "コメント本文");
        when(eventCommentRepository.findByEvent_IdOrderByCreatedAtAsc(EVENT_ID)).thenReturn(List.of(comment));

        List<EventCommentResponse> result = eventCommentService.list(EVENT_ID, USER_ID);

        assertThat(result.get(0).mine()).isFalse();
    }

    // 異常系: 存在しないイベントのコメント一覧取得は404相当
    @Test
    void list_異常系_イベントが存在しなければNotFoundException() {
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventCommentService.list(EVENT_ID, USER_ID))
                .isInstanceOf(NotFoundException.class);
    }

    // 正常系: コメント投稿が保存され、投稿者本人としてmine=trueで返る
    @Test
    void post_正常系_保存されmineがtrueで返る() {
        Event event = mock(Event.class);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getName()).thenReturn("投稿者");
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(eventCommentRepository.save(any(EventComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventCommentResponse response = eventCommentService.post(USER_ID, EVENT_ID, "こんにちは");

        assertThat(response.body()).isEqualTo("こんにちは");
        assertThat(response.mine()).isTrue();
        verify(eventCommentRepository).save(any(EventComment.class));
    }

    // 異常系: 存在しないイベントへの投稿は404相当
    @Test
    void post_異常系_イベントが存在しなければNotFoundException() {
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventCommentService.post(USER_ID, EVENT_ID, "こんにちは"))
                .isInstanceOf(NotFoundException.class);
        verify(eventCommentRepository, never()).save(any());
    }

    // 正常系: 投稿者本人は自分のコメントを削除できる
    @Test
    void delete_正常系_投稿者本人は削除できる() {
        Event event = mock(Event.class);
        User author = mock(User.class);
        when(author.getId()).thenReturn(USER_ID);
        EventComment comment = new EventComment(event, author, "コメント本文");
        when(eventCommentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));

        eventCommentService.delete(USER_ID, false, COMMENT_ID);

        verify(eventCommentRepository).delete(comment);
    }

    // 正常系（要件定義書§8）: 管理者は他人のコメントも削除できる
    @Test
    void delete_正常系_管理者は他人のコメントも削除できる() {
        Event event = mock(Event.class);
        User author = mock(User.class);
        when(author.getId()).thenReturn(OTHER_USER_ID);
        EventComment comment = new EventComment(event, author, "コメント本文");
        when(eventCommentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));

        eventCommentService.delete(USER_ID, true, COMMENT_ID);

        verify(eventCommentRepository).delete(comment);
    }

    // 異常系（要件定義書§8 E10）: 投稿者本人でも管理者でもない一般ユーザーは削除できない
    @Test
    void delete_異常系_本人でも管理者でもなければ削除できない() {
        Event event = mock(Event.class);
        User author = mock(User.class);
        when(author.getId()).thenReturn(OTHER_USER_ID);
        EventComment comment = new EventComment(event, author, "コメント本文");
        when(eventCommentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> eventCommentService.delete(USER_ID, false, COMMENT_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("削除できません");
        verify(eventCommentRepository, never()).delete(any(EventComment.class));
    }

    // 異常系: 存在しないコメントの削除は404相当
    @Test
    void delete_異常系_コメントが存在しなければNotFoundException() {
        when(eventCommentRepository.findById(COMMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventCommentService.delete(USER_ID, false, COMMENT_ID))
                .isInstanceOf(NotFoundException.class);
    }
}
