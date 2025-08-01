package com.astralx.browser.di

import android.content.Context
import com.astralx.browser.media.UniversalVideoPlayer
import com.astralx.browser.media.ThumbnailPreviewEngine
import com.astralx.browser.performance.MediaOptimizer
import com.astralx.browser.privacy.PrivacyShield
import com.astralx.browser.core.download.AdvancedDownloadEngine
import com.astralx.browser.core.privacy.EnhancedPrivacyManager
import com.astralx.browser.video.AdultContentVideoDetector
import com.astralx.browser.video.VideoThumbnailPreviewEngine
import com.astralx.browser.video.AdultContentVideoCodecs
import com.astralx.browser.video.VideoDownloadManager
import com.astralx.browser.video.VideoCastManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

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
        @ApplicationContext context: Context,
        privacyManager: EnhancedPrivacyManager,
        scope: CoroutineScope
    ): PrivacyShield {
        return PrivacyShield(context, privacyManager, scope)
    }
    
    @Provides
    @Singleton
    fun provideAdvancedDownloadEngine(
        @ApplicationContext context: Context
    ): AdvancedDownloadEngine {
        return AdvancedDownloadEngine(context)
    }
    
    @Provides
    @Singleton
    fun provideAdultContentVideoDetector(): AdultContentVideoDetector {
        return AdultContentVideoDetector()
    }
    
    @Provides
    @Singleton
    fun provideVideoThumbnailPreviewEngine(
        @ApplicationContext context: Context
    ): VideoThumbnailPreviewEngine {
        return VideoThumbnailPreviewEngine(context)
    }
    
    @Provides
    @Singleton
    fun provideAdultContentVideoCodecs(
        @ApplicationContext context: Context
    ): AdultContentVideoCodecs {
        return AdultContentVideoCodecs(context)
    }
    
    @Provides
    @Singleton
    fun provideVideoDownloadManager(
        @ApplicationContext context: Context,
        downloadEngine: AdvancedDownloadEngine
    ): VideoDownloadManager {
        return VideoDownloadManager(context, downloadEngine)
    }
    
    @Provides
    @Singleton
    fun provideVideoCastManager(
        @ApplicationContext context: Context
    ): VideoCastManager {
        return VideoCastManager(context)
    }
}