package com.example.eventapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * このSpring Bootアプリケーション全体の起動エントリーポイント（Javaプログラムの実行が始まるクラス）。
 * {@code @SpringBootApplication}が付いたこのクラスをもとに、Spring Bootが設定を自動で読み込み、
 * Controller・Service・Repository・共通処理(common)など、このプロジェクトの全クラスを
 * まとめて動かせる状態（DIコンテナ）を立ち上げる。
 */
// バックエンドの起動エントリーポイント。実行環境: サーバー側（JVM）。
// `./mvnw spring-boot:run` で起動し、内蔵Tomcatがlocalhost:8080で待ち受ける。
// このJVMプロセスの中でController/Service/Repository/共通処理(common)が動く。
@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		// SpringApplication.run(...) が、設定の読み込み・DIコンテナの構築・内蔵Tomcatの起動までを
		// すべて行ってくれる。このメソッドが呼ばれた後、ブラウザやSwagger UIからのHTTPリクエストを
		// 受け付けられる状態になる。
		SpringApplication.run(BackendApplication.class, args);
	}

}
