package com.example.eventapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.eventapp.dto.ApplicationCreateRequest;
import com.example.eventapp.dto.ApplicationResponse;
import com.example.eventapp.dto.CommentModerationResponse;
import com.example.eventapp.dto.CountResponse;
import com.example.eventapp.dto.EventCommentCreateRequest;
import com.example.eventapp.dto.EventCommentResponse;
import com.example.eventapp.dto.EventDetailResponse;
import com.example.eventapp.dto.EventSummaryResponse;
import com.example.eventapp.dto.EventUpsertRequest;
import com.example.eventapp.dto.FavoriteCreateRequest;
import com.example.eventapp.dto.FavoriteEventResponse;
import com.example.eventapp.dto.FavoriteResponse;
import com.example.eventapp.dto.MyApplicationResponse;
import com.example.eventapp.dto.UserCommentResponse;
import com.example.eventapp.dto.UserRegisterRequest;
import com.example.eventapp.dto.UserResponse;
import com.example.eventapp.entity.Application;
import com.example.eventapp.entity.ApplicationStatus;
import com.example.eventapp.entity.Event;
import com.example.eventapp.repository.ApplicationRepository;
import com.example.eventapp.repository.EventCommentRepository;
import com.example.eventapp.repository.EventRepository;
import com.example.eventapp.repository.FavoriteRepository;
import com.example.eventapp.repository.TicketTypeRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

