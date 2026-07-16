package com.kosmos.app.data.di;

import com.kosmos.app.data.local.db.KosmosDatabase;
import com.kosmos.app.data.local.db.dao.AuditDao;
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
public final class DatabaseModule_ProvideAuditDaoFactory implements Factory<AuditDao> {
  private final Provider<KosmosDatabase> databaseProvider;

  private DatabaseModule_ProvideAuditDaoFactory(Provider<KosmosDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public AuditDao get() {
    return provideAuditDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideAuditDaoFactory create(
      Provider<KosmosDatabase> databaseProvider) {
    return new DatabaseModule_ProvideAuditDaoFactory(databaseProvider);
  }

  public static AuditDao provideAuditDao(KosmosDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideAuditDao(database));
  }
}
