package com.elio.jianyu.backup.design

/**
 * PR09-13A 备份数据白名单设计原型。
 *
 * 不是生产 API，也不会被应用运行时调用。
 */
internal enum class PrototypeBackupObjectType {
    ISSUE,
    STAGE,
    EXECUTION_RUN,
    PARTICIPANT_SNAPSHOT,
    PARTICIPANT_STATE,
    RUN_BUDGET,
    MESSAGE,
    MESSAGE_USAGE,
    CROSS_DISCUSSION,
    DRAFT,
    DRAFT_REVISION,
    CONFIRMED_ARTIFACT,
    ARTIFACT_SOURCE,
    MATERIAL_REFERENCE,
    MATERIAL_USAGE,
    PERSONAL_CONTEXT_ENTRY,
    PERSONAL_CONTEXT_USAGE,
    STAGE_ADVANCEMENT,
    ARCHIVE_EVENT,
    RESUME_EVENT,
    ISSUE_RELATION,
    AUDIO_ASSET_METADATA,
    AUDIO_AVAILABLE_FILE,
    OFFICIAL_SKILL_COMBINATION,
    OFFICIAL_SKILL_COMBINATION_MEMBER,
    HISTORICAL_SKILL_SNAPSHOT,
    SAFE_USER_SETTING,

    API_KEY,
    API_KEY_ENCRYPTED_FILE,
    API_KEY_BINDING,
    KEYSTORE_KEY,
    BACKUP_PASSWORD,
    APP_LOCK_CREDENTIAL,
    ACCESS_TOKEN,
    AUTHORIZATION_HEADER,
    URI_GRANT,
    ABSOLUTE_PATH,
    TEMPORARY_PART_FILE,
    CACHE_FILE,
    ORPHAN_FILE,
    UNCONFIRMED_EDITOR_MEMORY,
    PENDING_MESSAGE,
    RUNNING_RUN,
    PENDING_AUDIO,
    TELEMETRY_BODY,
    PURGED_CONTENT,
    EXTERNAL_URI_ORIGINAL_FILE,
    STATIC_APK_SKILL_ASSET,
}

internal enum class PrototypePurgeState {
    NONE,
    REQUESTED,
    WAITING_FOR_TASKS,
    CANCELING_TASKS,
    DELETING_FILES,
    READY_FOR_DATABASE_PURGE,
    DATABASE_PURGING,
    FAILED_RETRYABLE,
}

internal data class PrototypeIssueBackupState(
    val purgeState: PrototypePurgeState = PrototypePurgeState.NONE,
    val hasRunningRun: Boolean = false,
    val hasPendingMessage: Boolean = false,
    val hasActiveAudioWork: Boolean = false,
)

internal object BackupDataScopePrototype {
    val portableWhitelist: Set<PrototypeBackupObjectType> = setOf(
        PrototypeBackupObjectType.ISSUE,
        PrototypeBackupObjectType.STAGE,
        PrototypeBackupObjectType.EXECUTION_RUN,
        PrototypeBackupObjectType.PARTICIPANT_SNAPSHOT,
        PrototypeBackupObjectType.PARTICIPANT_STATE,
        PrototypeBackupObjectType.RUN_BUDGET,
        PrototypeBackupObjectType.MESSAGE,
        PrototypeBackupObjectType.MESSAGE_USAGE,
        PrototypeBackupObjectType.CROSS_DISCUSSION,
        PrototypeBackupObjectType.DRAFT,
        PrototypeBackupObjectType.DRAFT_REVISION,
        PrototypeBackupObjectType.CONFIRMED_ARTIFACT,
        PrototypeBackupObjectType.ARTIFACT_SOURCE,
        PrototypeBackupObjectType.MATERIAL_REFERENCE,
        PrototypeBackupObjectType.MATERIAL_USAGE,
        PrototypeBackupObjectType.PERSONAL_CONTEXT_ENTRY,
        PrototypeBackupObjectType.PERSONAL_CONTEXT_USAGE,
        PrototypeBackupObjectType.STAGE_ADVANCEMENT,
        PrototypeBackupObjectType.ARCHIVE_EVENT,
        PrototypeBackupObjectType.RESUME_EVENT,
        PrototypeBackupObjectType.ISSUE_RELATION,
        PrototypeBackupObjectType.AUDIO_ASSET_METADATA,
        PrototypeBackupObjectType.AUDIO_AVAILABLE_FILE,
        PrototypeBackupObjectType.OFFICIAL_SKILL_COMBINATION,
        PrototypeBackupObjectType.OFFICIAL_SKILL_COMBINATION_MEMBER,
        PrototypeBackupObjectType.HISTORICAL_SKILL_SNAPSHOT,
        PrototypeBackupObjectType.SAFE_USER_SETTING,
    )

    val permanentExclusions: Set<PrototypeBackupObjectType> =
        PrototypeBackupObjectType.entries.toSet() - portableWhitelist

    fun requireIssueStable(state: PrototypeIssueBackupState) {
        if (state.purgeState != PrototypePurgeState.NONE) {
            throw BackupDesignException(BackupDesignErrorCode.PURGE_IN_PROGRESS)
        }
        if (state.hasRunningRun || state.hasPendingMessage || state.hasActiveAudioWork) {
            throw BackupDesignException(BackupDesignErrorCode.ACTIVE_WORK_IN_PROGRESS)
        }
    }

    fun requirePortableObject(type: PrototypeBackupObjectType) {
        if (type !in portableWhitelist) {
            throw BackupDesignException(
                if (type == PrototypeBackupObjectType.ABSOLUTE_PATH) {
                    BackupDesignErrorCode.PATH_INVALID
                } else {
                    BackupDesignErrorCode.VERIFICATION_FAILED
                },
            )
        }
    }

    fun portableSourceLocator(
        scheme: String?,
        rawLocator: String?,
    ): PrototypePortableLocator {
        if (rawLocator.isNullOrBlank()) return PrototypePortableLocator.None
        if (rawLocator.indexOf('\u0000') >= 0) {
            throw BackupDesignException(BackupDesignErrorCode.PATH_INVALID)
        }
        val normalizedScheme = scheme?.lowercase()
        return when (normalizedScheme) {
            "http", "https" -> PrototypePortableLocator.PublicUrl(rawLocator)
            "content", "file" -> PrototypePortableLocator.UnavailableAfterImport(
                sourceType = normalizedScheme,
            )
            else -> {
                if (rawLocator.startsWith('/') || rawLocator.startsWith('\\') ||
                    rawLocator.contains("../") || rawLocator.contains("..\\")
                ) {
                    throw BackupDesignException(BackupDesignErrorCode.PATH_INVALID)
                }
                PrototypePortableLocator.UnavailableAfterImport(
                    sourceType = normalizedScheme ?: "unknown",
                )
            }
        }
    }
}

internal sealed interface PrototypePortableLocator {
    data object None : PrototypePortableLocator
    data class PublicUrl(val value: String) : PrototypePortableLocator
    data class UnavailableAfterImport(val sourceType: String) : PrototypePortableLocator
}
