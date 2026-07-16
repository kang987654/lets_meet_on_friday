package com.kosmos.app.data.local.prefs;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
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
public final class SessionStore_Factory implements Factory<SessionStore> {
  private final Provider<DataStore<Preferences>> dataStoreProvider;

  private SessionStore_Factory(Provider<DataStore<Preferences>> dataStoreProvider) {
    this.dataStoreProvider = dataStoreProvider;
  }

  @Override
  public SessionStore get() {
    return newInstance(dataStoreProvider.get());
  }

  public static SessionStore_Factory create(Provider<DataStore<Preferences>> dataStoreProvider) {
    return new SessionStore_Factory(dataStoreProvider);
  }

  public static SessionStore newInstance(DataStore<Preferences> dataStore) {
    return new SessionStore(dataStore);
  }
}
