package com.example.eventapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// バックエンドの起動エントリーポイント。実行環境: サーバー側（JVM）。
// `./mvnw spring-boot:run` で起動し、内蔵Tomcatがlocalhost:8080で待ち受ける。
// このJVMプロセスの中でController/Service/Repository/共通処理(common)が動く。
@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
