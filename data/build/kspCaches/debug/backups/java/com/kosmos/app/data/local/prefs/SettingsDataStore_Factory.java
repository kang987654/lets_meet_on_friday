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
public final class SettingsDataStore_Factory implements Factory<SettingsDataStore> {
  private final Provider<DataStore<Preferences>> dataStoreProvider;

  private SettingsDataStore_Factory(Provider<DataStore<Preferences>> dataStoreProvider) {
    this.dataStoreProvider = dataStoreProvider;
  }

  @Override
  public SettingsDataStore get() {
    return newInstance(dataStoreProvider.get());
  }

  public static SettingsDataStore_Factory create(
      Provider<DataStore<Preferences>> dataStoreProvider) {
    return new SettingsDataStore_Factory(dataStoreProvider);
  }

  public static SettingsDataStore newInstance(DataStore<Preferences> dataStore) {
    return new SettingsDataStore(dataStore);
  }
}
