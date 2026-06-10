package com.ditto.domain.member.entity

enum class GenderPreference {
    OPPOSITE,
    SAME,
    ANY,
    ;

    fun targetGenders(ownerGender: Gender): Set<Gender> = when (this) {
        ANY -> Gender.entries.toSet()
        OPPOSITE -> setOf(ownerGender.opposite())
        SAME -> setOf(ownerGender)
    }
}
