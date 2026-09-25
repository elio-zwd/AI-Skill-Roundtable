package com.elio.jianyu.backup

import com.elio.jianyu.data.RoundtableDatabase
import java.util.WeakHashMap

/** Associates the runtime-owned gate with every repository/component built for that database. */
internal object BackupOperationGateRegistry {
    private val monitor = Any()
    private val gates = WeakHashMap<RoundtableDatabase, BackupOperationGate>()

    fun register(database: RoundtableDatabase, gate: BackupOperationGate) {
        synchronized(monitor) { gates[database] = gate }
    }

    fun find(database: RoundtableDatabase): BackupOperationGate? = synchronized(monitor) { gates[database] }

    fun remove(database: RoundtableDatabase) {
        synchronized(monitor) { gates.remove(database) }
    }
}
