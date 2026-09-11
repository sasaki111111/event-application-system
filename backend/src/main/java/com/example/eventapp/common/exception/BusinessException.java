package com.example.eventapp.common.exception;

// 400: 業務ルール違反（定員超過・締切超過等、要件定義書§8）。D以降でService層から送出する。
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
