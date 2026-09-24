package com.example.eventapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.eventapp.common.exception.NotFoundException;
import com.example.eventapp.dto.FavoriteEventResponse;
import com.example.eventapp.entity.ApplicationStatus;
import com.example.eventapp.entity.Event;
import com.example.eventapp.entity.Favorite;
import com.example.eventapp.entity.User;
import com.example.eventapp.repository.ApplicationRepository;
import com.example.eventapp.repository.EventRepository;
import com.example.eventapp.repository.FavoriteRepository;
import com.example.eventapp.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 実行環境: サーバー側（JVM）。N-2: FavoriteServiceの業務ロジック（要件定義書§8 E9の冪等性）のユニットテスト。
// JUnit5＋Mockito。Repositoryは全てモック化し、DBに触れずにビジネスロジックだけを検証する。
class FavoriteServiceTest {

    private FavoriteRepository favoriteRepository;
    private EventRepository eventRepository;
    private ApplicationRepository applicationRepository;
    private UserRepository userRepository;
    private FavoriteService favoriteService;

    private static final Long USER_ID = 1L;
    private static final Long EVENT_ID = 10L;

    @BeforeEach
    void setUp() {
        favoriteRepository = mock(FavoriteRepository.class);
        eventRepository = mock(EventRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        userRepository = mock(UserRepository.class);
        favoriteService = new FavoriteService(favoriteRepository, eventRepository, applicationRepository, userRepository);
    }

    // 正常系: 未登録のイベントをお気に入り登録すると新規のFavoriteが作られる
    @Test
    void add_正常系_未登録なら新規登録される() {
        Event event = mock(Event.class);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(favoriteRepository.findByUser_IdAndEvent_Id(USER_ID, EVENT_ID)).thenReturn(Optional.empty());
        User user = mock(User.class);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        Favorite saved = new Favorite(user, event);
        when(favoriteRepository.save(any(Favorite.class))).thenReturn(saved);

        FavoriteAddResult result = favoriteService.add(USER_ID, EVENT_ID);

        assertThat(result.created()).isTrue();
        assertThat(result.response().eventId()).isEqualTo(EVENT_ID);
        assertThat(result.response().createdAt()).isEqualTo(saved.getCreatedAt());
        verify(favoriteRepository).save(any(Favorite.class));
    }

    // 機能（冪等）: 既に登録済みのイベントに再度登録しても新規作成はされず、既存の1件をそのまま返す（要件定義書§8 E9）
    @Test
    void add_機能_既に登録済みなら新規作成せず既存を返す() {
        Event event = mock(Event.class);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        User user = mock(User.class);
        Favorite existing = new Favorite(user, event);
        when(favoriteRepository.findByUser_IdAndEvent_Id(USER_ID, EVENT_ID)).thenReturn(Optional.of(existing));

        FavoriteAddResult result = favoriteService.add(USER_ID, EVENT_ID);

        assertThat(result.created()).isFalse();
        assertThat(result.response().createdAt()).isEqualTo(existing.getCreatedAt());
        verify(favoriteRepository, never()).save(any());
    }

    // 異常系: 存在しないイベントへのお気に入り登録は404相当
    @Test
    void add_異常系_イベントが存在しなければNotFoundException() {
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.add(USER_ID, EVENT_ID))
                .isInstanceOf(NotFoundException.class);
        verify(favoriteRepository, never()).save(any());
    }

    // 機能（冪等）: 未登録のイベントを解除してもエラーにならない（要件定義書§8 E9）
    @Test
    void remove_機能_未登録でもエラーにならない() {
        favoriteService.remove(USER_ID, EVENT_ID);

        verify(favoriteRepository).deleteByUser_IdAndEvent_Id(USER_ID, EVENT_ID);
    }

    // 正常系: 自分のお気に入り一覧に、対象イベントの受付済数・受付中フラグ・登録日時が反映される
    @Test
    void myFavorites_正常系_イベント情報と登録日時を含めて返す() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getName()).thenReturn("テストイベント");
        when(event.getCapacity()).thenReturn(10);
        when(event.isOpen(any(LocalDateTime.class))).thenReturn(true);
        User user = mock(User.class);
        Favorite favorite = new Favorite(user, event);
        when(favoriteRepository.findByUser_IdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(favorite));
        when(applicationRepository.countByEvent_IdAndStatus(EVENT_ID, ApplicationStatus.ACCEPTED)).thenReturn(3L);

        List<FavoriteEventResponse> result = favoriteService.myFavorites(USER_ID);

        assertThat(result).hasSize(1);
        FavoriteEventResponse response = result.get(0);
        assertThat(response.id()).isEqualTo(EVENT_ID);
        assertThat(response.acceptedCount()).isEqualTo(3L);
        assertThat(response.open()).isTrue();
        assertThat(response.favoritedAt()).isEqualTo(favorite.getCreatedAt());
    }

    // 正常系: お気に入りが0件なら空配列を返す
    @Test
    void myFavorites_正常系_お気に入りが無ければ空配列() {
        when(favoriteRepository.findByUser_IdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());

        List<FavoriteEventResponse> result = favoriteService.myFavorites(USER_ID);

        assertThat(result).isEmpty();
        verify(applicationRepository, never()).countByEvent_IdAndStatus(anyLong(), any());
    }
}
