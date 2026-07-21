package com.pianokids.di

import android.content.Context
import androidx.room.Room
import com.pianokids.audio.AudioCapture
import com.pianokids.audio.NoteRecognizer
import com.pianokids.audio.PitchDetector
import com.pianokids.data.db.AchievementDao
import com.pianokids.data.db.PianoDatabase
import com.pianokids.data.db.PracticeDao
import com.pianokids.data.db.ProgressDao
import com.pianokids.data.prefs.UserPreferences
import com.pianokids.data.repo.ProgressRepository
import com.pianokids.music.DtwMatcher
import com.pianokids.music.MidiParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 应用全局 Hilt 模块：提供无法用 `@Inject constructor` 自动构造的依赖。
 *
 * 以下类已用 `@Singleton` + `@Inject constructor` 标注，Hilt 可自动构造，
 * 无需在此声明：
 * - [AudioCapture] / [PitchDetector] / [NoteRecognizer]
 * - [UserPreferences] / [ProgressRepository]
 *
 * 本模块只提供：
 * - [PianoDatabase]（需 Room builder）+ 三个 DAO
 * - [MidiParser] / [DtwMatcher]（无状态工具类，单例化避免重复创建）
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ============== 数据库 ==============

    /**
     * 提供 [PianoDatabase] 单例。
     */
    @Provides
    @Singleton
    fun providePianoDatabase(@ApplicationContext context: Context): PianoDatabase =
        Room.databaseBuilder(
            context,
            PianoDatabase::class.java,
            PianoDatabase.DB_NAME,
        )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideProgressDao(db: PianoDatabase): ProgressDao = db.progressDao()

    @Provides
    fun provideAchievementDao(db: PianoDatabase): AchievementDao = db.achievementDao()

    @Provides
    fun providePracticeDao(db: PianoDatabase): PracticeDao = db.practiceDao()

    // ============== 音乐工具 ==============

    /**
     * [MidiParser] 是无状态解析器，提供单例避免重复创建。
     */
    @Provides
    @Singleton
    fun provideMidiParser(): MidiParser = MidiParser()

    /**
     * [DtwMatcher] 是无状态算法工具，提供单例。
     */
    @Provides
    @Singleton
    fun provideDtwMatcher(): DtwMatcher = DtwMatcher()
}
