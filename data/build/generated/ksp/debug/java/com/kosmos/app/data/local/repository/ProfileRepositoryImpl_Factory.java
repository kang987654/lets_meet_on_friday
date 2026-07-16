package com.kosmos.app.data.local.repository;

import com.kosmos.app.data.local.db.dao.ProfileDao;
import com.kosmos.app.data.local.prefs.SettingsDataStore;
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
public final class ProfileRepositoryImpl_Factory implements Factory<ProfileRepositoryImpl> {
  private final Provider<ProfileDao> profileDaoProvider;

  private final Provider<SettingsDataStore> settingsDataStoreProvider;

  private ProfileRepositoryImpl_Factory(Provider<ProfileDao> profileDaoProvider,
      Provider<SettingsDataStore> settingsDataStoreProvider) {
    this.profileDaoProvider = profileDaoProvider;
    this.settingsDataStoreProvider = settingsDataStoreProvider;
  }

  @Override
  public ProfileRepositoryImpl get() {
    return newInstance(profileDaoProvider.get(), settingsDataStoreProvider.get());
  }

  public static ProfileRepositoryImpl_Factory create(Provider<ProfileDao> profileDaoProvider,
      Provider<SettingsDataStore> settingsDataStoreProvider) {
    return new ProfileRepositoryImpl_Factory(profileDaoProvider, settingsDataStoreProvider);
  }

  public static ProfileRepositoryImpl newInstance(ProfileDao profileDao,
      SettingsDataStore settingsDataStore) {
    return new ProfileRepositoryImpl(profileDao, settingsDataStore);
  }
}
