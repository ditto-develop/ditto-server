---
name: feedback_read_convention_first
description: 작업 전 PROJECT_CONVENTION.md 필독 및 JPA 연관관계 매핑 규칙
type: feedback
---

작업 전에 반드시 PROJECT_CONVENTION.md를 먼저 읽고 시작할 것.

JPA 엔티티 간 연관관계는 @ManyToOne, @OneToMany 등 객체 참조 대신 ID 매핑 방식(memberId: Long)을 사용할 것.
**Why:** 사용자가 명시적으로 ID 매핑 방식을 선호함.
**How to apply:** 새 엔티티 작성 시 다른 엔티티를 참조할 때 항상 `@Column`으로 ID 필드를 직접 선언.
