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

/**
 * FavoriteService（お気に入りの登録・解除・一覧取得の業務ロジック）に対する単体テスト。
 * JUnit5とMockitoを使用する。Repository（DBアクセスを担うクラス）はすべてモック化（偽装）し、
 * 本物のDBに接続せずに「既に登録済みなら新規作成しない」といった冪等性のルールだけを検証する。
 */
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

    // @BeforeEachが付いたメソッドは各@Testメソッドの実行前に毎回呼ばれ、テストごとに新しいモックとServiceを用意する。
    @BeforeEach
    void setUp() {
        // mock(クラス.class)で、本物のRepository（DBに接続するクラス）の代わりに
        // 振る舞いを偽装したオブジェクトを作る。テストメソッド内のwhen(...).thenReturn(...)で
        // 戻り値を設定し、DBに依存せずFavoriteServiceの業務ロジックだけを検証する。
        favoriteRepository = mock(FavoriteRepository.class);
        eventRepository = mock(EventRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        userRepository = mock(UserRepository.class);
        favoriteService = new FavoriteService(favoriteRepository, eventRepository, applicationRepository, userRepository);
    }

    // 正常系: 未登録のイベントをお気に入り登録すると新規のFavoriteが作られる
    @Test
    void add_正常系_未登録なら新規登録される() {
        // モック化したEventを用意する（本物のEventは作らず、挙動だけ偽装する）
        Event event = mock(Event.class);
        // 「対象イベントが存在し、削除もされていない」という状況を設定する
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        // 「まだお気に入り登録されていない」という状況を設定する（戻り値が空のOptional）
        when(favoriteRepository.findByUser_IdAndEvent_Id(USER_ID, EVENT_ID)).thenReturn(Optional.empty());
        User user = mock(User.class);
        // ユーザーIDからUserの参照を取得する処理を偽装する
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        // save()が呼ばれたときに返す「保存済みのFavorite」を用意する
        Favorite saved = new Favorite(user, event);
        when(favoriteRepository.save(any(Favorite.class))).thenReturn(saved);

        // テスト対象のメソッドを実行する
        FavoriteAddResult result = favoriteService.add(USER_ID, EVENT_ID);

        // 新規作成されたことを示すフラグがtrueであることを確認する
        assertThat(result.created()).isTrue();
        // レスポンスのイベントIDが指定したイベントと一致することを確認する
        assertThat(result.response().eventId()).isEqualTo(EVENT_ID);
        // レスポンスの登録日時が、保存したFavoriteの作成日時と一致することを確認する
        assertThat(result.response().createdAt()).isEqualTo(saved.getCreatedAt());
        // save()が実際に呼ばれた（新規保存が行われた）ことを確認する
        verify(favoriteRepository).save(any(Favorite.class));
    }

    // 機能（冪等）: 既に登録済みのイベントに再度登録しても新規作成はされず、既存の1件をそのまま返す（要件定義書§8 E9）
    @Test
    void add_機能_既に登録済みなら新規作成せず既存を返す() {
        Event event = mock(Event.class);
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        User user = mock(User.class);
        // 既に登録済みのFavoriteを用意する
        Favorite existing = new Favorite(user, event);
        // 「すでにお気に入り登録されている」という状況を設定する
        when(favoriteRepository.findByUser_IdAndEvent_Id(USER_ID, EVENT_ID)).thenReturn(Optional.of(existing));

        FavoriteAddResult result = favoriteService.add(USER_ID, EVENT_ID);

        // 新規作成はされなかった（既存のものを返した）ことを確認する
        assertThat(result.created()).isFalse();
        // レスポンスの登録日時が、既存のFavoriteの作成日時と一致する（新しく作られていない）ことを確認する
        assertThat(result.response().createdAt()).isEqualTo(existing.getCreatedAt());
        // save()が一度も呼ばれていない（新規保存されていない）ことを確認する
        verify(favoriteRepository, never()).save(any());
    }

    // 異常系: 存在しないイベントへのお気に入り登録は404相当
    @Test
    void add_異常系_イベントが存在しなければNotFoundException() {
        // 対象イベントが見つからない状況を設定する
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.empty());

        // メソッド実行時にNotFoundExceptionがスローされることを確認する
        assertThatThrownBy(() -> favoriteService.add(USER_ID, EVENT_ID))
                .isInstanceOf(NotFoundException.class);
        // 例外発生前にsave()が呼ばれていないことを確認する
        verify(favoriteRepository, never()).save(any());
    }

    // 機能（冪等）: 未登録のイベントを解除してもエラーにならない（要件定義書§8 E9）
    @Test
    void remove_機能_未登録でもエラーにならない() {
        // 未登録の状態で解除を実行しても例外が発生しないことを確認する（メソッド呼び出し自体が検証になる）
        favoriteService.remove(USER_ID, EVENT_ID);

        // 削除処理（存在しなくても安全に実行される）が呼ばれたことを確認する
        verify(favoriteRepository).deleteByUser_IdAndEvent_Id(USER_ID, EVENT_ID);
    }

    // 正常系: 自分のお気に入り一覧に、対象イベントの受付済数・受付中フラグ・登録日時が反映される
    @Test
    void myFavorites_正常系_イベント情報と登録日時を含めて返す() {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getName()).thenReturn("テストイベント");
        when(event.getCapacity()).thenReturn(10);
        // 受付中（申込期限内）であることを設定する
        when(event.isOpen(any(LocalDateTime.class))).thenReturn(true);
        User user = mock(User.class);
        Favorite favorite = new Favorite(user, event);
        // このユーザーのお気に入り一覧に、上で用意したFavoriteが1件含まれる状況を設定する
        when(favoriteRepository.findByUser_IdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(favorite));
        // 対象イベントの受付済件数を3件として設定する
        when(applicationRepository.countByEvent_IdAndStatus(EVENT_ID, ApplicationStatus.ACCEPTED)).thenReturn(3L);

        List<FavoriteEventResponse> result = favoriteService.myFavorites(USER_ID);

        // 結果が1件であることを確認する
        assertThat(result).hasSize(1);
        FavoriteEventResponse response = result.get(0);
        // イベントIDが一致することを確認する
        assertThat(response.id()).isEqualTo(EVENT_ID);
        // 受付済件数が反映されていることを確認する
        assertThat(response.acceptedCount()).isEqualTo(3L);
        // 受付中フラグがtrueであることを確認する
        assertThat(response.open()).isTrue();
        // お気に入り登録日時が、元のFavoriteの作成日時と一致することを確認する
        assertThat(response.favoritedAt()).isEqualTo(favorite.getCreatedAt());
    }

    // 正常系: お気に入りが0件なら空配列を返す
    @Test
    void myFavorites_正常系_お気に入りが無ければ空配列() {
        // お気に入りが0件の状況を設定する
        when(favoriteRepository.findByUser_IdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());

        List<FavoriteEventResponse> result = favoriteService.myFavorites(USER_ID);

        // 結果が空であることを確認する
        assertThat(result).isEmpty();
        // お気に入りが無いため、受付済件数を集計する処理（countByEvent_IdAndStatus）が呼ばれていないことを確認する
        verify(applicationRepository, never()).countByEvent_IdAndStatus(anyLong(), any());
    }

    // 正常系: お気に入り総数はRepositoryのcount()をそのまま返す
    @Test
    void countAll_正常系_リポジトリの件数を返す() {
        // Repositoryのcount()が42を返す状況を設定する
        when(favoriteRepository.count()).thenReturn(42L);

        long result = favoriteService.countAll();

        // Repositoryが返した件数がそのまま返ることを確認する
        assertThat(result).isEqualTo(42L);
    }
}
