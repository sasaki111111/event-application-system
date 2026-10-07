package com.example.eventapp.common.validation;

/**
 * こちらはEventDatesValidatorのような独自アノテーションではなく、標準の{@code @Pattern}
 * アノテーションで使う正規表現を定数として共用するためのクラス（DTOのフィールドに
 * {@code @Pattern(regexp = ValidationPatterns.XXX)}の形で付ける）。
 * {@code @Pattern}のregexp属性にはコンパイル時定数（{@code static final}のString）しか渡せないため、
 * 複数のDTOで同じ正規表現を使う場合はここに集約している。
 */
// 実行環境: サーバー側（JVM）。複数DTOで共用するBean Validationの正規表現定数（docs/30_詳細設計/33_共通詳細設計書.md参照）。
// @Patternのregexpはコンパイル時定数が必要なため、ここに集約する。
public final class ValidationPatterns {

    // 制御文字（タブ・改行・復帰を除くC0/C1制御文字）およびゼロ幅文字・BOMを拒否する。
    // 空欄に見えて実際には文字が入っている状態（@NotBlankの回避）やコメント・CSV出力の崩れを防ぐ。
    public static final String NO_CONTROL_CHARS =
            "^[^\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F\\u0080-\\u009F\\u200B-\\u200D\\uFEFF]*$";

    // http/httpsで始まるURLのみを許可する（javascript:等のスキームを拒否する）。任意項目のため空文字は許容する。
    public static final String HTTP_URL = "^$|^https?://.*";

    // パスワードの形式（docs/20_基本設計/24_方式設計書.md 3.1）: 半角の英字・数字・記号のみで構成され、
    // 英字と数字をそれぞれ1文字以上含むこと。文字数（8〜72文字）は@Sizeで別途チェックする。
    // [!-~]は半角の英字・数字・記号（空白を除く印字可能なASCII文字）を表す。
    // 任意項目ではないため空文字は@NotBlankで弾く（@Patternはnullを検証対象外とする）
    public static final String PASSWORD = "^(?=.*[A-Za-z])(?=.*[0-9])[!-~]+$";

    // パスワードの入力エラーのメッセージ（E-V-025・E-V-027）。条件のどれに違反しても同じ文言を返す
    public static final String PASSWORD_MESSAGE = "パスワードは8文字以上72文字以下で、英字と数字をそれぞれ1文字以上含めてください";

    // 定数だけを提供するユーティリティクラスのため、インスタンス化できないようにコンストラクタを隠す
    private ValidationPatterns() {
    }
}
