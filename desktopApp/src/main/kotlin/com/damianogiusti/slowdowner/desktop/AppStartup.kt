package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.domain.effects.RestoreEffectsSettingsUseCase
import com.damianogiusti.slowdowner.domain.practice.AutoSavePracticeStateUseCase
import com.damianogiusti.slowdowner.domain.support.RecordAppLaunchUseCase
import com.damianogiusti.slowdowner.domain.update.CheckForUpdateUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.Koin

/**
 * The desktop twin of Android's SlowDownerApplication.onCreate: the saved effects go back into the
 * engine, and each track's speed, pitch and loop are saved as they change. Runs for the whole process.
 * It also counts the launch and looks for a new version, here and not in the UI: once per process,
 * whatever the window does.
 */
fun startAppWork(koin: Koin, scope: CoroutineScope) {
    scope.launch { koin.get<RestoreEffectsSettingsUseCase>()() }
    scope.launch { koin.get<AutoSavePracticeStateUseCase>()() }
    scope.launch { koin.get<RecordAppLaunchUseCase>()() }
    scope.launch { koin.get<CheckForUpdateUseCase>()() }
}