// 実行環境: サーバー側（JVM）。G-2: API/DB結合テスト。
// Controller→Service→Repository→H2（インメモリDB）まで実際に通し、必須API 8本のうち代表7本を検証する。
// Userは公開コンストラクタが無いためJdbcTemplateで直接INSERTし、Eventは実際のEventRepositoryで作成する。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ApiIntegrationTest {

    private static final Long GENERAL_USER_ID = 1L;
    private static final Long ADMIN_USER_ID = 2L;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private EventCommentRepository eventCommentRepository;

    @Autowired
    private TicketTypeRepository ticketTypeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 追加したお気に入り・コメント・参加区分関連テストがeventsを参照したまま残ると、
        // 後続テストのイベント削除がFK制約違反になるため先に消す
        favoriteRepository.deleteAll();
        eventCommentRepository.deleteAll();
        applicationRepository.deleteAll();
        ticketTypeRepository.deleteAll();
        eventRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, role, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                GENERAL_USER_ID, "一般ユーザー", "general@example.com", "general");
        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, role, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                ADMIN_USER_ID, "管理者", "admin@example.com", "admin");
    }

    private Event openEvent() {
        return openEvent(5);
    }

    private Event openEvent(int capacity) {
        return eventRepository.save(new Event(
                "結合テスト用イベント",
                LocalDateTime.now().plusDays(10),
                "会議室",
                capacity,
                LocalDateTime.now().plusDays(5),
                "G-2結合テスト用データ",
                null, null, null));
    }

    private HttpHeaders authHeaders(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    // AP-04: 登録したイベントがDBから読み出されて一覧に含まれる
    @Test
    void ap04_イベント一覧取得() {
        Event event = openEvent();

        ResponseEntity<EventSummaryResponse[]> response = restTemplate.exchange(
                url("/api/events"), HttpMethod.GET, new HttpEntity<>(authHeaders(GENERAL_USER_ID)),
                EventSummaryResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(EventSummaryResponse::id).contains(event.getId());
    }

    // AP-05: 指定したイベントの詳細がDBの内容通りに返る
    @Test
    void ap05_イベント詳細取得() {
        Event event = openEvent();

        ResponseEntity<EventDetailResponse> response = restTemplate.exchange(
                url("/api/events/" + event.getId()), HttpMethod.GET, new HttpEntity<>(authHeaders(GENERAL_USER_ID)),
                EventDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("結合テスト用イベント");
        assertThat(response.getBody().remaining()).isEqualTo(5);
    }

    // AP-07: 管理者が登録したイベントが実際にDBへ保存される
    @Test
    void ap07_管理者はイベントを登録できる() {
        EventUpsertRequest request = new EventUpsertRequest(
                "新規登録テスト", LocalDateTime.now().plusDays(20), "会議室B", 10,
                LocalDateTime.now().plusDays(15), null, null, null, null, null);

        ResponseEntity<EventDetailResponse> response = restTemplate.exchange(
                url("/api/events"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(ADMIN_USER_ID)),
                EventDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long createdId = response.getBody().id();
        assertThat(eventRepository.findById(createdId)).isPresent();
        assertThat(eventRepository.findById(createdId).get().getName()).isEqualTo("新規登録テスト");
    }

    // AP-07の権限チェック: 一般ユーザーは登録できず、DBにも作られない
    @Test
    void ap07_一般ユーザーはイベントを登録できない() {
        EventUpsertRequest request = new EventUpsertRequest(
                "権限チェック用", LocalDateTime.now().plusDays(20), "会議室B", 10,
                LocalDateTime.now().plusDays(15), null, null, null, null, null);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/events"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(GENERAL_USER_ID)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(eventRepository.count()).isZero();
    }

    // AP-07（要件定義書§8）: 定員未指定でも、参加区分を指定していれば区分の定員合計で登録できる
    @Test
    void ap07_定員を指定せず参加区分のみで登録できる() {
        EventUpsertRequest request = new EventUpsertRequest(
                "区分のみ登録テスト", LocalDateTime.now().plusDays(20), "会議室B", null,
                LocalDateTime.now().plusDays(15), null, null, null, null,
                List.of(new com.example.eventapp.dto.TicketTypeRequest("一般枠", 7),
                        new com.example.eventapp.dto.TicketTypeRequest("会員枠", 3)));

        ResponseEntity<EventDetailResponse> response = restTemplate.exchange(
                url("/api/events"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(ADMIN_USER_ID)),
                EventDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().capacity()).isEqualTo(10);
    }

    // AP-07（要件定義書§8）: 定員も参加区分も指定が無ければ400（業務ルール違反）になる
    @Test
    void ap07_定員も参加区分も未指定なら400() {
        EventUpsertRequest request = new EventUpsertRequest(
                "定員無し登録テスト", LocalDateTime.now().plusDays(20), "会議室B", null,
                LocalDateTime.now().plusDays(15), null, null, null, null, null);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/events"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(ADMIN_USER_ID)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("定員を入力してください");
    }

    // AP-12: 申込がDBに実際に1件作成される
    @Test
    void ap12_一般ユーザーは申込できる() {
        Event event = openEvent();
        ApplicationCreateRequest request = new ApplicationCreateRequest(event.getId(), null, null);

        ResponseEntity<ApplicationResponse> response = restTemplate.exchange(
                url("/api/applications"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(GENERAL_USER_ID)),
                ApplicationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(applicationRepository.count()).isEqualTo(1);
    }

    // AP-13: 自分が申し込んだ内容がDBから読み出されて一覧に反映される
    @Test
    void ap13_自分の申込一覧取得() {
        Event event = openEvent();
        ApplicationCreateRequest applyRequest = new ApplicationCreateRequest(event.getId(), null, null);
        restTemplate.exchange(url("/api/applications"), HttpMethod.POST,
                new HttpEntity<>(applyRequest, authHeaders(GENERAL_USER_ID)), ApplicationResponse.class);

        ResponseEntity<MyApplicationResponse[]> response = restTemplate.exchange(
                url("/api/my/applications"), HttpMethod.GET, new HttpEntity<>(authHeaders(GENERAL_USER_ID)),
                MyApplicationResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].eventId()).isEqualTo(event.getId());
    }

    // AP-14: キャンセル後、DB上のステータスが実際に更新される
    @Test
    void ap14_申込をキャンセルできる() {
        Event event = openEvent();
        ResponseEntity<ApplicationResponse> applyResponse = restTemplate.exchange(
                url("/api/applications"), HttpMethod.POST,
                new HttpEntity<>(new ApplicationCreateRequest(event.getId(), null, null), authHeaders(GENERAL_USER_ID)),
                ApplicationResponse.class);
        Long applicationId = applyResponse.getBody().id();

        ResponseEntity<Void> cancelResponse = restTemplate.exchange(
                url("/api/applications/" + applicationId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), Void.class);

        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        Application cancelled = applicationRepository.findById(applicationId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo("キャンセル済");
    }

    // 同時申込時の排他制御（悲観ロック）。定員1のイベントに2人が同時に申込んでも、
    // 「受付済」になるのは1件だけで、もう1件は「キャンセル待ち」になる（二重受付が起きない）ことを確認する。
    @Test
    void o01_同時に申し込んでも定員を超えて受付済にならない() throws Exception {
        Event event = openEvent(1); // 定員1
        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, role, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                3L, "利用者3", "user3@example.com", "general");
        jdbcTemplate.update(
                "INSERT INTO users (id, name, email, role, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                4L, "利用者4", "user4@example.com", "general");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<ResponseEntity<ApplicationResponse>>> tasks = List.of(3L, 4L).stream()
                    .map(userId -> (Callable<ResponseEntity<ApplicationResponse>>) () -> restTemplate.exchange(
                            url("/api/applications"), HttpMethod.POST,
                            new HttpEntity<>(new ApplicationCreateRequest(event.getId(), null, null), authHeaders(userId)),
                            ApplicationResponse.class))
                    .collect(Collectors.toList());

            List<Future<ResponseEntity<ApplicationResponse>>> futures = pool.invokeAll(tasks);
            List<String> statuses = futures.stream().map(f -> {
                try {
                    return f.get().getBody().status();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).toList();

            assertThat(statuses).containsExactlyInAnyOrder(ApplicationStatus.ACCEPTED, ApplicationStatus.WAITLISTED);
            assertThat(applicationRepository.countByEvent_IdAndStatus(event.getId(), ApplicationStatus.ACCEPTED))
                    .isEqualTo(1);
        } finally {
            pool.shutdown();
        }
    }

    // AP-23: whoamiのレスポンスに、role="admin"由来のadminフィールド（true）が含まれる
    @Test
    void ap23_whoamiはadminフィールドを含む() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/whoami"), HttpMethod.GET, new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"admin\":true");
    }

    // AP-25: 既存の管理者は新たな管理者アカウントを登録でき、実際にDBへ管理者として保存される
    @Test
    void ap25_管理者は管理者アカウントを登録できる() {
        UserRegisterRequest request = new UserRegisterRequest("新管理者", "new-admin@example.com");

        ResponseEntity<UserResponse> response = restTemplate.exchange(
                url("/api/admins"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(ADMIN_USER_ID)),
                UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().role()).isEqualTo("admin");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE email = ?", String.class, "new-admin@example.com"))
                .isEqualTo("admin");
    }

    // AP-25の権限チェック: 一般ユーザーは管理者アカウントを登録できない
    @Test
    void ap25_一般ユーザーは管理者アカウントを登録できない() {
        UserRegisterRequest request = new UserRegisterRequest("新管理者", "new-admin2@example.com");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/admins"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(GENERAL_USER_ID)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?",
                Integer.class, "new-admin2@example.com")).isZero();
    }

    // AP-33: 管理者が2人以上いる状態なら、一方をもう一方が降格できる
    @Test
    void ap33_管理者は他の管理者を降格できる() {
        UserRegisterRequest newAdmin = new UserRegisterRequest("新管理者", "new-admin3@example.com");
        ResponseEntity<UserResponse> created = restTemplate.exchange(
                url("/api/admins"), HttpMethod.POST, new HttpEntity<>(newAdmin, authHeaders(ADMIN_USER_ID)),
                UserResponse.class);
        Long newAdminId = created.getBody().userId();

        ResponseEntity<UserResponse> response = restTemplate.exchange(
                url("/api/users/" + newAdminId + "/demote"), HttpMethod.PUT,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().role()).isEqualTo("general");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE id = ?", String.class, newAdminId)).isEqualTo("general");
    }

    // AP-33の業務ルール（R-17）: 管理者が1人のみの状態では、その管理者を降格できない
    @Test
    void ap33_最後の管理者は降格できない() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/users/" + ADMIN_USER_ID + "/demote"), HttpMethod.PUT,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE id = ?", String.class, ADMIN_USER_ID)).isEqualTo("admin");
    }

    // AP-33の業務ルール（R-16）: 既に一般利用者の対象は降格できない
    @Test
    void ap33_既に一般利用者の対象は降格できない() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/users/" + GENERAL_USER_ID + "/demote"), HttpMethod.PUT,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // AP-33の権限チェック: 一般ユーザーは降格を実行できない
    @Test
    void ap33_一般ユーザーは降格を実行できない() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/users/" + ADMIN_USER_ID + "/demote"), HttpMethod.PUT,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE id = ?", String.class, ADMIN_USER_ID)).isEqualTo("admin");
    }

    // AP-33: 対象の利用者が存在しない場合は404
    @Test
    void ap33_対象の利用者が存在しない場合は404() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/users/9999/demote"), HttpMethod.PUT,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // AP-34: 本人は自分のアカウントを退会（匿名化）できる
    @Test
    void ap34_本人は自分のアカウントを退会できる() {
        ResponseEntity<UserResponse> deleteResponse = restTemplate.exchange(
                url("/api/users/" + GENERAL_USER_ID), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), UserResponse.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deleteResponse.getBody().anonymizedAt()).isNotNull();
        assertThat(deleteResponse.getBody().name()).isEqualTo("退会済み利用者");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT email FROM users WHERE id = ?", String.class, GENERAL_USER_ID))
                .isEqualTo("withdrawn-" + GENERAL_USER_ID + "@invalid.example");
    }

    // AP-34: 管理者は一般利用者を退会させられる
    @Test
    void ap34_管理者は一般利用者を退会させられる() {
        ResponseEntity<UserResponse> response = restTemplate.exchange(
                url("/api/users/" + GENERAL_USER_ID), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().anonymizedAt()).isNotNull();
    }

    // AP-34の権限チェック: 一般利用者は他人のアカウントを退会させられない
    @Test
    void ap34_一般利用者は他人を退会させられない() {
        UserRegisterRequest otherUser = new UserRegisterRequest("別の利用者", "other-user@example.com");
        ResponseEntity<UserResponse> created = restTemplate.exchange(
                url("/api/users"), HttpMethod.POST, new HttpEntity<>(otherUser), UserResponse.class);
        Long otherUserId = created.getBody().userId();

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/users/" + otherUserId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT anonymized_at IS NULL FROM users WHERE id = ?", Boolean.class, otherUserId)).isTrue();
    }

    // AP-34の業務ルール（R-18）: 管理者は退会できない（先にAP-33で降格する必要がある）
    @Test
    void ap34_管理者は退会できない() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/users/" + ADMIN_USER_ID), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT anonymized_at IS NULL FROM users WHERE id = ?", Boolean.class, ADMIN_USER_ID)).isTrue();
    }

    // AP-34の業務ルール（R-19）: 既に退会済みの利用者を再度退会させることはできない
    @Test
    void ap34_既に退会済みの利用者は再度退会できない() {
        restTemplate.exchange(url("/api/users/" + GENERAL_USER_ID), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), UserResponse.class);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/users/" + GENERAL_USER_ID), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // AP-34: 対象の利用者が存在しない場合は404
    @Test
    void ap34_対象の利用者が存在しない場合は404() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/users/9999"), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // AP-34実行後の認証: 退会済み（匿名化済み）のuserIdは、以後のリクエストで認証エラーになる
    // （AuthInterceptorがAP-34の効果を継続中のアクセスにも及ぼす）
    @Test
    void ap34_退会済みの利用者は以後のリクエストで認証エラーになる() {
        restTemplate.exchange(url("/api/users/" + GENERAL_USER_ID), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), UserResponse.class);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/whoami"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // AP-09 format=csv: 削除済みイベントに紐づく申込明細はCSVに含まれない
    @Test
    void ap09_csv出力は削除済みイベントの申込を含まない() {
        Event deletedEvent = openEvent();
        ApplicationCreateRequest applyRequest = new ApplicationCreateRequest(deletedEvent.getId(), null, null);
        restTemplate.exchange(url("/api/applications"), HttpMethod.POST,
                new HttpEntity<>(applyRequest, authHeaders(GENERAL_USER_ID)), ApplicationResponse.class);
        deletedEvent.softDelete();
        eventRepository.save(deletedEvent);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/reports/applications?format=csv"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContain(deletedEvent.getName());
    }

    // AP-04: favoriteCountがお気に入り登録件数を反映する。全利用者に返す項目のため一般ユーザーで確認する
    @Test
    void ap04_favoriteCountはお気に入り登録件数を反映する() {
        Event event = openEvent();
        restTemplate.exchange(url("/api/favorites"), HttpMethod.POST,
                new HttpEntity<>(new FavoriteCreateRequest(event.getId()), authHeaders(GENERAL_USER_ID)),
                FavoriteResponse.class);

        ResponseEntity<EventSummaryResponse[]> response = restTemplate.exchange(
                url("/api/events"), HttpMethod.GET, new HttpEntity<>(authHeaders(GENERAL_USER_ID)),
                EventSummaryResponse[].class);

        assertThat(response.getBody())
                .filteredOn(e -> e.id().equals(event.getId()))
                .extracting(EventSummaryResponse::favoriteCount)
                .containsExactly(1L);
    }

    // AP-22: CSV明細にアンケート回答列が追加され、回答内容がそのまま出力される
    @Test
    void ap22_csv出力にアンケート回答列が含まれる() {
        Event event = eventRepository.save(new Event(
                "アンケート付きイベント",
                LocalDateTime.now().plusDays(10),
                "会議室",
                5,
                LocalDateTime.now().plusDays(5),
                "G-2結合テスト用データ",
                null, null, "参加動機を教えてください"));
        restTemplate.exchange(url("/api/applications"), HttpMethod.POST,
                new HttpEntity<>(new ApplicationCreateRequest(event.getId(), null, "業務で必要なため"),
                        authHeaders(GENERAL_USER_ID)),
                ApplicationResponse.class);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/reports/applications?format=csv"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("アンケート回答");
        assertThat(response.getBody()).contains("業務で必要なため");
    }

    // AP-26: 管理者は利用者情報を取得できる。一般ユーザーは403、存在しないIDは404
    @Test
    void ap26_利用者情報取得は管理者のみ() {
        ResponseEntity<UserResponse> adminResponse = restTemplate.exchange(
                url("/api/users/" + GENERAL_USER_ID), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), UserResponse.class);
        assertThat(adminResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adminResponse.getBody().name()).isEqualTo("一般ユーザー");

        ResponseEntity<String> generalResponse = restTemplate.exchange(
                url("/api/users/" + ADMIN_USER_ID), HttpMethod.GET,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), String.class);
        assertThat(generalResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> notFoundResponse = restTemplate.exchange(
                url("/api/users/9999"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);
        assertThat(notFoundResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // AP-27: 管理者は対象利用者の申込一覧を取得できる。一般ユーザーは403
    @Test
    void ap27_利用者の申込一覧取得() {
        Event event = openEvent();
        restTemplate.exchange(url("/api/applications"), HttpMethod.POST,
                new HttpEntity<>(new ApplicationCreateRequest(event.getId(), null, null), authHeaders(GENERAL_USER_ID)),
                ApplicationResponse.class);

        ResponseEntity<MyApplicationResponse[]> response = restTemplate.exchange(
                url("/api/users/" + GENERAL_USER_ID + "/applications"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), MyApplicationResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].eventId()).isEqualTo(event.getId());

        ResponseEntity<String> forbidden = restTemplate.exchange(
                url("/api/users/" + GENERAL_USER_ID + "/applications"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // AP-28: 管理者は対象利用者のお気に入り一覧を取得できる
    @Test
    void ap28_利用者のお気に入り一覧取得() {
        Event event = openEvent();
        restTemplate.exchange(url("/api/favorites"), HttpMethod.POST,
                new HttpEntity<>(new FavoriteCreateRequest(event.getId()), authHeaders(GENERAL_USER_ID)),
                FavoriteResponse.class);

        ResponseEntity<FavoriteEventResponse[]> response = restTemplate.exchange(
                url("/api/users/" + GENERAL_USER_ID + "/favorites"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), FavoriteEventResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].id()).isEqualTo(event.getId());
    }

    // AP-22: CSV明細に参加区分列が追加され、区分名がそのまま出力される
    @Test
    void ap22_csv出力に参加区分列が含まれる() {
        Event event = eventRepository.save(new Event(
                "区分付きイベント",
                LocalDateTime.now().plusDays(10),
                "会議室",
                5,
                LocalDateTime.now().plusDays(5),
                "G-2結合テスト用データ",
                null, null, null));
        ResponseEntity<EventDetailResponse> updated = restTemplate.exchange(
                url("/api/events/" + event.getId()), HttpMethod.PUT,
                new HttpEntity<>(new EventUpsertRequest(
                        event.getName(), event.getStartAt(), event.getPlace(), 5, event.getApplicationDeadline(),
                        event.getDescription(), null, null, null,
                        List.of(new com.example.eventapp.dto.TicketTypeRequest("一般枠", 5))),
                        authHeaders(ADMIN_USER_ID)),
                EventDetailResponse.class);
        Long ticketTypeId = updated.getBody().ticketTypes().get(0).id();

        restTemplate.exchange(url("/api/applications"), HttpMethod.POST,
                new HttpEntity<>(new ApplicationCreateRequest(event.getId(), ticketTypeId, null),
                        authHeaders(GENERAL_USER_ID)),
                ApplicationResponse.class);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/reports/applications?format=csv"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("参加区分");
        assertThat(response.getBody()).contains("一般枠");
    }

    // AP-29: お気に入り総数取得は管理者のみ実行できる
    @Test
    void ap29_お気に入り総数取得は管理者のみ() {
        Event event = openEvent();
        restTemplate.exchange(url("/api/favorites"), HttpMethod.POST,
                new HttpEntity<>(new FavoriteCreateRequest(event.getId()), authHeaders(GENERAL_USER_ID)),
                FavoriteResponse.class);

        ResponseEntity<CountResponse> response = restTemplate.exchange(
                url("/api/favorites/count"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), CountResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().count()).isEqualTo(1L);

        ResponseEntity<String> forbidden = restTemplate.exchange(
                url("/api/favorites/count"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // AP-30: コメント総数は論理削除済みを除外し、管理者のみ実行できる
    @Test
    void ap30_コメント総数取得は論理削除済みを除外する() {
        Event event = openEvent();
        ResponseEntity<EventCommentResponse> commentResponse = restTemplate.exchange(
                url("/api/events/" + event.getId() + "/comments"), HttpMethod.POST,
                new HttpEntity<>(new EventCommentCreateRequest("有効なコメント", null), authHeaders(GENERAL_USER_ID)),
                EventCommentResponse.class);
        restTemplate.exchange(url("/api/events/" + event.getId() + "/comments"), HttpMethod.POST,
                new HttpEntity<>(new EventCommentCreateRequest("削除予定のコメント", null), authHeaders(GENERAL_USER_ID)),
                EventCommentResponse.class);
        Long willBeDeletedId = commentResponse.getBody().id();
        // 返信を付けたうえで削除すると論理削除になる
        restTemplate.exchange(url("/api/events/" + event.getId() + "/comments"), HttpMethod.POST,
                new HttpEntity<>(new EventCommentCreateRequest("返信", willBeDeletedId), authHeaders(ADMIN_USER_ID)),
                EventCommentResponse.class);
        restTemplate.exchange(url("/api/comments/" + willBeDeletedId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), Void.class);

        ResponseEntity<CountResponse> response = restTemplate.exchange(
                url("/api/comments/count"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), CountResponse.class);

        // 「削除予定のコメント」「返信」の2件が有効。論理削除された1件は含まない
        assertThat(response.getBody().count()).isEqualTo(2L);
    }

    // AP-31: 利用者のコメント履歴は、論理削除済みも含めイベント名付きで取得できる
    @Test
    void ap31_利用者のコメント履歴取得() {
        Event event = openEvent();
        restTemplate.exchange(url("/api/events/" + event.getId() + "/comments"), HttpMethod.POST,
                new HttpEntity<>(new EventCommentCreateRequest("履歴確認用コメント", null), authHeaders(GENERAL_USER_ID)),
                EventCommentResponse.class);

        ResponseEntity<UserCommentResponse[]> response = restTemplate.exchange(
                url("/api/users/" + GENERAL_USER_ID + "/comments"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), UserCommentResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].eventName()).isEqualTo(event.getName());
        assertThat(response.getBody()[0].deleted()).isFalse();

        ResponseEntity<String> forbidden = restTemplate.exchange(
                url("/api/users/" + GENERAL_USER_ID + "/comments"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // AP-32: 全コメント一覧は有効なコメントのみを対象とし、イベント名・投稿者名を含む
    @Test
    void ap32_全コメント一覧取得は有効なコメントのみ() {
        Event event = openEvent();
        ResponseEntity<EventCommentResponse> commentResponse = restTemplate.exchange(
                url("/api/events/" + event.getId() + "/comments"), HttpMethod.POST,
                new HttpEntity<>(new EventCommentCreateRequest("表示されるコメント", null), authHeaders(GENERAL_USER_ID)),
                EventCommentResponse.class);
        Long commentId = commentResponse.getBody().id();
        restTemplate.exchange(url("/api/events/" + event.getId() + "/comments"), HttpMethod.POST,
                new HttpEntity<>(new EventCommentCreateRequest("返信", commentId), authHeaders(ADMIN_USER_ID)),
                EventCommentResponse.class);
        // 返信があるので論理削除される＝一覧には「削除されました」として残るが、本テストは有効なものだけ数える
        restTemplate.exchange(url("/api/comments/" + commentId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), Void.class);

        ResponseEntity<CommentModerationResponse[]> response = restTemplate.exchange(
                url("/api/comments"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_USER_ID)), CommentModerationResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 「表示されるコメント」は論理削除済みのため対象外、「返信」のみが有効なコメントとして残る
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].body()).isEqualTo("返信");
        assertThat(response.getBody()[0].eventName()).isEqualTo(event.getName());
        assertThat(response.getBody()[0].userName()).isEqualTo("管理者");

        ResponseEntity<String> forbidden = restTemplate.exchange(
                url("/api/comments"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(GENERAL_USER_ID)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
