package com.xiaobai.paycore.di

import com.xiaobai.paycore.config.PaymentConfig
import com.xiaobai.paycore.channel.PaymentChannelManager
import com.xiaobai.paycore.data.PaymentErrorMapper
import com.xiaobai.paycore.data.PaymentRepositoryImpl
import com.xiaobai.paycore.domain.PaymentRepository
import com.xiaobai.paycore.domain.usecase.CreateOrderUseCase
import com.xiaobai.paycore.domain.usecase.FetchChannelsUseCase
import com.xiaobai.paycore.domain.usecase.PaymentUseCases
import com.xiaobai.paycore.domain.usecase.QueryStatusUseCase
import com.xiaobai.paycore.network.PaymentApiService
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin 模块定义。
 */
fun paymentModule(config: PaymentConfig): Module = module {
    single { config }
    single { PaymentErrorMapper() }
    single { PaymentChannelManager() }
    single {
        PaymentApiService(
            baseUrl = config.apiBaseUrl,
            timeoutMs = config.networkTimeout * 1000,
            securityConfig = config.securityConfig
        )
    }
    single<PaymentRepository> { PaymentRepositoryImpl(get(), get()) }
    single {
        PaymentUseCases(
            fetchChannels = FetchChannelsUseCase(get()),
            createOrder = CreateOrderUseCase(get()),
            queryStatus = QueryStatusUseCase(get())
        )
    }
}
