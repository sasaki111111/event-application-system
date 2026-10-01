// 実行環境: サーバー側（JVM）。Repository層＝DBへの問い合わせだけを担当する層。
// Controller/Serviceから直接SQLを書かず、ここ経由でDBとやり取りする（JPA Repositoryを実装）。
package com.example.eventapp.repository;
