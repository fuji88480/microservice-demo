package com.example.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redisの接続・シリアライズ設定クラス。
 *
 * 【Redisをリフレッシュトークン保存に使う理由】
 * - インメモリDBなので読み書きが高速 (トークン検証は毎回のリフレッシュで発生)
 * - TTL(有効期限)をキー単位で設定可能 → 期限切れトークンの自動削除
 * - サーバー側でトークンを無効化可能 → ログアウト時にRedisから削除するだけ
 * - RDBに保存するとリフレッシュの度にディスクI/Oが発生し非効率
 *
 * @Configuration: このクラスがSpringの設定クラスであることを示す。
 *   内部の@Beanメソッドの戻り値がDIコンテナに登録される。
 */
@Configuration
public class RedisConfig {

  /**
   * RedisTemplateのBean定義。
   * RedisTemplateはRedisへのCRUD操作を提供する中心的なクラス。
   *
   * 【シリアライザの設定が必要な理由】
   * デフォルトのRedisTemplateはJavaのシリアライズ(JdkSerializationRedisSerializer)を使用する。
   * これだとRedisに保存される値がバイナリ形式になり、redis-cliで確認できない。
   * StringRedisSerializerを設定することで、キー・値ともにUTF-8文字列として保存される。
   *
   * @param connectionFactory Spring Bootが自動構成するRedis接続ファクトリ
   *   application.propertiesの spring.data.redis.host / port から接続情報を取得
   * @return 設定済みのRedisTemplate<String, String>
   */
  @Bean
  public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, String> template = new RedisTemplate<>();

    // Redis接続ファクトリを設定 (ホスト・ポート・接続プール等の情報を持つ)
    template.setConnectionFactory(connectionFactory);

    // キーのシリアライザ: "refresh_token:abc-123" のような文字列でRedisに保存
    template.setKeySerializer(new StringRedisSerializer());

    // 値のシリアライザ: ユーザーIDを "42" のような文字列でRedisに保存
    template.setValueSerializer(new StringRedisSerializer());

    return template;
  }
}
