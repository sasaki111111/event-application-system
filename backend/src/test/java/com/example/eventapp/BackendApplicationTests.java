package com.example.eventapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * アプリケーション全体が正しく起動できるかだけを確認する、起動確認用のスモークテスト（単体テストではない）。
 * @SpringBootTestがSpring全体の設定（Controller/Service/Repositoryなど）を実際に読み込んで組み立てるため、
 * 設定の書き間違い等で起動自体に失敗する場合はこのテストが失敗する。
 */
// 起動確認用のスモークテスト。src/test/resources/application.ymlによりMySQLではなくH2（インメモリ）に接続する。
@SpringBootTest
class BackendApplicationTests {

	// Springの設定一式（アプリケーションコンテキスト）が例外なく組み立てられれば成功。
	// メソッド内に何も書かれていなくても、起動中に例外が発生すればテストは失敗する。
	@Test
	void contextLoads() {
	}

}
