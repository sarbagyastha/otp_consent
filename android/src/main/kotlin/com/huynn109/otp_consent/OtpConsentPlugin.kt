package com.huynn109.otp_consent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.NonNull
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.huynn109.otp_consent.SMSBroadcastReceiver.Companion.SMS_CONSENT_REQUEST
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.util.regex.Pattern

/** OtpConsentPlugin */
class OtpConsentPlugin : FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.ActivityResultListener, SMSBroadcastReceiver.Listener {
    private lateinit var methodChannel: MethodChannel
    private lateinit var mActivity: Activity
    private lateinit var mContext: Context
    private var isListening: Boolean = false
    private val smsBroadcastReceiver by lazy { SMSBroadcastReceiver() }

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel = MethodChannel(flutterPluginBinding.binaryMessenger, "otp_consent")
        methodChannel.setMethodCallHandler(this)
        mContext = flutterPluginBinding.applicationContext
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "getPlatformVersion" -> result.success("Android ${android.os.Build.VERSION.RELEASE}")
            "startListening" -> startListening(result, call.arguments?.toString())
            "stopListening" -> stopListening()
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
        unRegisterBroadcastListener()
    }

    override fun onDetachedFromActivity() {}

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {}

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        mActivity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {}

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        when (requestCode) {
            SMS_CONSENT_REQUEST -> {
                when {
                    resultCode == Activity.RESULT_OK && data != null -> {
                        val message = data.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)
                        methodChannel.invokeMethod("onSmsConsentReceived", message)
                    }
                    else -> {
                        // Consent denied. User can type OTC manually.
                        methodChannel.invokeMethod("onSmsConsentPermissionDenied", null)
                    }
                }
                unRegisterBroadcastListener()
            }
        }
        return true
    }

    override fun onShowPermissionDialog() {
        methodChannel.invokeMethod("onShowPermissionDialog", null)
    }

    override fun onTimeout() {
        methodChannel.invokeMethod("onTimeout", null)
        unRegisterBroadcastListener()
    }

    private fun startListening(result: Result, phone: String?) {
        synchronized(this) {
            startBroadcastReceiver(result, phone)
        }
    }

    private fun startBroadcastReceiver(result: Result, phone: String?) {
        SmsRetriever.getClient(mActivity).startSmsUserConsent(phone)
        smsBroadcastReceiver.injectListener(mActivity, this)
        mActivity.registerReceiver(smsBroadcastReceiver, IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION))
        result.success(true)
    }

    private fun stopListening() {
        methodChannel.invokeMethod("onStopListener", null)
        unRegisterBroadcastListener()
    }

    private fun unRegisterBroadcastListener() {
        try {
            mActivity.unregisterReceiver(smsBroadcastReceiver)
            isListening = false
        } catch (ex: Exception) {
        }
    }
}
