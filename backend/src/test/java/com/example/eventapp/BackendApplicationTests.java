package com.example.eventapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 起動確認用のスモークテスト。src/test/resources/application.ymlによりMySQLではなくH2（インメモリ）に接続する。
@SpringBootTest
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
