package com.afyzfur.afyzhub.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afyzfur.afyzhub.data.settings.SettingsRepository
import com.afyzfur.afyzhub.domain.model.AiProvider
import com.afyzfur.afyzhub.domain.model.ApiProfile
import com.afyzfur.afyzhub.domain.model.ApiProfileStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 多组 API 配置的增删改查。
 *
 * 与 [SettingsViewModel] 分开而不是扩充它：那个类围绕"单组配置加
 * 防抖自动保存"建立，字段是一批独立的 StateFlow。多组的状态是一个
 * 列表，混在一起会让两套状态互相同步，容易出现改了一处另一处没跟上。
 *
 * 这里的写操作都是"读全量、改一处、写回全量"。组数量在几十以内，
 * 全量写的开销可以忽略，换来的是不需要处理增量合并的并发问题。
 */
class ApiProfilesViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val store: StateFlow<ApiProfileStore> = settingsRepository.apiProfilesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ApiProfileStore()
        )

    init {
        // 首次进入时把旧的单组配置搬过来。放在这里而非应用启动时：
        // 迁移只影响配置页的展示，没必要拖慢冷启动
        viewModelScope.launch { settingsRepository.migrateProfilesIfNeeded() }
    }

    /**
     * 新建一组。
     *
     * 名称留空时按"新配置 N"编号，N 取当前组数加一——不保证唯一，
     * 但只是初始名，用户可以随时改。
     */
    fun addProfile(
        name: String = "",
        group: String = "",
        provider: AiProvider = AiProvider.DEFAULT,
        /** 新建完成后回调新组 id，界面据此跳转到编辑页 */
        onCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val current = settingsRepository.currentProfiles()
            val profile = ApiProfile(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { "新配置 ${current.profiles.size + 1}" },
                group = group.trim(),
                providerId = provider.id
            )
            settingsRepository.saveProfiles(
                current.copy(
                    profiles = current.profiles + profile,
                    // 新建后直接选中：用户建组就是为了用它
                    activeId = profile.id
                )
            )
            // 落盘后再跳转，否则编辑页可能读不到这一组
            onCreated(profile.id)
        }
    }

    /** 整组替换，按 id 定位。找不到 id 时什么都不做 */
    fun updateProfile(profile: ApiProfile) {
        viewModelScope.launch {
            val current = settingsRepository.currentProfiles()
            if (current.profiles.none { it.id == profile.id }) return@launch
            settingsRepository.saveProfiles(
                current.copy(
                    profiles = current.profiles.map {
                        if (it.id == profile.id) profile else it
                    }
                )
            )
        }
    }

    fun selectProfile(id: String) {
        viewModelScope.launch {
            val current = settingsRepository.currentProfiles()
            if (current.profiles.none { it.id == id }) return@launch
            settingsRepository.saveProfiles(current.copy(activeId = id))
        }
    }

    /**
     * 删除一组。
     *
     * 删掉的正好是当前选中项时，把选中移到剩下的第一组；全删空后
     * activeId 置空，界面据此提示新建。
     */
    fun deleteProfile(id: String) {
        viewModelScope.launch {
            val current = settingsRepository.currentProfiles()
            val remaining = current.profiles.filterNot { it.id == id }
            val nextActive = when {
                current.activeId != id -> current.activeId
                else -> remaining.firstOrNull()?.id.orEmpty()
            }
            settingsRepository.saveProfiles(
                current.copy(profiles = remaining, activeId = nextActive)
            )
        }
    }

    /** 复制一组，用于基于现有配置改几项——中转多个 Key 的常见场景 */
    fun duplicateProfile(id: String) {
        viewModelScope.launch {
            val current = settingsRepository.currentProfiles()
            val source = current.profiles.firstOrNull { it.id == id } ?: return@launch
            val copy = source.copy(
                id = UUID.randomUUID().toString(),
                name = "${source.displayName} 副本"
            )
            settingsRepository.saveProfiles(
                current.copy(profiles = current.profiles + copy)
            )
        }
    }
}
