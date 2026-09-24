package com.example.eventapp.service;

import com.example.eventapp.dto.FavoriteResponse;

// 実行環境: サーバー側（JVM）。FavoriteService.add()の結果。新規作成か既存かをControllerが
// HTTPステータス（201／200）の出し分けに使うための内部的な型（APIレスポンスそのものではない）。
public record FavoriteAddResult(FavoriteResponse response, boolean created) {
}
