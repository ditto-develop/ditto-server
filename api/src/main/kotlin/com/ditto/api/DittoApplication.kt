package com.ditto.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.ditto"])
class DittoApplication

fun main(args: Array<String>) {
    runApplication<DittoApplication>(*args)
}
