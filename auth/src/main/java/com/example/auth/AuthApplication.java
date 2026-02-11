package com.example.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 認証サービス（auth-service）のエントリーポイント。
 *
 * <p>マイクロサービスアーキテクチャにおけるユーザー認証・認可を担当するサービス。
 * JWT（アクセストークン）とリフレッシュトークン（Redis管理）による
 * ステートレス認証を提供する。</p>
 *
 * <p>{@code @SpringBootApplication} は以下の3つのアノテーションを集約したもの：</p>
 * <ul>
 *   <li>{@code @Configuration} - JavaベースのBean定義クラスとして機能</li>
 *   <li>{@code @EnableAutoConfiguration} - クラスパス上のライブラリに基づいてBeanを自動構成</li>
 *   <li>{@code @ComponentScan} - このパッケージ配下のコンポーネントを自動検出・登録</li>
 * </ul>
 */
@SpringBootApplication
public class AuthApplication {

	/**
	 * アプリケーションのメインメソッド。
	 *
	 * <p>Spring Bootの組み込みTomcatサーバーを起動し、
	 * 認証サービスをHTTPリクエスト受付可能な状態にする。</p>
	 *
	 * @param args コマンドライン引数（Spring Bootのプロパティ上書き等に使用可能）
	 */
	public static void main(String[] args) {
		SpringApplication.run(AuthApplication.class, args);
	}

}
