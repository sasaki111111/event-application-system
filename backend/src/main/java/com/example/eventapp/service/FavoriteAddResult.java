package com.example.eventapp.service;

import com.example.eventapp.dto.FavoriteResponse;

// 実行環境: サーバー側（JVM）。FavoriteService.add()の結果。新規作成か既存かをControllerが
// HTTPステータス（201／200）の出し分けに使うための内部的な型（APIレスポンスそのものではない）。
/**
 * FavoriteService#addの戻り値専用の型。FavoriteController#addがこれを受け取り、createdの値で
 * HTTPステータス（201／200）を出し分ける。
 * {@code record}は、フィールド（ここではresponseとcreated）・コンストラクタ・getter（{@code response()}・
 * {@code created()}）・equals／hashCode／toStringを自動で生成してくれるJavaの構文で、
 * 「値を保持するだけの、変更されない小さなデータの入れ物」を短く書くためのもの。
 *
 * @param response 登録済みのお気に入り内容（新規登録・既存のいずれでも、最終的なレスポンスの内容そのもの）
 * @param created  今回新規に作成されたか（trueなら201、falseなら既存のため200）
 */
public record FavoriteAddResult(FavoriteResponse response, boolean created) {
}
