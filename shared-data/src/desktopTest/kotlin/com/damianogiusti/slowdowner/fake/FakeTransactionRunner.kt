package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.data.database.DatabaseTransactionRunner

/** Runs the block directly. No rollback: tests check files and results, not undo. */
class FakeTransactionRunner : DatabaseTransactionRunner {
    var transactionCount = 0
        private set

    override suspend fun <R> inTransaction(block: suspend () -> R): R {
        transactionCount++
        return block()
    }
}
