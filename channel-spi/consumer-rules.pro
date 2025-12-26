-keep class ** implements com.xiaobai.paycore.channel.IPaymentChannel
-keep @com.xiaobai.paycore.channel.PaymentChannelService class * { *; }
-keepclassmembers class * {
    @com.xiaobai.paycore.channel.PaymentChannelService *;
}
-keepattributes *Annotation*
