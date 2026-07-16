package com.kosmos.app.data.di;

import android.content.Context;
import com.kosmos.app.data.local.db.KosmosDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class DatabaseModule_ProvideKosmosDatabaseFactory implements Factory<KosmosDatabase> {
  private final Provider<Context> contextProvider;

  private DatabaseModule_ProvideKosmosDatabaseFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public KosmosDatabase get() {
    return provideKosmosDatabase(contextProvider.get());
  }

  public static DatabaseModule_ProvideKosmosDatabaseFactory create(
      Provider<Context> contextProvider) {
    return new DatabaseModule_ProvideKosmosDatabaseFactory(contextProvider);
  }

  public static KosmosDatabase provideKosmosDatabase(Context context) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideKosmosDatabase(context));
  }
}
