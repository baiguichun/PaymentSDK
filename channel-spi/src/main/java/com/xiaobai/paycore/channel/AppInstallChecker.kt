package com.xiaobai.paycore.channel

import android.content.Context
import android.content.pm.PackageManager

/**
 * APP安装检测工具类
 */
object AppInstallChecker {
    
    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            val packageManager = context.packageManager
            packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    fun checkMultipleApps(
        context: Context,
        packageNames: List<String>
    ): Map<String, Boolean> {
        return packageNames.associateWith { packageName ->
            isPackageInstalled(context, packageName)
        }
    }
    
    fun getInstalledApps(
        context: Context,
        packageNames: List<String>
    ): List<String> {
        return packageNames.filter { isPackageInstalled(context, it) }
    }
    
    object CommonPaymentApps {
        const val WECHAT = "com.tencent.mm"
        const val ALIPAY = "com.eg.android.AlipayGphone"
        const val UNION_PAY = "com.unionpay"
        const val QQ_WALLET = "com.tencent.mobileqq"
        const val JD_PAY = "com.jd.lib.pay"
    }
}
