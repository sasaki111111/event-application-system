package com.example.eventapp.common.exception;

// 実行環境: サーバー側（JVM）。404: 指定IDのリソースが存在しない（API設計書§0「不在=404」）。
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
