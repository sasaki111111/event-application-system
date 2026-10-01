package com.example.eventapp.common.validation;

// 実行環境: サーバー側（JVM）。複数DTOで共用するBean Validationの正規表現定数（docs/07_バリデーション設計書.md 8-2参照）。
// @Patternのregexpはコンパイル時定数が必要なため、ここに集約する。
public final class ValidationPatterns {

    // 制御文字（タブ・改行・復帰を除くC0/C1制御文字）およびゼロ幅文字・BOMを拒否する。
    // 空欄に見えて実際には文字が入っている状態（@NotBlankの回避）やコメント・CSV出力の崩れを防ぐ。
    public static final String NO_CONTROL_CHARS =
            "^[^\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F\\u0080-\\u009F\\u200B-\\u200D\\uFEFF]*$";

    // http/httpsで始まるURLのみを許可する（javascript:等のスキームを拒否する）。任意項目のため空文字は許容する。
    public static final String HTTP_URL = "^$|^https?://.*";

    private ValidationPatterns() {
    }
}
