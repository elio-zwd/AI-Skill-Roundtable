package com.elio.jianyu.data

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException

/**
 * PR09-03 唯一数据库事务与异常映射协调器。
 *
 * 领域组件只能通过此类型开启跨表事务；协程取消必须原样向上传播，不能被误报为存储失败。
 * 事务块返回 [RepositoryResult.Failure] 时必须先触发 Room 回滚，再在事务外恢复原错误。
 */
internal class JianyuRepositoryTransactions(
    private val database: RoundtableDatabase
) {
    private val dao: JianyuRepositoryDao
        get() = database.jianyuRepositoryDao()

    private val collaborationDao: CollaborationDao
        get() = database.collaborationDao()

    private val stageAdvancementDao: StageAdvancementDao
        get() = database.stageAdvancementDao()

    suspend fun <T> transaction(
        operation: String,
        block: suspend JianyuRepositoryDao.() -> RepositoryResult<T>
    ): RepositoryResult<T> {
        return execute(operation) {
            transactionRaw(block)
        }
    }

    suspend fun <T> collaborationTransaction(
        operation: String,
        block: suspend CollaborationTransactionScope.() -> RepositoryResult<T>,
    ): RepositoryResult<T> {
        return execute(operation) {
            if (database.isExplicitlyClosed) {
                throw RepositoryStorageUnavailableAbort()
            }
            database.withTransaction {
                val scope = CollaborationTransactionScope(dao, collaborationDao)
                when (val result = scope.block()) {
                    is RepositoryResult.Success -> result
                    is RepositoryResult.Failure -> throw RepositoryTransactionFailureAbort(result.error)
                }
            }
        }
    }

    suspend fun <T> stageAdvancementTransaction(
        operation: String,
        block: suspend StageAdvancementDao.() -> RepositoryResult<T>,
    ): RepositoryResult<T> {
        return execute(operation) {
            if (database.isExplicitlyClosed) {
                throw RepositoryStorageUnavailableAbort()
            }
            database.withTransaction {
                when (val result = stageAdvancementDao.block()) {
                    is RepositoryResult.Success -> result
                    is RepositoryResult.Failure -> throw RepositoryTransactionFailureAbort(result.error)
                }
            }
        }
    }

    suspend fun <T> execute(
        operation: String,
        block: suspend () -> RepositoryResult<T>
    ): RepositoryResult<T> {
        return try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: RepositoryTransactionFailureAbort) {
            RepositoryResult.Failure(error.repositoryError)
        } catch (error: RepositoryCompatibilityAbort) {
            RepositoryResult.Failure(
                RepositoryError.CompatibilityFailure(operation, error.code)
            )
        } catch (error: RepositoryStorageUnavailableAbort) {
            RepositoryResult.Failure(
                RepositoryError.StorageFailure(operation, retryable = true)
            )
        } catch (error: SQLiteConstraintException) {
            RepositoryResult.Failure(
                RepositoryError.ConstraintViolation(operation, "sqlite_constraint")
            )
        } catch (error: SQLiteException) {
            RepositoryResult.Failure(
                RepositoryError.StorageFailure(operation, retryable = true)
            )
        } catch (error: IllegalArgumentException) {
            RepositoryResult.Failure(
                RepositoryError.ConstraintViolation(operation, "invalid_argument")
            )
        } catch (error: IllegalStateException) {
            RepositoryResult.Failure(
                RepositoryError.StorageFailure(operation, retryable = true)
            )
        } catch (error: Exception) {
            RepositoryResult.Failure(
                RepositoryError.StorageFailure(operation, retryable = true)
            )
        }
    }

    suspend fun <T> transactionRaw(
        block: suspend JianyuRepositoryDao.() -> RepositoryResult<T>
    ): RepositoryResult<T> {
        if (database.isExplicitlyClosed) {
            throw RepositoryStorageUnavailableAbort()
        }
        return database.withTransaction {
            when (val result = dao.block()) {
                is RepositoryResult.Success -> result
                is RepositoryResult.Failure -> throw RepositoryTransactionFailureAbort(result.error)
            }
        }
    }
}

internal class CollaborationTransactionScope(
    val core: JianyuRepositoryDao,
    val collaboration: CollaborationDao,
)

internal class RepositoryTransactionFailureAbort(
    val repositoryError: RepositoryError
) : RuntimeException()

internal class RepositoryCompatibilityAbort(
    val code: String
) : RuntimeException()

internal class RepositoryStorageUnavailableAbort : RuntimeException()
