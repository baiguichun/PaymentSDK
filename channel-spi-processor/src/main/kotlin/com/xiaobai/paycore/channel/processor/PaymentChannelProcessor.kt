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
import java.nio.charset.StandardCharsets

private const val ANNOTATION = "com.xiaobai.paycore.channel.PaymentChannelService"
private const val PAYMENT_CHANNEL_INTERFACE = "com.xiaobai.paycore.channel.IPaymentChannel"
private const val GENERATED_PACKAGE = "com.xiaobai.paycore.channel.generated"

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

        writeRegistry(entries)

        generated = true
        return deferred
    }

    // 生成静态注册表，写入 channelId + 工厂闭包，供运行时懒加载渠道实例
    private fun writeRegistry(entries: List<ChannelEntry>) {
        val dependencies = entries.dependencies()
        val objectName = "GeneratedPaymentChannelRegistry"

        codeGenerator.createNewFile(
            dependencies = dependencies,
            packageName = GENERATED_PACKAGE,
            fileName = objectName,
            extensionName = "kt"
        ).use { output ->
            val content = buildString {
                appendLine("package $GENERATED_PACKAGE")
                appendLine()
                appendLine("import com.xiaobai.paycore.channel.PaymentChannelFactory")
                appendLine()
                appendLine("object $objectName {")
                appendLine("    @JvmField")
                appendLine("    val factories: List<PaymentChannelFactory> = listOf(")
                entries.forEachIndexed { index, entry ->
                    val suffix = if (index == entries.lastIndex) "" else ","
                    appendLine("        PaymentChannelFactory(\"${entry.channelId}\") { ${entry.className}() }$suffix")
                }
                appendLine("    )")
                appendLine("}")
            }
            output.write(content.toByteArray(StandardCharsets.UTF_8))
        }
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
