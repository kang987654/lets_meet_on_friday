package com.kosmos.app.data.local.repository;

import com.kosmos.app.data.local.db.dao.ConversationDao;
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
public final class ConversationRepositoryImpl_Factory implements Factory<ConversationRepositoryImpl> {
  private final Provider<ConversationDao> conversationDaoProvider;

  private ConversationRepositoryImpl_Factory(Provider<ConversationDao> conversationDaoProvider) {
    this.conversationDaoProvider = conversationDaoProvider;
  }

  @Override
  public ConversationRepositoryImpl get() {
    return newInstance(conversationDaoProvider.get());
  }

  public static ConversationRepositoryImpl_Factory create(
      Provider<ConversationDao> conversationDaoProvider) {
    return new ConversationRepositoryImpl_Factory(conversationDaoProvider);
  }

  public static ConversationRepositoryImpl newInstance(ConversationDao conversationDao) {
    return new ConversationRepositoryImpl(conversationDao);
  }
}
