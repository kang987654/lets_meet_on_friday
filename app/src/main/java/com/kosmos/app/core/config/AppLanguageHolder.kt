package com.kosmos.app.core.config

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.kosmos.app.assistant.orchestrator.ConversationPrewarmer
import com.kosmos.app.core.common.AppLanguage
import com.kosmos.app.core.common.runCatchingCancellable
import com.kosmos.app.data.local.prefs.SettingsDataStore
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.platform.notification.NotificationChannels
import com.kosmos.app.widget.WidgetRefresher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * [AppLanguageHolder]
 * 앱 전체의 현재 표시/비서 언어([AppLanguage])와 설정 선택지([AppLanguage.Option])를 단일 정본으로 관리합니다 (0.40.0 M2a).
 *
 * ### Architecture Context
 * - **Layer**: App (Config)
 * - **Dependencies**: [LocaleManager] (API 33+ 정본), [SettingsDataStore] (API 26~32 폴백), [ModelRunner], [ConversationPrewarmer], [WidgetRefresher]
 *
 * ### Key Flow
 * 1. `KosmosApp.onCreate` 가 [start] 를 호출해 초기 언어를 읽습니다 (생성자/init 에서 구독하지 않음 — AGENTS §2-④).
 * 2. 휴대폰 시스템 설정에서 앱별 언어를 바꾸면 `KosmosApp.onConfigurationChanged` 가 [onConfigurationChanged] 를 불러 즉시 동기화합니다.
 * 3. 앱 설정 화면에서 `시스템 / 한국어 / English` 를 고르면 [setOption] 이 `LocaleManager.setApplicationLocales` 를 호출하고 상태를 갱신합니다.
 * 4. 언어가 실제로 바뀌면 살아 있는 대화(KV)를 무효화하고([ModelRunner.invalidateConversation]) 다음 턴 대화를 미리 준비하며([ConversationPrewarmer]),
 *    알림 채널 이름과 홈 위젯도 새 언어로 갱신합니다.
 */
