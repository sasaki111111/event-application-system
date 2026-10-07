package com.example.eventapp.service;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.common.CodeNameResolver;
import com.example.eventapp.common.exception.BusinessException;
import com.example.eventapp.common.exception.ForbiddenException;
import com.example.eventapp.common.exception.NotFoundException;
import com.example.eventapp.common.exception.UnauthorizedException;
import com.example.eventapp.dto.UserResponse;
import com.example.eventapp.entity.RoleCode;
import com.example.eventapp.entity.User;
import com.example.eventapp.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 実行環境: サーバー側（JVM）。軽い会員登録・メールアドレスでのログイン・ユーザー一覧（いずれも機能追加）の業務ロジック。
// パスワードはBCryptでハッシュ化して保存する。この経路で登録できるのは常に一般ユーザーのみ（管理者の登録はregisterAdmin）。
/**
 * ログイン・利用者登録（AP-010・AP-011）、利用者一覧（AP-140）、管理者アカウント登録（AP-145）、
 * 利用者詳細の基本情報取得（AP-141）、管理者権限の降格（AP-146）、利用者の匿名化／退会（AP-013）の
 * 業務ロジックを担当するService。UserControllerの各メソッドから呼ばれ、DBアクセスにはUserRepositoryを使う。
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final AuthContext authContext;
    // パスワードのハッシュ化・照合（BCrypt）。PasswordConfigがBeanとして登録する
    private final PasswordEncoder passwordEncoder;
    private final CodeNameResolver codeNameResolver;

    // ログイン失敗時のメッセージ（E-A-006）。メールアドレスの未登録・退会済み・パスワードの不一致を区別しない
    // （docs/20_基本設計/24_方式設計書.md 3.5）
    private static final String LOGIN_FAILED_MESSAGE =
            "ログインできませんでした。入力内容をご確認のうえ、初めてご利用の場合は新規登録してください";

    public UserService(UserRepository userRepository, AuthContext authContext, PasswordEncoder passwordEncoder,
            CodeNameResolver codeNameResolver) {
        this.userRepository = userRepository;
        this.authContext = authContext;
        this.passwordEncoder = passwordEncoder;
        this.codeNameResolver = codeNameResolver;
    }

    /**
     * 新規利用者（一般利用者）を登録する（AP-011）。UserController#registerから呼ばれる。
     *
     * @param name  利用者名
     * @param email メールアドレス（登録前に正規化される）
     * @param password パスワード（ハッシュ化して保存する。条件の検証はDTOで完了済み）
     * @return 登録された利用者の情報
     */
    @Transactional
    public UserResponse register(String name, String email, String password) {
        email = normalizeEmail(email);
        // 利用者列挙対策（docs/20_基本設計/24_方式設計書.md）: 「既に登録済み」と直接断定せず、
        // ログインを促す形にする。管理者登録（registerAdmin）は管理者専用かつ利用者一覧から
        // 既存メールアドレスを確認できるため、こちらは明確なメッセージのままとする
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("この内容では登録できませんでした。ログインをお試しください");
        }

        // パスワードはハッシュ化した値のみを保存する（元の値は保存しない）。利用者区分は常に一般利用者
        User saved = userRepository.save(
                new User(name, email, passwordEncoder.encode(password), RoleCode.GENERAL));
        // 利用者登録の成功はログに出力しない（usersの行とcreated_atから確認できるため。
        // docs/20_基本設計/24_方式設計書.md 6.2）
        return toResponse(saved);
    }

    // AP-145: 管理者アカウント登録（管理者のみ。権限チェックはController側）。作成されるのは常に管理者
    /**
     * 新たな管理者アカウントを登録する（AP-145）。UserController#registerAdminから呼ばれる
     * （管理者権限の確認はController側で完了済み）。
     *
     * @param name  利用者名
     * @param email メールアドレス（登録前に正規化される）
     * @param password 初期パスワード（ハッシュ化して保存する）
     * @return 登録された管理者アカウントの情報
     */
    @Transactional
    public UserResponse registerAdmin(String name, String email, String password) {
        email = normalizeEmail(email);
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("このメールアドレスは既に登録されています");
        }

        User saved = userRepository.save(
                new User(name, email, passwordEncoder.encode(password), RoleCode.ADMIN));
        log.info("管理者アカウント登録完了 userId={} 実行者userId={}",
                saved.getId(), authContext.getCurrentUser().userId());
        return toResponse(saved);
    }

    // POST /api/login: メールアドレスで利用者を特定し、パスワードを照合する
    /**
     * メールアドレスとパスワードでログインする（AP-010）。UserController#loginから呼ばれる。
     * 該当する利用者が存在しない場合・退会済みの場合・パスワードが一致しない場合は、いずれも同じメッセージの
     * UnauthorizedExceptionを投げ、GlobalExceptionHandlerにより401（Unauthorized）になる（R-22）。
     *
     * @param email    ログインに使うメールアドレス
     * @param password 入力されたパスワード
     * @return ログインした利用者の情報
     */
    @Transactional(readOnly = true)
    public UserResponse login(String email, String password) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new UnauthorizedException(LOGIN_FAILED_MESSAGE));
        // 退会済みの利用者はpassword_hashがNULLのため照合できない（ログイン不可）。
        // PasswordEncoder#matchesは、入力されたパスワードと保存済みのハッシュ値が対応するかを判定する
        if (user.isAnonymized() || user.getPasswordHash() == null
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedException(LOGIN_FAILED_MESSAGE);
        }
        // ログインの成功はデータベースに痕跡が残らないため、ログに記録する（docs/30_詳細設計/33_共通詳細設計書.md 6.1）
        log.info("ログイン成功 userId={}", user.getId());
        return toResponse(user);
    }

    // AP-012: パスワード変更。対象は常にログイン中の利用者本人（Controllerが認証情報から利用者IDを渡す）
    /**
     * ログイン中の利用者のパスワードを変更する（AP-012）。UserController#changePasswordから呼ばれる。
     * 現在のパスワードが一致しない場合はBusinessExceptionを投げ、GlobalExceptionHandlerにより
     * 400（Bad Request）になる（R-23）。
     *
     * @param userId          ログイン中の利用者ID
     * @param currentPassword 現在のパスワード
     * @param newPassword     新しいパスワード（条件の検証はDTOで完了済み）
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("利用者が見つかりません"));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException("現在のパスワードが正しくありません");
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        // 変更内容（何が変わったか）はデータベースから分からないため、ログに記録する。パスワード自体は出力しない
        log.info("パスワード変更完了 userId={}", user.getId());
    }

    // メールアドレスの表記ゆれ対策（docs/30_詳細設計/33_共通詳細設計書.md）: 前後の空白を除去し小文字化してから
    // 重複チェック・検索・保存を行う。`eventapp`データベースの照合順序（utf8mb4_ja_0900_as_cs）は大文字小文字を
    // 区別するため、正規化しないと同じメールアドレスが別アカウントとして扱われてしまう。
    private String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    // GET /api/users: ユーザー一覧（管理者専用、権限チェックはController側）
    /**
     * 登録済み利用者の一覧を取得する（AP-140）。UserController#listから呼ばれる
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

    // AP-141: 利用者詳細（SC-141）の表示対象利用者の基本情報。管理者専用、権限チェックはController側
    /**
     * 指定した利用者の基本情報を取得する（AP-141）。UserController#getUserから呼ばれるほか、
     * applicationsOf／favoritesOf／commentsOf（AP-142・28・31）の中でも対象利用者の存在確認
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

    // AP-146: 管理者権限の降格（管理者のみ。権限チェックはController側）。roleをgeneralに変更するのみ。
    // 管理者が1人のみの状態での降格は禁止する（docs/30_詳細設計/32_処理詳細設計書.md R-17）
    /**
     * 指定した利用者（管理者）を一般利用者に変更する（AP-146）。UserController#demoteから呼ばれる
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
        if (userRepository.countByRoleCode(RoleCode.ADMIN) <= 1) {
            throw new BusinessException("最後の管理者は降格できません");
        }
        user.demote();
        log.info("管理者権限降格完了 userId={} 実行者userId={}",
                user.getId(), authContext.getCurrentUser().userId());
        return toResponse(user);
    }

    // AP-013: 利用者の匿名化（退会）。本人、または管理者が実行できる（権限チェックはここで行う）。
    // 対象が管理者の場合は実行不可（先にAP-146で降格する必要がある、docs/30_詳細設計/32_処理詳細設計書.md R-18）。
    // 物理削除ではなく、名前・メールアドレスを固定の文言・形式に置き換えるのみ（申込等の履歴は残す）
    /**
     * 指定した利用者を匿名化（退会）する（AP-013）。UserController#anonymizeから呼ばれる。
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
        log.info("利用者匿名化完了 userId={} 実行者userId={}", user.getId(), requestingUserId);
        return toResponse(user);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRoleCode(),
                codeNameResolver.roleName(user.getRoleCode()), user.getAnonymizedAt());
    }
}
