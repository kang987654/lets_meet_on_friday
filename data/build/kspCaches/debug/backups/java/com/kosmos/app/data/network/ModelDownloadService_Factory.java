package com.kosmos.app.data.network;

import android.content.Context;
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
public final class ModelDownloadService_Factory implements Factory<ModelDownloadService> {
  private final Provider<Context> contextProvider;

  private ModelDownloadService_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public ModelDownloadService get() {
    return newInstance(contextProvider.get());
  }

  public static ModelDownloadService_Factory create(Provider<Context> contextProvider) {
    return new ModelDownloadService_Factory(contextProvider);
  }

  public static ModelDownloadService newInstance(Context context) {
    return new ModelDownloadService(context);
  }
}
