package com.openjarvis.android.di

import com.openjarvis.android.tools.AndroidTool
import com.openjarvis.android.tools.platform.DeviceInfoTool
import com.openjarvis.android.tools.platform.DialOrCallTool
import com.openjarvis.android.tools.platform.FileReadTool
import com.openjarvis.android.tools.platform.FileWriteTool
import com.openjarvis.android.tools.platform.FlashlightTool
import com.openjarvis.android.tools.platform.NotificationTool
import com.openjarvis.android.tools.platform.OpenAppTool
import com.openjarvis.android.tools.platform.SaveMemoryTool
import com.openjarvis.android.tools.platform.SearchMemoryTool
import com.openjarvis.android.tools.platform.SendMessageIntentTool
import com.openjarvis.android.tools.platform.SetAlarmTool
import com.openjarvis.android.tools.platform.SetTimerTool
import com.openjarvis.android.tools.platform.SettingsShortcutTool
import com.openjarvis.android.tools.platform.VolumeControlTool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class ToolsModule {

    @Binds
    @IntoSet
    abstract fun bindOpenAppTool(tool: OpenAppTool): AndroidTool

    @Binds
    @IntoSet
    abstract fun bindDialOrCallTool(tool: DialOrCallTool): AndroidTool

    @Binds
    @IntoSet
    abstract fun bindSendMessageTool(tool: SendMessageIntentTool): AndroidTool

    @Binds
    @IntoSet
    abstract fun bindFlashlightTool(tool: FlashlightTool): AndroidTool

    @Binds
    @IntoSet
    abstract fun bindVolumeControlTool(tool: VolumeControlTool): AndroidTool

    @Binds
    @IntoSet
    abstract fun bindSetAlarmTool(tool: SetAlarmTool): AndroidTool

    @Binds
    @IntoSet
    abstract fun bindSetTimerTool(tool: SetTimerTool): AndroidTool

    @Binds
    @IntoSet
    abstract fun bindDeviceInfoTool(tool: DeviceInfoTool): AndroidTool

    @Binds
    @IntoSet
    abstract fun bindNotificationTool(tool: NotificationTool): AndroidTool

    @Binds
    @IntoSet
    abstract fun bindSettingsShortcutTool(tool: SettingsShortcutTool): AndroidTool

    @Binds
    @IntoSet
    abstract fun bindFileReadTool(tool: FileReadTool): AndroidTool

    @Binds
    @IntoSet
    abstract fun bindFileWriteTool(tool: FileWriteTool): AndroidTool

    @Binds
    @IntoSet
    abstract fun bindSearchMemoryTool(tool: SearchMemoryTool): AndroidTool

    @Binds
    @IntoSet
    abstract fun bindSaveMemoryTool(tool: SaveMemoryTool): AndroidTool
}
