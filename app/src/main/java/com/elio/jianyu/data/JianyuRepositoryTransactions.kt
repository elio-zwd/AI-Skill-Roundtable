package com.elio.jianyu.data

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException

/**
 * PR09-03 唯一数据库事务与异常映射协调器。
 *
 * 领域组件只能通过此类型开启跨表事务；协程取消必须原样向上传播，不能被误报为存储失败。
 */
internal class JianyuRepositoryTransactions(
    private val database: RoundtableDatabase
) {
    private val databaseWasOpened = AtomicBoolean(database.isOpen)

    private val dao: JianyuRepositoryDao
        get() = database.jianyuRepositoryDao()

    suspend fun <T> transaction(
        operation: String,
        block: suspend JianyuRepositoryDao.() -> RepositoryResult<T>
    ): RepositoryResult<T> {
        return execute(operation) {
            transactionRaw(block)
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
        if (databaseWasOpened.get() && !database.isOpen) {
            throw RepositoryStorageUnavailableAbort()
        }
        return database.withTransaction {
            databaseWasOpened.set(true)
            dao.block()
        }
    }
}

internal class RepositoryCompatibilityAbort(
    val code: String
) : RuntimeException()

internal class RepositoryStorageUnavailableAbort : RuntimeException()
