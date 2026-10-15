package com.example.eventapp.dto;

import java.time.LocalDateTime;

// 実行環境: サーバー側（JVM）。利用者情報のレスポンス（docs/30_詳細設計/31_API詳細設計書.md 2.1）。
// ログイン（AP-010）・利用者登録（AP-011）・退会（AP-013）・利用者一覧（AP-140）・利用者情報取得（AP-141）・
// 管理者アカウント登録（AP-145）・降格（AP-146）で共通に使う。
// 利用者登録の直後にそのままログイン扱いにできるよう、ログインと同じ形の項目を返す。
// anonymizedAtはAP-013（利用者の匿名化）で退会済みかどうかを示す（NULLなら未退会）。通常の登録・ログイン等では常にnull。
// 利用者区分はコード（roleCode）と表示名（roleName）の両方を返す（docs/20_基本設計/23_API基本設計書.md 2.8）。
// パスワード・パスワードのハッシュ値は含めない
public record UserResponse(Long userId, String name, String email, Integer roleCode, String roleName,
        LocalDateTime anonymizedAt) {
}
