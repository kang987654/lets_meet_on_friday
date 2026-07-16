package com.kosmos.app.data.local.repository;

import com.kosmos.app.data.local.db.dao.AuditDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class AuditRepositoryImpl_Factory implements Factory<AuditRepositoryImpl> {
  private final Provider<AuditDao> auditDaoProvider;

  private AuditRepositoryImpl_Factory(Provider<AuditDao> auditDaoProvider) {
    this.auditDaoProvider = auditDaoProvider;
  }

  @Override
  public AuditRepositoryImpl get() {
    return newInstance(auditDaoProvider.get());
  }

  public static AuditRepositoryImpl_Factory create(Provider<AuditDao> auditDaoProvider) {
    return new AuditRepositoryImpl_Factory(auditDaoProvider);
  }

  public static AuditRepositoryImpl newInstance(AuditDao auditDao) {
    return new AuditRepositoryImpl(auditDao);
  }
}
