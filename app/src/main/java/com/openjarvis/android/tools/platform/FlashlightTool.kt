package com.openjarvis.android.tools.platform

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import com.openjarvis.android.tools.AndroidTool
import com.openjarvis.android.tools.ToolExecutionResult
import com.openjarvis.android.tools.ToolParamSchema
import com.openjarvis.android.tools.ToolRiskLevel
import com.openjarvis.android.tools.ToolSpec
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

class FlashlightTool @Inject constructor() : AndroidTool {
    override val spec = ToolSpec(
        name = "set_flashlight",
        description = "Turn the device camera flashlight torch ON or OFF.",
        riskLevel = ToolRiskLevel.DEVICE_STATE,
        parameters = listOf(
            ToolParamSchema(
                name = "enabled",
                type = "boolean",
                description = "True to turn flashlight ON, false to turn OFF.",
                required = true,
            ),
        ),
    )

    @Volatile
    private var lastObservedTorchState: Boolean? = null

    override suspend fun execute(context: Context, args: JsonObject): ToolExecutionResult {
        val enabled = args.bool("enabled")
            ?: return ToolExecutionResult(spec.name, false, "Missing boolean 'enabled' parameter.")

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return ToolExecutionResult(
            toolName = spec.name,
            success = false,
            summary = "No camera flash unit available on this device.",
            recoverable = false,
        )

        val observed = CompletableDeferred<Boolean>()
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, mode: Boolean) {
                if (id == cameraId) {
                    lastObservedTorchState = mode
                    if (mode == enabled) observed.complete(mode)
                }
            }
        }
        val handler = Handler(Looper.getMainLooper())
        cameraManager.registerTorchCallback(callback, handler)
        return try {
            cameraManager.setTorchMode(cameraId, enabled)
            val confirmedState = withTimeoutOrNull(1_200L) { observed.await() }
            val verified = confirmedState == enabled
            ToolExecutionResult(
                toolName = spec.name,
                success = true,
                summary = "Flashlight turned ${if (enabled) "ON" else "OFF"}.",
                verified = verified,
            )
        } finally {
            cameraManager.unregisterTorchCallback(callback)
        }
    }

    override suspend fun verify(
        context: Context,
        args: JsonObject,
        initialResult: ToolExecutionResult,
    ): Boolean {
        val target = args.bool("enabled") ?: return initialResult.verified
        return initialResult.verified || lastObservedTorchState == target
    }
}
