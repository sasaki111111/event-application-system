/**
 * package-info.javaは、パッケージ全体の説明を書くための特殊なJavaファイル（クラスを持たず、
 * {@code package}宣言とJavadocコメントだけで構成される）。ここに書いた内容はJavadoc上で
 * パッケージ単位の説明として扱われる。
 */
// 実行環境: サーバー側（JVM）。Repository層＝DBへの問い合わせだけを担当する層。
// Controller/Serviceから直接SQLを書かず、ここ経由でDBとやり取りする（JPA Repositoryを実装）。
package com.example.eventapp.repository;
