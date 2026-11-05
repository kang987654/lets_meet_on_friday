package com.kosmos.app.domain.audit

import com.kosmos.app.domain.model.AuditEvent
import java.security.MessageDigest

/**
 * [AuditHashChain]
 * 활동 기록의 해시 체인 — 행마다 앞 행의 지문을 넣어 SHA-256 지문을 만들고, 이어 붙인 사슬을 검증합니다 (0.41.4).
 *
 * ### Architecture Context
 * - **Layer**: Domain (Audit) — 순수 JVM(`MessageDigest`, 의존성 0). 저장소가 저장할 때 [entryHash] 로 지문을 만들고, 화면이 [Verifier] 로 검증한다.
 *
 * ### Key Flow
 * 1. 저장: 번호 = 머리 번호 + 1, 앞 지문 = 머리 지문(첫 행이면 [GENESIS]) → [entryHash].
 * 2. 검증: 번호 오름차순으로 [Verifier.accept] 에 쪽 단위로 넣고 [Verifier.result] — 체인 전 행(번호 0)은 세기만 한다.
 *
 * [WHY] **변경 감지(tamper-evident)이지 무결성 증명이 아니다** — 키가 없어 DB 를 고칠 수 있는 사람이 지문을 처음부터 다시 계산하면 감지하지 못한다.
 * 개인용 앱의 실제 위협(파일 손상·버그로 인한 누락·순서 꼬임)과 체인을 모르는 편집을 잡는다. 재계산은 범위 밖(ADR-039, 0.41.4 Q1 사용자 결정) —
 * 화면의 "지금 체인 머리"를 따로 적어 두면 재계산도 드러난다. 키가 없어 다른 기기에 복원한 백업도 그대로 검증된다.
 */
object AuditHashChain {
    /** 첫 행의 앞 지문. */
    const val GENESIS = "GENESIS"

    /** 체인을 붙이기 전(0.41.4 이전)부터 있던 행의 앞 지문 — 번호 0, 검증 대상 아님(Q2). */
    const val LEGACY = "LEGACY"

    /**
     * 한 행의 지문 — SHA-256 소문자 hex 64자.
     *
     * [WHY] 필드마다 `길이:값` 으로 잇는다 — 구분자만 쓰면 `"a|b" + "c"` 와 `"a" + "b|c"` 가 같은 바이트가 되어, 내용을 옮겨 적고도 같은 지문을 만들 수 있다.
     */
    fun entryHash(chainSeq: Long, prevHash: String, id: String, timestamp: Long, type: String, sessionId: String, details: String): String {
        val text = listOf(chainSeq.toString(), prevHash, id, timestamp.toString(), type, sessionId, details)
            .joinToString("") { "${it.length}:$it" }
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun entryHashOf(event: AuditEvent): String =
        entryHash(event.chainSeq, event.prevHash, event.id, event.timestamp, event.type.name, event.sessionId, event.details)

    sealed interface Result {
        /**
         * @param verified 검증한 체인 행 수 @param legacy 체인 시작 전 행 수 @param head 마지막 체인 행(머리) — 체인 행이 없으면 null
         */
        data class Valid(val verified: Int, val legacy: Int, val headSeq: Long?, val headHash: String?) : Result

        /** [chainSeq] 번째 행([eventId])에서 처음 깨졌다. */
        data class Tampered(val chainSeq: Long, val eventId: String, val reason: Reason) : Result
    }

    enum class Reason {
        /** 내용이 바뀌었다 — 다시 계산한 지문이 저장된 지문과 다르다. */
        HASH_MISMATCH,

        /** 앞 행의 지문과 이어지지 않는다 — 행이 끼워졌거나 바뀌었다. */
        LINK_BROKEN,

        /** 번호가 건너뛰거나 거꾸로다 — 행이 지워졌거나 순서가 바뀌었다. */
        SEQUENCE_GAP
    }

    /**
     * 쪽 단위 검증기 — 기록이 수만 건이어도 한 번에 메모리에 올리지 않게 이어 받는다.
     * 번호 오름차순(체인 전 행 먼저)으로 넣는다. 한 번 깨지면 그 결과를 유지한다.
     */
    class Verifier {
        private var legacy = 0
        private var verified = 0
        private var lastSeq = 0L
        private var lastHash: String? = null
        private var broken: Result.Tampered? = null

        /** 이 쪽을 검사한다 — 깨졌으면 그 지점을 돌려준다(이후 쪽은 보지 않는다). */
        fun accept(events: List<AuditEvent>): Result.Tampered? {
            broken?.let { return it }
            for (e in events) {
                if (e.chainSeq == 0L) {
                    // [WHY] 체인 행 뒤에 번호 0 이 다시 나오면 번호가 거꾸로다 — 정렬은 오름차순이라 정상이면 체인 전 행이 먼저 온다.
                    if (lastHash != null) return fail(e, Reason.SEQUENCE_GAP)
                    legacy++
                    continue
                }
                val expectedPrev = lastHash ?: GENESIS
                if (e.chainSeq != lastSeq + 1) return fail(e, Reason.SEQUENCE_GAP)
                if (e.prevHash != expectedPrev) return fail(e, Reason.LINK_BROKEN)
                if (entryHashOf(e) != e.entryHash) return fail(e, Reason.HASH_MISMATCH)
                lastSeq = e.chainSeq
                lastHash = e.entryHash
                verified++
            }
            return null
        }

        fun result(): Result = broken ?: Result.Valid(verified, legacy, lastSeq.takeIf { lastHash != null }, lastHash)

        private fun fail(e: AuditEvent, reason: Reason): Result.Tampered = Result.Tampered(e.chainSeq, e.id, reason).also { broken = it }
    }

    /** 한 번에 검증 — 테스트·작은 목록용. */
    fun verify(eventsAscending: List<AuditEvent>): Result = Verifier().run { accept(eventsAscending) ?: result() }
}
