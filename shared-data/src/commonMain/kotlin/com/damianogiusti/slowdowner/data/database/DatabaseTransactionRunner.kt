package com.damianogiusti.slowdowner.data.database

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/** Runs DAO calls atomically. An interface so repositories stay testable with fake DAOs. */
interface DatabaseTransactionRunner {
    /** Use suspend DAO methods inside [block]: they join the transaction. */
    suspend fun <R> inTransaction(block: suspend () -> R): R
}

class RoomTransactionRunner(private val database: SlowDownerDatabase) : DatabaseTransactionRunner {
    // The multiplatform form of withTransaction: DAO calls in the block reuse this connection.
    override suspend fun <R> inTransaction(block: suspend () -> R): R =
        database.useWriterConnection { connection -> connection.immediateTransaction { block() } }
}
