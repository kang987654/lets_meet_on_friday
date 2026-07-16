package com.kosmos.app.data.local.file;

import android.content.Context;
import com.kosmos.app.data.local.db.KosmosDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class ExportImportManager_Factory implements Factory<ExportImportManager> {
  private final Provider<Context> contextProvider;

  private final Provider<KosmosDatabase> databaseProvider;

  private ExportImportManager_Factory(Provider<Context> contextProvider,
      Provider<KosmosDatabase> databaseProvider) {
    this.contextProvider = contextProvider;
    this.databaseProvider = databaseProvider;
  }

  @Override
  public ExportImportManager get() {
    return newInstance(contextProvider.get(), databaseProvider.get());
  }

  public static ExportImportManager_Factory create(Provider<Context> contextProvider,
      Provider<KosmosDatabase> databaseProvider) {
    return new ExportImportManager_Factory(contextProvider, databaseProvider);
  }

  public static ExportImportManager newInstance(Context context, KosmosDatabase database) {
    return new ExportImportManager(context, database);
  }
}
