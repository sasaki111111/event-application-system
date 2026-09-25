package com.example.eventapp.controller;

import com.example.eventapp.common.AuthContext;
import com.example.eventapp.dto.LoginRequest;
import com.example.eventapp.dto.UserRegisterRequest;
import com.example.eventapp.dto.UserResponse;
import com.example.eventapp.service.UserService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 実行環境: サーバー側（JVM、localhost:8080）。ログイン（機能追加：メールアドレス方式）・
// 軽い会員登録（機能追加）・ユーザー一覧（機能追加：管理者向けマスタ確認用）・
// 管理者アカウント登録（AP-25、機能追加）。
// login・registerはログイン前（未認証）に呼ばれるため、認証不要（WebConfig／AuthInterceptor参照）。
@RestController
public class UserController {

    private final UserService userService;
    private final AuthContext authContext;
    private final Validator validator;

    public UserController(UserService userService, AuthContext authContext, Validator validator) {
        this.userService = userService;
        this.authContext = authContext;
        this.validator = validator;
    }

    // POST /api/login（認証不要）。メールアドレスからユーザーを特定し、そのロールを返す
    @PostMapping("/api/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request) {
        return userService.login(request.email());
    }

    // POST /api/users（認証不要。作成されるのは常に一般ユーザー）
    @PostMapping("/api/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody UserRegisterRequest request) {
        return userService.register(request.name(), request.email());
    }

    // GET /api/users（管理者のみ。マスタ確認用の一覧）
    @GetMapping("/api/users")
    public List<UserResponse> list() {
        authContext.requireAdmin();
        return userService.list();
    }

    // AP-25 POST /api/admins（管理者のみ。作成されるのは常に管理者）
    // API設計書§2.5: 権限チェックを先に行うため@Validは使わず、権限チェック後に手動でバリデーションする
    @PostMapping("/api/admins")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse registerAdmin(@RequestBody UserRegisterRequest request) {
        authContext.requireAdmin();
        validate(request);
        return userService.registerAdmin(request.name(), request.email());
    }

    private void validate(UserRegisterRequest request) {
        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
