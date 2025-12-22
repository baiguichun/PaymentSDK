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
 * 防止重复支付，带超时自动释放。
 */
object PaymentLockManager {
    
    private val payingOrders = ConcurrentHashMap.newKeySet<String>()
    private val timeoutJobs = ConcurrentHashMap<String, Job>()
    private val managerLock = ReentrantLock()
    private val timeoutScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var onTimeoutCallback: ((orderId: String) -> Unit)? = null
    
    fun isOrderPaying(orderId: String): Boolean = payingOrders.contains(orderId)
    
    fun tryLockOrder(orderId: String, timeoutMs: Long = 300000): Boolean {
        managerLock.withLock {
            if (payingOrders.contains(orderId)) return false
            payingOrders.add(orderId)
            startTimeoutTask(orderId, timeoutMs)
            return true
        }
    }
    
    fun unlockOrder(orderId: String) {
        managerLock.withLock {
            payingOrders.remove(orderId)
            timeoutJobs.remove(orderId)?.cancel()
        }
    }
    
    private fun startTimeoutTask(orderId: String, timeoutMs: Long) {
        timeoutJobs.remove(orderId)?.cancel()
        val timeoutJob = timeoutScope.launch {
            try {
                delay(timeoutMs)
                managerLock.withLock {
                    if (payingOrders.contains(orderId)) {
                        payingOrders.remove(orderId)
                        timeoutJobs.remove(orderId)
                        onTimeoutCallback?.invoke(orderId)
                    }
                }
            } catch (_: Exception) {
                // ignore
            }
        }
        timeoutJobs[orderId] = timeoutJob
    }
    
    fun setOnTimeoutCallback(callback: ((orderId: String) -> Unit)?) {
        onTimeoutCallback = callback
    }
    
    fun clearAll() {
        managerLock.withLock {
            payingOrders.clear()
            timeoutJobs.values.forEach { it.cancel() }
            timeoutJobs.clear()
        }
    }
    
    fun shutdown() {
        managerLock.withLock {
            payingOrders.clear()
            timeoutJobs.values.forEach { it.cancel() }
            timeoutJobs.clear()
            timeoutScope.cancel()
        }
    }
    
    fun getPayingOrderCount(): Int = payingOrders.size
    fun getPayingOrders(): Set<String> = payingOrders.toSet()
}

class OrderAlreadyPayingException(message: String) : Exception(message)
