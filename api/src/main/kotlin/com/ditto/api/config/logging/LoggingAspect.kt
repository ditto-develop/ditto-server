package com.ditto.api.config.logging

import com.ditto.common.logging.Mask
import io.github.oshai.kotlinlogging.KotlinLogging
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.stereotype.Component
import org.springframework.util.StopWatch
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

@Aspect
@Component
class LoggingAspect {

    @Around("@annotation(com.ditto.common.logging.Loggable)")
    fun logEntryPoint(joinPoint: ProceedingJoinPoint): Any? {
        loggingActive.set(true)
        return try {
            log(joinPoint)
        } finally {
            loggingActive.remove()
        }
    }

    @Around(
        "execution(* com.ditto..*(..)) && !@annotation(com.ditto.common.logging.Loggable) && (" +
            "within(@org.springframework.stereotype.Component *) || " +
            "within(@org.springframework.stereotype.Service *) || " +
            "within(@org.springframework.stereotype.Repository *) || " +
            "within(@org.springframework.stereotype.Controller *) || " +
            "within(@org.springframework.web.bind.annotation.RestController *))",
    )
    fun logInternalCalls(joinPoint: ProceedingJoinPoint): Any? {
        return if (loggingActive.get()) log(joinPoint) else joinPoint.proceed()
    }

    private fun log(joinPoint: ProceedingJoinPoint): Any? {
        val signature = joinPoint.signature as MethodSignature
        val className = signature.declaringType.simpleName
        val methodName = signature.name
        val params = formatParams(signature.parameterNames, joinPoint.args)
        logger.info { "--> $className.$methodName($params)" }
        val stopWatch = StopWatch()
        stopWatch.start()
        return try {
            val result = joinPoint.proceed()
            stopWatch.stop()
            logger.info {
                "<-- $className.$methodName | ${stopWatch.totalTimeMillis}ms | return: ${
                    formatReturnValue(
                        result,
                    )
                }"
            }
            result
        } catch (e: Exception) {
            stopWatch.stop()
            logger.error(e) { "<-- $className.$methodName | ${stopWatch.totalTimeMillis}ms | exception: ${e.javaClass.simpleName}(${e.message})" }
            throw e
        }
    }

    private fun formatParams(paramNames: Array<String>, args: Array<Any?>): String {
        if (paramNames.isEmpty()) return ""
        return paramNames.zip(args).joinToString(", ") { (name, value) -> "$name=${serialize(value)}" }
    }

    private fun formatReturnValue(result: Any?): String = when (result) {
        null -> "null"
        is Unit -> "void"
        else -> serialize(result)
    }

    private fun serialize(value: Any?, maxLength: Int = 200): String {
        val str = mask(value)
        return if (str.length > maxLength) str.substring(0, maxLength) + "..." else str
    }

    private fun mask(value: Any?): String {
        if (value == null) return "null"
        val kClass = value::class
        val properties = kClass.memberProperties
        val hasMasked = properties.any { prop ->
            prop.annotations.any { it is Mask } ||
                prop.javaField?.isAnnotationPresent(Mask::class.java) == true
        }
        if (!hasMasked) return value.toString()
        val fields = properties.joinToString(", ") { prop ->
            val isMasked = prop.annotations.any { it is Mask } ||
                prop.javaField?.isAnnotationPresent(Mask::class.java) == true
            val fieldValue = if (isMasked) "**"
            else prop.getter.call(value)?.toString() ?: "null"
            "${prop.name}=$fieldValue"
        }
        return "${kClass.simpleName}($fields)"
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        private val loggingActive = ThreadLocal.withInitial { false }
    }
}
