package com.openjarvis.android.di

import com.openjarvis.android.tools.AndroidTool
import com.openjarvis.android.tools.device.DeviceInfoTool
import com.openjarvis.android.tools.device.DialOrCallTool
import com.openjarvis.android.tools.device.FileReadTool
import com.openjarvis.android.tools.device.FileWriteTool
import com.openjarvis.android.tools.device.FlashlightTool
import com.openjarvis.android.tools.device.NotificationTool
import com.openjarvis.android.tools.device.OpenAppTool
import com.openjarvis.android.tools.device.SaveMemoryTool
import com.openjarvis.android.tools.device.SearchMemoryTool
import com.openjarvis.android.tools.device.SendMessageIntentTool
import com.openjarvis.android.tools.device.SetAlarmTool
import com.openjarvis.android.tools.device.SetTimerTool
import com.openjarvis.android.tools.device.SettingsShortcutTool
import com.openjarvis.android.tools.device.VolumeControlTool
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
