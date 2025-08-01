package com.astralx.browser.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton
import com.astralx.browser.media.UniversalVideoPlayer
import com.astralx.browser.media.ThumbnailPreviewEngine
import com.astralx.browser.performance.MediaOptimizer
import com.astralx.browser.privacy.PrivacyShield
import com.astralx.browser.core.download.AdvancedDownloadEngine
import com.astralx.browser.core.privacy.EnhancedPrivacyManager
import android.content.Context

/**
 * Dagger module for media-related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object MediaModule {
    
    @Provides
    @Singleton
    fun provideMediaScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }
    
    @Provides
    @Singleton
    fun provideUniversalVideoPlayer(
        downloadManager: AdvancedDownloadEngine,
        scope: CoroutineScope
    ): UniversalVideoPlayer {
        return UniversalVideoPlayer(downloadManager, scope)
    }
    
    @Provides
    @Singleton
    fun provideThumbnailPreviewEngine(
        scope: CoroutineScope
    ): ThumbnailPreviewEngine {
        return ThumbnailPreviewEngine(scope)
    }
    
    @Provides
    @Singleton
    fun provideMediaOptimizer(
        scope: CoroutineScope
    ): MediaOptimizer {
        return MediaOptimizer(scope)
    }
    
    @Provides
    @Singleton
    fun providePrivacyShield(
        context: Context,
        privacyManager: EnhancedPrivacyManager,
        scope: CoroutineScope
    ): PrivacyShield {
        return PrivacyShield(context, privacyManager, scope)
    }
}