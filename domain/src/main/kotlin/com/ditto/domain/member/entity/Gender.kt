package com.ditto.domain.member.entity

enum class Gender(private val description: String) {
    MALE("남성"),
    FEMALE("여성"),
    ;

    fun opposite(): Gender = when (this) {
        MALE -> FEMALE
        FEMALE -> MALE
    }
}
