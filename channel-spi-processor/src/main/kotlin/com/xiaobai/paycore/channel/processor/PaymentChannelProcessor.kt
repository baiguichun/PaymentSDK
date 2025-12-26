package com.xiaobai.paycore.channel.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.google.devtools.ksp.getAllSuperTypes
import java.io.OutputStream
import java.nio.charset.StandardCharsets

private const val ANNOTATION = "com.xiaobai.paycore.channel.PaymentChannelService"
private const val PAYMENT_CHANNEL_INTERFACE = "com.xiaobai.paycore.channel.IPaymentChannel"
private const val SERVICE_FILE = "META-INF/services/$PAYMENT_CHANNEL_INTERFACE"
private const val CHANNEL_MAPPING_FILE = "META-INF/paycore/payment-channels.properties"

class PaymentChannelProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()

        val annotated = resolver
            .getSymbolsWithAnnotation(ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()

        if (!annotated.iterator().hasNext()) {
            return emptyList()
        }

        val deferred = mutableListOf<KSAnnotated>()
        val entries = mutableListOf<ChannelEntry>()

        annotated.forEach { declaration ->
            if (!declaration.validate()) {
                deferred += declaration
                return@forEach
            }

            if (!declaration.isValidChannel()) {
                logger.error(
                    "@PaymentChannelService 只能标记实现 IPaymentChannel 的具体类",
                    declaration
                )
                return@forEach
            }

            val channelId = declaration.channelId()
            if (channelId.isNullOrBlank()) {
                logger.error("channelId 不能为空", declaration)
                return@forEach
            }

            val qualifiedName = declaration.qualifiedName?.asString()
            if (qualifiedName == null) {
                logger.error("无法获取类名: $declaration", declaration)
                return@forEach
            }

            entries += ChannelEntry(channelId, qualifiedName, declaration.containingFile)
        }

        if (entries.isEmpty()) {
            return deferred
        }

        writeServiceFile(entries)
        writeChannelMapping(entries)

        generated = true
        return deferred
    }

    private fun writeServiceFile(entries: List<ChannelEntry>) {
        val dependencies = entries.dependencies()
        codeGenerator.createNewFile(
            dependencies = dependencies,
            packageName = "",
            fileName = SERVICE_FILE
        ).use { output ->
            writeLines(output, entries.map { it.className })
        }
    }

    private fun writeChannelMapping(entries: List<ChannelEntry>) {
        val dependencies = entries.dependencies()
        val deduped = entries
            .groupBy { it.channelId }
            .map { (channelId, group) ->
                if (group.size > 1) {
                    logger.warn("检测到重复的 channelId: $channelId，仅使用第一个声明: ${group.first().className}")
                }
                channelId to group.first().className
            }

        codeGenerator.createNewFile(
            dependencies = dependencies,
            packageName = "",
            fileName = CHANNEL_MAPPING_FILE
        ).use { output ->
            writeLines(output, deduped.map { (channelId, className) -> "$channelId=$className" })
        }
    }

    private fun writeLines(output: OutputStream, lines: List<String>) {
        lines.joinToString(separator = "\n", postfix = "\n")
            .toByteArray(StandardCharsets.UTF_8)
            .let(output::write)
    }

    private fun List<ChannelEntry>.dependencies(): Dependencies {
        val sourceFiles = mapNotNull(ChannelEntry::source).toTypedArray()
        return if (sourceFiles.isEmpty()) {
            Dependencies(false)
        } else {
            Dependencies(false, *sourceFiles)
        }
    }

    private fun KSClassDeclaration.channelId(): String? {
        val annotation = annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION
        } ?: return null
        return annotation.arguments
            .firstOrNull { it.name?.asString() == "channelId" }
            ?.value
            ?.toString()
    }

    private fun KSClassDeclaration.isValidChannel(): Boolean {
        if (classKind != ClassKind.CLASS) return false
        if (modifiers.contains(Modifier.ABSTRACT)) return false

        return getAllSuperTypes().any {
            it.declaration.qualifiedName?.asString() == PAYMENT_CHANNEL_INTERFACE
        }
    }

    private data class ChannelEntry(
        val channelId: String,
        val className: String,
        val source: KSFile?
    )
}
