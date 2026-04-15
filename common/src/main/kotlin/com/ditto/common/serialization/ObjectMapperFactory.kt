package com.ditto.common.serialization

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object DateTimeFormats {
    const val DATE = "yyyy-MM-dd"
    const val TIME = "HH:mm:ss"
    const val DATE_TIME = "yyyy-MM-dd HH:mm:ss"
    val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern(DATE)
    val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern(TIME)
    val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern(DATE_TIME)
}

object ObjectMapperFactory {
    fun create(): ObjectMapper {
        val javaTimeModule = JavaTimeModule().apply {
            addSerializer(LocalDate::class.java, LocalDateSerializer(DateTimeFormats.DATE_FORMATTER))
            addDeserializer(LocalDate::class.java, LocalDateDeserializer(DateTimeFormats.DATE_FORMATTER))
            addSerializer(LocalTime::class.java, LocalTimeSerializer(DateTimeFormats.TIME_FORMATTER))
            addDeserializer(LocalTime::class.java, LocalTimeDeserializer(DateTimeFormats.TIME_FORMATTER))
            addSerializer(LocalDateTime::class.java, LocalDateTimeSerializer(DateTimeFormats.DATE_TIME_FORMATTER))
            addDeserializer(LocalDateTime::class.java, LocalDateTimeDeserializer(DateTimeFormats.DATE_TIME_FORMATTER))
        }
        return jacksonObjectMapper().apply {
            registerModule(javaTimeModule)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
        }
    }
}
