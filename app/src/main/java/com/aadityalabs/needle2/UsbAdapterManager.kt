package com.aadityalabs.needle2

import android.content.Context
import android.hardware.usb.UsbManager

data class UsbWifiAdapter(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val interfaces: Int,
    val note: String
)

class UsbAdapterManager(context: Context) {
    private val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    fun inventory(): List<UsbWifiAdapter> =
        manager.deviceList.values.map { device ->
            UsbWifiAdapter(
                deviceName = device.deviceName,
                vendorId = device.vendorId,
                productId = device.productId,
                interfaces = device.interfaceCount,
                note = "USB device detected; monitor/injection support depends on Android kernel/driver/root environment."
            )
        }

    fun isUsbHostAvailable(context: Context): Boolean =
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_USB_HOST)
}
