package com.example.eventapp.common.exception;

// 実行環境: サーバー側（JVM）。401: X-User-Idヘッダ無し、または存在しないuserId（API設計書§0）。
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
