package com.example.eventapp.service;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.common.exception.BusinessException;
import com.example.eventapp.common.exception.ForbiddenException;
import com.example.eventapp.common.exception.NotFoundException;
import com.example.eventapp.common.exception.UnauthorizedException;
import com.example.eventapp.dto.UserResponse;
import com.example.eventapp.entity.User;
import com.example.eventapp.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 実行環境: サーバー側（JVM）。軽い会員登録・メールアドレスでのログイン・ユーザー一覧（いずれも機能追加）の業務ロジック。
// パスワードは扱わない。登録できるのは常に一般ユーザーのみ（管理者は登録経路を用意しない）。
/**
 * ログイン・利用者登録（AP-01・AP-02）、利用者一覧（AP-03）、管理者アカウント登録（AP-25）、
 * 利用者詳細の基本情報取得（AP-26）、管理者権限の降格（AP-33）、利用者の匿名化／退会（AP-34）の
 * 業務ロジックを担当するService。UserControllerの各メソッドから呼ばれ、DBアクセスにはUserRepositoryを使う。
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final AuthContext authContext;

    public UserService(UserRepository userRepository, AuthContext authContext) {
        this.userRepository = userRepository;
        this.authContext = authContext;
    }

    /**
     * 新規利用者（一般利用者）を登録する（AP-02）。UserController#registerから呼ばれる。
     *
     * @param name  利用者名
     * @param email メールアドレス（登録前に正規化される）
     * @return 登録された利用者の情報
     */
    @Transactional
    public UserResponse register(String name, String email) {
        email = normalizeEmail(email);
        // 利用者列挙対策（docs/09_認証認可設計書.md 9-8）: 「既に登録済み」と直接断定せず、
        // ログインを促す形にする。管理者登録（registerAdmin）は管理者専用かつ利用者一覧から
        // 既存メールアドレスを確認できるため、こちらは明確なメッセージのままとする
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("この内容では登録できませんでした。ログインをお試しください");
        }

        User saved = userRepository.save(new User(name, email, "general"));
        log.info("利用者登録完了 userId={}", saved.getId());
        return toResponse(saved);
    }

    // AP-25: 管理者アカウント登録（管理者のみ。権限チェックはController側）。作成されるのは常に管理者
    /**
     * 新たな管理者アカウントを登録する（AP-25）。UserController#registerAdminから呼ばれる
     * （管理者権限の確認はController側で完了済み）。
     *
     * @param name  利用者名
     * @param email メールアドレス（登録前に正規化される）
     * @return 登録された管理者アカウントの情報
     */
    @Transactional
    public UserResponse registerAdmin(String name, String email) {
        email = normalizeEmail(email);
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("このメールアドレスは既に登録されています");
        }

        User saved = userRepository.save(new User(name, email, "admin"));
        log.info("管理者アカウント登録完了 userId={}（登録した管理者userId={}）",
                saved.getId(), authContext.getCurrentUser().userId());
        return toResponse(saved);
    }

    // POST /api/login: メールアドレスでユーザーを特定する（パスワード照合は無し＝ダミー認証のまま）
    /**
     * メールアドレスでログインする（AP-01）。UserController#loginから呼ばれる。登録されていないメールアドレスの
     * 場合はUnauthorizedExceptionを投げ、GlobalExceptionHandlerにより401（Unauthorized）になる。
     *
     * @param email ログインに使うメールアドレス
     * @return ログインした利用者の情報
     */
    @Transactional(readOnly = true)
    public UserResponse login(String email) {
        // 利用者列挙対策（docs/09_認証認可設計書.md 9-8）: 「未登録」と直接断定せず、新規登録を案内する形にする
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new UnauthorizedException("ログインできませんでした。入力内容をご確認のうえ、初めてご利用の場合は新規登録してください"));
        return toResponse(user);
    }

    // メールアドレスの表記ゆれ対策（docs/07_バリデーション設計書.md 8-6）: 前後の空白を除去し小文字化してから
    // 重複チェック・検索・保存を行う。`eventapp`データベースの照合順序（utf8mb4_ja_0900_as_cs）は大文字小文字を
    // 区別するため、正規化しないと同じメールアドレスが別アカウントとして扱われてしまう。
    private String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    // GET /api/users: ユーザー一覧（管理者専用、権限チェックはController側）
    /**
     * 登録済み利用者の一覧を取得する（AP-03）。UserController#listから呼ばれる
     * （管理者権限の確認はController側で完了済み）。
     *
     * @return 利用者一覧（利用者ID昇順）
     */
    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAllByOrderByIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    // AP-26: 利用者詳細（SC-15）の表示対象利用者の基本情報。管理者専用、権限チェックはController側
    /**
     * 指定した利用者の基本情報を取得する（AP-26）。UserController#getUserから呼ばれるほか、
     * applicationsOf／favoritesOf／commentsOf（AP-27・28・31）の中でも対象利用者の存在確認
     * （存在しなければNotFoundExceptionを投げ、GlobalExceptionHandlerが404にする）に使われる。
     *
     * @param id 対象利用者ID
     * @return 利用者の基本情報
     */
    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("利用者が見つかりません"));
        return toResponse(user);
    }

    // AP-33: 管理者権限の降格（管理者のみ。権限チェックはController側）。roleをgeneralに変更するのみ。
    // 管理者が1人のみの状態での降格は禁止する（docs/06_詳細設計書.md 7-5 R-17）
    /**
     * 指定した利用者（管理者）を一般利用者に変更する（AP-33）。UserController#demoteから呼ばれる
     * （管理者権限の確認はController側で完了済み）。対象が既に一般利用者、または管理者が1人のみの
     * 状態での実行はBusinessExceptionを投げ、GlobalExceptionHandlerにより400（Bad Request）になる。
     *
     * @param id 降格対象の利用者ID
     * @return 降格後の利用者情報
     */
    @Transactional
    public UserResponse demote(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("利用者が見つかりません"));
        if (!user.isAdmin()) {
            throw new BusinessException("既に一般利用者です");
        }
        if (userRepository.countByRole("admin") <= 1) {
            throw new BusinessException("最後の管理者は降格できません");
        }
        user.demote();
        log.info("管理者権限降格完了 userId={}（実行した管理者userId={}）",
                user.getId(), authContext.getCurrentUser().userId());
        return toResponse(user);
    }

    // AP-34: 利用者の匿名化（退会）。本人、または管理者が実行できる（権限チェックはここで行う）。
    // 対象が管理者の場合は実行不可（先にAP-33で降格する必要がある、docs/06_詳細設計書.md 7-5 R-18）。
    // 物理削除ではなく、名前・メールアドレスを固定の文言・形式に置き換えるのみ（申込等の履歴は残す）
    /**
     * 指定した利用者を匿名化（退会）する（AP-34）。UserController#anonymizeから呼ばれる。
     * 他のAdmin専用メソッドと異なり、本人か管理者かの判定はController側のauthContext.requireAdmin()
     * ではなくこのメソッド自身が行う（本人にも実行を許すため）。本人・管理者のいずれでもない場合は
     * ForbiddenException（403）、対象が管理者または既に退会済みの場合はBusinessException（400）になる。
     *
     * @param id                 退会対象の利用者ID
     * @param requestingUserId   実行者の利用者ID
     * @param requestingIsAdmin  実行者が管理者かどうか
     * @return 匿名化後の利用者情報
     */
    @Transactional
    public UserResponse anonymize(Long id, Long requestingUserId, boolean requestingIsAdmin) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("利用者が見つかりません"));
        boolean isSelf = requestingUserId.equals(id);
        if (!requestingIsAdmin && !isSelf) {
            throw new ForbiddenException("権限がありません");
        }
        if (user.isAdmin()) {
            throw new BusinessException("管理者は退会できません");
        }
        if (user.isAnonymized()) {
            throw new BusinessException("既に退会済みです");
        }
        user.anonymize();
        log.info("利用者匿名化完了 userId={}（実行した利用者userId={} 管理者={}）",
                user.getId(), requestingUserId, requestingIsAdmin);
        return toResponse(user);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getAnonymizedAt());
    }
}
