package com.example.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ユーザーエンティティ。usersテーブルにマッピングされる。
 *
 * <p>認証サービスにおけるユーザーアカウント情報を保持する。
 * メールアドレスをログインIDとして使用し、パスワードはBCryptハッシュで保存する。</p>
 *
 * <p>JPAアノテーション：</p>
 * <ul>
 *   <li>{@code @Entity} - JPAの管理対象エンティティとしてマーク</li>
 *   <li>{@code @Table} - マッピング先テーブル名とユニーク制約を定義</li>
 * </ul>
 *
 * <p>Lombokアノテーション：</p>
 * <ul>
 *   <li>{@code @Getter} - 全フィールドのgetterを自動生成</li>
 *   <li>{@code @NoArgsConstructor(access = PROTECTED)} - JPAが内部的に使用する引数なしコンストラクタを生成。
 *       protectedにすることで外部からの不正なインスタンス生成を防止。</li>
 * </ul>
 */
@Entity
@Table(name = "users", uniqueConstraints = { @UniqueConstraint(columnNames = "email") })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
  /** 主キー（自動採番）。DBのAUTO_INCREMENT（MySQL）またはSERIAL（PostgreSQL）に対応。 */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** メールアドレス（ログインID）。テーブルレベルのユニーク制約あり。最大50文字。 */
  @Column(nullable = false, length = 50)
  private String email;

  /**
   * BCryptハッシュ化済みパスワード。
   * <p>DBカラム名は "password" だが、Javaフィールド名は {@code hashedPassword} にして
   * 平文パスワードとの混同を防いでいる。BCryptハッシュは最大60文字だが余裕を持って255文字。</p>
   */
  @Column(name = "password", nullable = false, length = 255)
  private String hashedPassword;

  /**
   * ユーザー作成用コンストラクタ。
   *
   * @param email          メールアドレス
   * @param hashedPassword BCryptハッシュ化済みパスワード
   */
  public User(String email, String hashedPassword) {
    this.email = email;
    this.hashedPassword = hashedPassword;
  }
}
