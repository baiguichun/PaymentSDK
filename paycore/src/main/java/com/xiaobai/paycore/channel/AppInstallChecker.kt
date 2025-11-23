package com.xiaobai.paycore.channel

import android.content.Context
import android.content.pm.PackageManager

/**
 * APP安装检测工具类
 * 
 * 用于验证第三方支付APP是否已安装
 */
object AppInstallChecker {
    
    /**
     * 检查指定包名的APP是否已安装
     * 
     * @param context 上下文
     * @param packageName 包名
     * @return true表示已安装，false表示未安装
     */
    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            val packageManager = context.packageManager
            packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    /**
     * 批量检查多个APP的安装状态
     * 
     * @param context 上下文
     * @param packageNames 包名列表
     * @return Map<包名, 是否已安装>
     */
    fun checkMultipleApps(
        context: Context, 
        packageNames: List<String>
    ): Map<String, Boolean> {
        return packageNames.associateWith { packageName ->
            isPackageInstalled(context, packageName)
        }
    }
    
    /**
     * 获取已安装的APP列表
     * 
     * @param context 上下文
     * @param packageNames 要检查的包名列表
     * @return 已安装的包名列表
     */
    fun getInstalledApps(
        context: Context, 
        packageNames: List<String>
    ): List<String> {
        return packageNames.filter { isPackageInstalled(context, it) }
    }
    
    /**
     * 常用支付APP包名
     */
    object CommonPaymentApps {
        const val WECHAT = "com.tencent.mm"
        const val ALIPAY = "com.eg.android.AlipayGphone"
        const val UNION_PAY = "com.unionpay"
        const val QQ_WALLET = "com.tencent.mobileqq"
        const val JD_PAY = "com.jd.lib.pay"
    }
}

