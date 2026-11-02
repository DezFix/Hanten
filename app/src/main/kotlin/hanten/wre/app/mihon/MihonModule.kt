package hanten.wre.app.mihon

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import hanten.wre.app.core.network.MangaHttpClient
import hanten.wre.app.core.network.webview.WebViewExecutor
import hanten.wre.app.mihon.compat.MihonInjektBridge
import hanten.wre.app.mihon.extensions.repo.ExternalExtensionRepoRepository
import hanten.wre.app.mihon.extensions.repo.InstalledExtensionSignatureValidator
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MihonModule {
    @Provides
    @Singleton
    fun provideMihonInjektBridge(
        @ApplicationContext context: Context,
        @MangaHttpClient okHttpClient: OkHttpClient,
        cookieJar: CookieJar,
        webViewExecutor: WebViewExecutor,
    ): MihonInjektBridge = MihonInjektBridge(
        context = context,
        httpClient = okHttpClient,
        cookieJar = cookieJar,
        webViewExecutor = webViewExecutor,
    )

    @Provides
    @Singleton
    fun provideMihonExtensionLoader(
        @ApplicationContext context: Context,
        injektBridge: dagger.Lazy<MihonInjektBridge>,
        repoRepositoryProvider: Provider<ExternalExtensionRepoRepository>,
        signatureValidator: InstalledExtensionSignatureValidator,
    ): MihonExtensionLoader {
        return MihonExtensionLoader(context, injektBridge, repoRepositoryProvider, signatureValidator)
    }

    @Provides
    @Singleton
    fun provideMihonExtensionManager(
        @ApplicationContext context: Context,
        loader: MihonExtensionLoader,
    ): MihonExtensionManager {
        return MihonExtensionManager(context, loader)
    }
}
