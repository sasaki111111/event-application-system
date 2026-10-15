package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。POST /api/users（軽い会員登録、機能追加）のレスポンス。
// 登録直後にそのままログイン扱いにできるよう、/api/whoamiと同じ形の項目を返す。
// anonymizedAtはAP-013（利用者の匿名化）で退会済みかどうかを示す（NULLなら未退会）。通常の登録・ログイン等では常にnull。
// 利用者区分はコード（roleCode）と表示名（roleName）の両方を返す（docs/20_基本設計/23_API基本設計書.md 2.8）。
// パスワード・パスワードのハッシュ値は含めない
public record UserResponse(Long userId, String name, String email, Integer roleCode, String roleName,
        LocalDateTime anonymizedAt) {
}
