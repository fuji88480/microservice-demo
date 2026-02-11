package com.example.auth.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.auth.model.User;

import lombok.Getter;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Spring Securityの {@link UserDetails} 実装クラス。
 *
 * <p>
 * {@link User} エンティティをSpring Securityの認証で使用できる形に変換するアダプター。
 * {@link com.example.auth.security.UserDetailsServiceImpl} から生成され、
 * 認証処理やJWT生成時にユーザー情報を提供する。
 * </p>
 *
 * <p>
 * 設計方針：
 * </p>
 * <ul>
 * <li>このサービスでは権限（ロール）管理を行わないため、{@code getAuthorities()} は空リストを返す</li>
 * <li>アカウントのロック・期限切れ等の機能は未使用のため、すべてtrueを返す</li>
 * <li>パスワードフィールドは {@code @JsonIgnore} でシリアライズ対象外にし、漏洩を防止</li>
 * </ul>
 */
public class UserDetailsImpl implements UserDetails {
  private static final long serialVersionUID = 1L;

  /** ユーザーID。JWTのsubjectクレームに使用される。 */
  @Getter
  private Long id;

  /** メールアドレス。Spring Securityの {@code username} として使用。 */
  private String email;

  /** BCryptハッシュ化済みパスワード。{@code @JsonIgnore} によりJSON出力から除外。 */
  @JsonIgnore
  private String password;

  public UserDetailsImpl(Long id, String email, String password) {
    this.id = id;
    this.email = email;
    this.password = password;
  }

  /**
   * {@link User} エンティティから {@link UserDetailsImpl} を生成するファクトリメソッド。
   *
   * @param user DBから取得したユーザーエンティティ
   * @return Spring Security用のUserDetails実装
   */
  public static UserDetailsImpl build(User user) {
    return new UserDetailsImpl(
        user.getId(),
        user.getEmail(),
        user.getHashedPassword());
  }

  /**
   * ユーザーの権限リストを返す。
   * <p>
   * このサービスではロール管理を行わないため、空リストを返す。
   * </p>
   */
  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of();
  }

  @Override
  public String getPassword() {
    return password;
  }

  /**
   * Spring Securityの「ユーザー名」としてメールアドレスを返す。
   * <p>
   * AuthenticationManagerがこの値を使ってユーザーを識別する。
   * </p>
   */
  @Override
  public String getUsername() {
    return email;
  }

  /** アカウント有効期限チェック。未使用のため常にtrueを返す。 */
  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  /** アカウントロックチェック。未使用のため常にtrueを返す。 */
  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  /** 資格情報（パスワード）有効期限チェック。未使用のため常にtrueを返す。 */
  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  /** アカウント有効化チェック。未使用のため常にtrueを返す。 */
  @Override
  public boolean isEnabled() {
    return true;
  }
}
