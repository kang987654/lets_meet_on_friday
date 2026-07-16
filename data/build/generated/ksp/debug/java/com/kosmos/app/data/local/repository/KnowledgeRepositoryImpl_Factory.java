package com.kosmos.app.data.local.repository;

import com.kosmos.app.data.local.db.dao.KnowledgeDao;
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
public final class KnowledgeRepositoryImpl_Factory implements Factory<KnowledgeRepositoryImpl> {
  private final Provider<KnowledgeDao> daoProvider;

  private KnowledgeRepositoryImpl_Factory(Provider<KnowledgeDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public KnowledgeRepositoryImpl get() {
    return newInstance(daoProvider.get());
  }

  public static KnowledgeRepositoryImpl_Factory create(Provider<KnowledgeDao> daoProvider) {
    return new KnowledgeRepositoryImpl_Factory(daoProvider);
  }

  public static KnowledgeRepositoryImpl newInstance(KnowledgeDao dao) {
    return new KnowledgeRepositoryImpl(dao);
  }
}
