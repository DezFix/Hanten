package hanten.wre.app.core.ui

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import hanten.wre.app.core.exceptions.resolve.ExceptionResolver
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.prefs.ImportedThemeStore
import hanten.wre.app.core.ui.util.ActivityRecreationHandle

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BaseActivityEntryPoint {

	val settings: AppSettings

	val importedThemeStore: ImportedThemeStore

	val exceptionResolverFactory: ExceptionResolver.Factory

	val activityRecreationHandle: ActivityRecreationHandle
}
