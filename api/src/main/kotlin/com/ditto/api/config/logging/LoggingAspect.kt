package com.ditto.api.config.logging

import io.github.oshai.kotlinlogging.KotlinLogging
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.stereotype.Component
import org.springframework.util.StopWatch

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

    @Around("execution(* com.ditto..*(..)) && !@annotation(com.ditto.common.logging.Loggable)")
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
            logger.info { "<-- $className.$methodName | ${stopWatch.totalTimeMillis}ms | return: ${formatReturnValue(result)}" }
            result
        } catch (e: Exception) {
            stopWatch.stop()
            logger.error(e) { "<-- $className.$methodName | ${stopWatch.totalTimeMillis}ms | exception: ${e.javaClass.simpleName}(${e.message})" }
            throw e
        }
    }

    private fun formatParams(paramNames: Array<String>, args: Array<Any?>): String {
        if (paramNames.isEmpty()) return ""
        return paramNames.zip(args).joinToString(", ") { (name, value) -> "$name=${truncate(value)}" }
    }

    private fun formatReturnValue(result: Any?): String = when (result) {
        null -> "null"
        is Unit -> "void"
        else -> truncate(result)
    }

    private fun truncate(value: Any?, maxLength: Int = 200): String {
        val str = value.toString()
        return if (str.length > maxLength) str.substring(0, maxLength) + "..." else str
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        private val loggingActive = ThreadLocal.withInitial { false }
    }
}
