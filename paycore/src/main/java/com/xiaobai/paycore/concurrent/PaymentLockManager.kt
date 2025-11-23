package com.xiaobai.paycore.concurrent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 支付锁管理器
 * 
 * 功能：
 * 1. 防止同一订单重复支付
 * 2. 管理订单级别的锁
 * 3. 线程安全的订单状态管理
 * 4. 订单锁超时自动释放（防止死锁）
 */
object PaymentLockManager {
    
    // 正在支付中的订单集合
    private val payingOrders = ConcurrentHashMap.newKeySet<String>()
    
    // 存储每个订单的超时任务
    private val timeoutJobs = ConcurrentHashMap<String, Job>()
    
    // 全局锁管理器的锁
    private val managerLock = ReentrantLock()
    
    // 协程作用域，用于管理超时任务
    private val timeoutScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // 超时回调（可选，用于日志记录）
    private var onTimeoutCallback: ((orderId: String) -> Unit)? = null
    
    /**
     * 检查订单是否正在支付中
     * 
     * @param orderId 订单ID
     * @return true表示正在支付，false表示未在支付
     */
    fun isOrderPaying(orderId: String): Boolean {
        return payingOrders.contains(orderId)
    }
    
    /**
     * 尝试获取订单的支付锁
     * 
     * @param orderId 订单ID
     * @param timeoutMs 超时时间（毫秒），超过此时间自动释放锁，默认5分钟
     * @return true表示获取成功，false表示订单正在支付中
     */
    fun tryLockOrder(orderId: String, timeoutMs: Long = 300000): Boolean {
        managerLock.withLock {
            // 检查订单是否已在支付中
            if (payingOrders.contains(orderId)) {
                return false
            }
            
            // 标记订单为支付中
            payingOrders.add(orderId)
            
            // 启动超时任务
            startTimeoutTask(orderId, timeoutMs)
            
            return true
        }
    }
    
    /**
     * 释放订单的支付锁
     * 
     * @param orderId 订单ID
     */
    fun unlockOrder(orderId: String) {
        managerLock.withLock {
            payingOrders.remove(orderId)
            // 取消超时任务
            timeoutJobs.remove(orderId)?.cancel()
        }
    }
    
    /**
     * 启动超时任务
     * 
     * @param orderId 订单ID
     * @param timeoutMs 超时时间（毫秒）
     */
    private fun startTimeoutTask(orderId: String, timeoutMs: Long) {
        // 取消之前的超时任务（如果存在）
        timeoutJobs.remove(orderId)?.cancel()
        
        // 创建新的超时任务
        val timeoutJob = timeoutScope.launch {
            try {
                delay(timeoutMs)
                
                // 超时后自动释放锁
                managerLock.withLock {
                    if (payingOrders.contains(orderId)) {
                        payingOrders.remove(orderId)
                        timeoutJobs.remove(orderId)
                        
                        // 触发超时回调
                        onTimeoutCallback?.invoke(orderId)
                    }
                }
            } catch (e: Exception) {
                // 任务被取消或其他异常，忽略
            }
        }
        
        timeoutJobs[orderId] = timeoutJob
    }
    
    /**
     * 设置超时回调（用于日志记录或监控）
     * 
     * @param callback 超时回调，参数为订单ID
     */
    fun setOnTimeoutCallback(callback: ((orderId: String) -> Unit)?) {
        onTimeoutCallback = callback
    }
    
    /**
     * 清理所有锁（测试用）
     */
    fun clearAll() {
        managerLock.withLock {
            payingOrders.clear()
            // 取消所有超时任务
            timeoutJobs.values.forEach { it.cancel() }
            timeoutJobs.clear()
        }
    }
    
    /**
     * 关闭锁管理器，清理所有资源（仅在SDK关闭时调用）
     */
    fun shutdown() {
        managerLock.withLock {
            payingOrders.clear()
            timeoutJobs.values.forEach { it.cancel() }
            timeoutJobs.clear()
            timeoutScope.cancel()
        }
    }
    
    /**
     * 获取当前正在支付的订单数量
     */
    fun getPayingOrderCount(): Int {
        return payingOrders.size
    }
    
    /**
     * 获取当前正在支付的订单列表（调试用）
     */
    fun getPayingOrders(): Set<String> {
        return payingOrders.toSet()
    }
}

/**
 * 订单正在支付中异常
 */
class OrderAlreadyPayingException(message: String) : Exception(message)

