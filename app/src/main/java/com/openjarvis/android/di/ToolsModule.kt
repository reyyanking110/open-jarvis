package com.openjarvis.android.di

import com.openjarvis.android.tools.AndroidTool
import com.openjarvis.android.tools.native.DeviceInfoTool
import com.openjarvis.android.tools.native.DialOrCallTool
import com.openjarvis.android.tools.native.FileReadTool
import com.openjarvis.android.tools.native.FileWriteTool
import com.openjarvis.android.tools.native.FlashlightTool
import com.openjarvis.android.tools.native.NotificationTool
import com.openjarvis.android.tools.native.OpenAppTool
import com.openjarvis.android.tools.native.SaveMemoryTool
import com.openjarvis.android.tools.native.SearchMemoryTool
import com.openjarvis.android.tools.native.SendMessageIntentTool
import com.openjarvis.android.tools.native.SetAlarmTool
import com.openjarvis.android.tools.native.SetTimerTool
import com.openjarvis.android.tools.native.SettingsShortcutTool
import com.openjarvis.android.tools.native.VolumeControlTool
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