@Singleton
class AppLanguageHolder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val modelRunner: Provider<ModelRunner>,
    private val conversationPrewarmer: Provider<ConversationPrewarmer>,
    private val widgetRefresher: Provider<WidgetRefresher>,
    private val sessionStore: com.kosmos.app.data.local.prefs.SessionStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // [WHY] 초기 기본값은 KO — Hilt 테스트나 start() 호출 전에도 기존 한국어 동작과 100% 일치한다.
    private val _language = MutableStateFlow(AppLanguage.KO)
    val languageFlow: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _option = MutableStateFlow(AppLanguage.Option.SYSTEM)
    val optionFlow: StateFlow<AppLanguage.Option> = _option.asStateFlow()

    /** 현재 적용된 [AppLanguage] (`KO` 또는 `EN`). */
    val current: AppLanguage
        get() = _language.value

    /**
     * `KosmosApp.onCreate` 에서 한 번 호출해 현재 시스템/앱별 언어를 읽습니다 (멱등).
     *
     * [WHY] 생성자/init 에서 코루틴이나 시스템 조회를 돌리지 않는다(AGENTS §2-④).
     */
    fun start() {
        syncFromSystem(notifyChange = false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            scope.launch {
                val savedKey = runCatchingCancellable { settingsDataStore.appLanguageOptionFlow.first() }
                    .getOrDefault(AppLanguage.Option.SYSTEM.key)
                val opt = AppLanguage.Option.fromKey(savedKey)
                _option.value = opt
                updateResolvedLanguage(opt.resolve(currentConfigLocale(context.resources.configuration)), notifyChange = false)
            }
        }
    }

    /** `KosmosApp.onConfigurationChanged` 에서 호출되어 OS 앱별 언어 변경을 즉시 반영합니다. */
    fun onConfigurationChanged(newConfig: Configuration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val opt = readOptionFromLocaleManager()
            _option.value = opt
            val resolved = when (opt) {
                AppLanguage.Option.KO -> AppLanguage.KO
                AppLanguage.Option.EN -> AppLanguage.EN
                AppLanguage.Option.SYSTEM -> AppLanguage.fromLocale(currentConfigLocale(newConfig))
            }
            updateResolvedLanguage(resolved, notifyChange = true)
        } else {
            val resolved = _option.value.resolve(currentConfigLocale(newConfig))
            updateResolvedLanguage(resolved, notifyChange = true)
        }
    }

    /** 설정 화면에서 사용자가 언어 선택지(`SYSTEM` / `KO` / `EN`)를 바꿨을 때 호출합니다. */
    fun setOption(option: AppLanguage.Option) {
        _option.value = option
        val resolved = when (option) {
            AppLanguage.Option.KO -> AppLanguage.KO
            AppLanguage.Option.EN -> AppLanguage.EN
            AppLanguage.Option.SYSTEM -> AppLanguage.fromLocale(systemDeviceLocale())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                val locales = if (option.tag.isEmpty()) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(option.tag)
                localeManager?.applicationLocales = locales
            }
        }
        scope.launch {
            runCatchingCancellable { settingsDataStore.saveAppLanguageOption(option.key) }
        }
        updateResolvedLanguage(resolved, notifyChange = true)
    }

    /** 테스트나 명시적 언어 지정이 필요한 곳에서 현재 언어를 직접 설정합니다. */
    fun setLanguageForTest(language: AppLanguage) {
        _language.value = language
    }

    private fun syncFromSystem(notifyChange: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val opt = readOptionFromLocaleManager()
            _option.value = opt
            val resolved = when (opt) {
                AppLanguage.Option.KO -> AppLanguage.KO
                AppLanguage.Option.EN -> AppLanguage.EN
                AppLanguage.Option.SYSTEM -> AppLanguage.fromLocale(currentConfigLocale(context.resources.configuration))
            }
            updateResolvedLanguage(resolved, notifyChange = notifyChange)
        } else {
            val resolved = AppLanguage.fromLocale(currentConfigLocale(context.resources.configuration))
            updateResolvedLanguage(resolved, notifyChange = notifyChange)
        }
    }

    private fun readOptionFromLocaleManager(): AppLanguage.Option {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return _option.value
        return runCatching {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val appLocales = localeManager?.applicationLocales
            if (appLocales == null || appLocales.isEmpty) {
                AppLanguage.Option.SYSTEM
            } else {
                AppLanguage.Option.fromLocaleTag(appLocales.get(0)?.toLanguageTag())
            }
        }.getOrDefault(AppLanguage.Option.SYSTEM)
    }

    private fun currentConfigLocale(config: Configuration): Locale? =
        if (!config.locales.isEmpty) config.locales.get(0) else Locale.getDefault()

    private fun systemDeviceLocale(): Locale? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                val sysLocales = localeManager?.systemLocales
                if (sysLocales != null && !sysLocales.isEmpty) return sysLocales.get(0)
            }
        }
        return currentConfigLocale(context.resources.configuration)
    }

    private fun updateResolvedLanguage(newLang: AppLanguage, notifyChange: Boolean) {
        val prev = _language.value
        _language.value = newLang
        if (notifyChange && prev != newLang) {
            // [WHY] 언어가 바뀌면 시스템 지시가 바뀌므로 살아 있는 대화(KV)를 무효화하고 새 언어로 미리 준비한다.
            runCatching { modelRunner.get().invalidateConversation() }
            runCatching { NotificationChannels.ensureCreated(context, newLang) }
            scope.launch {
                runCatchingCancellable { widgetRefresher.get().refresh() }
                val activeSession = runCatchingCancellable { sessionStore.activeSessionIdFlow.first() }.getOrNull()
                if (activeSession != null) {
                    runCatchingCancellable { conversationPrewarmer.get().afterTurn(activeSession) }
                }
            }
        }
    }
}
