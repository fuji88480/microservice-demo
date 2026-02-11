package com.example.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.auth.model.User;

/**
 * ユーザーエンティティのリポジトリインターフェース。
 *
 * <p>{@link JpaRepository} を継承することで、基本的なCRUD操作（save, findById, delete等）が
 * 自動的に提供される。エンティティ型は {@link User}、主キー型は {@link Long}。</p>
 *
 * <p>{@code @Repository} はSpringのステレオタイプアノテーションで、
 * データアクセス層のコンポーネントとしてDIコンテナに登録する。
 * また、データアクセス例外をSpringの {@code DataAccessException} に変換する。</p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
  /**
   * メールアドレスでユーザーを検索する。
   *
   * <p>Spring Data JPAのメソッド名規約により、以下のSQLが自動生成される：
   * {@code SELECT * FROM users WHERE email = ?}</p>
   *
   * @param email 検索対象のメールアドレス
   * @return ユーザーが見つかった場合はそのUser、見つからなければ空のOptional
   */
  Optional<User> findByEmail(String email);

  /**
   * 指定メールアドレスのユーザーが存在するか確認する。
   *
   * <p>Spring Data JPAのメソッド名規約により、以下のSQLが自動生成される：
   * {@code SELECT COUNT(*) > 0 FROM users WHERE email = ?}</p>
   *
   * <p>サインアップ時のメールアドレス重複チェックに使用。
   * findByEmailと異なりエンティティを取得しないため、存在確認のみの用途では効率的。</p>
   *
   * @param email チェック対象のメールアドレス
   * @return 存在する場合はtrue
   */
  boolean existsByEmail(String email);
}
