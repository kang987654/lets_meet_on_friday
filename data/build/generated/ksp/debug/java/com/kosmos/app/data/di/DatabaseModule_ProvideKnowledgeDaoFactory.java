package com.kosmos.app.data.di;

import com.kosmos.app.data.local.db.KosmosDatabase;
import com.kosmos.app.data.local.db.dao.KnowledgeDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class DatabaseModule_ProvideKnowledgeDaoFactory implements Factory<KnowledgeDao> {
  private final Provider<KosmosDatabase> databaseProvider;

  private DatabaseModule_ProvideKnowledgeDaoFactory(Provider<KosmosDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public KnowledgeDao get() {
    return provideKnowledgeDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideKnowledgeDaoFactory create(
      Provider<KosmosDatabase> databaseProvider) {
    return new DatabaseModule_ProvideKnowledgeDaoFactory(databaseProvider);
  }

  public static KnowledgeDao provideKnowledgeDao(KosmosDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideKnowledgeDao(database));
  }
}
