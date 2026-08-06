package com.kosmos.app.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kosmos.app.core.common.FloatBytes

/**
 * [KosmosMigrations]
 * Room 스키마 마이그레이션 정의입니다.
 *
 * ### Architecture Context
 * - **Layer**: Data (Local DB)
 *
 * [WHY] 이전에는 `DatabaseModule` 안에 익명 객체로 인라인돼 있어 `MigrationTestHelper` 로
 * 검증할 수가 없었다. 데이터를 재인코딩하는 마이그레이션(4→5)이 생기면서 테스트가 필수가 됐고,
 * 그래서 별도 파일로 꺼냈다.
 *
 * [WHY] 사용자 메모리가 핵심 가치인 앱이므로 파괴적 마이그레이션을 쓰지 않는다.
 * `fallbackToDestructiveMigrationOnDowngrade()` 만 걸려 있어 업그레이드 경로는 항상 명시적
 * Migration 을 요구한다 — 빠뜨리면 런타임에 `IllegalStateException` 이 난다.
 */
object KosmosMigrations {

    /** 일정 종료 시각/설명 보존을 위한 스키마 확장. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE task_item ADD COLUMN endDateIso TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE task_item ADD COLUMN description TEXT DEFAULT NULL")
        }
    }

    /**
     * `knowledge_note.embedding` 을 콤마 구분 TEXT 에서 little-endian float BLOB 으로 옮깁니다.
     *
     * [WHY] SQLite 는 칼럼 타입을 `ALTER` 로 바꿀 수 없고, CSV→바이트 재인코딩은 SQL 로
     * 표현할 수도 없다. 그래서 새 테이블을 만들어 복사하고, 임베딩만 Kotlin 에서 행 단위로
     * 변환해 채운다. 임베딩을 버리는 쪽이 훨씬 간단하지만 재생성 경로가 없어서 기존 노트가
     * 벡터 검색에서 영구히 사라진다 (사용자 결정).
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `knowledge_note_new` (
                    `id` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `sourceSessionId` TEXT,
                    `tags` TEXT NOT NULL,
                    `embedding` BLOB,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `knowledge_note_new`
                    (`id`, `content`, `sourceSessionId`, `tags`, `embedding`, `createdAt`, `updatedAt`)
                SELECT `id`, `content`, `sourceSessionId`, `tags`, NULL, `createdAt`, `updatedAt`
                FROM `knowledge_note`
                """.trimIndent()
            )

            // [WHY] 커서를 먼저 완전히 소진해 리스트로 옮긴다 — DROP TABLE 전에 커서가 닫혀
            // 있어야 하고, 순회 중 UPDATE 를 섞으면 SQLite 가 같은 연결에서 잠긴다.
            val encoded = mutableListOf<Pair<String, ByteArray>>()
            db.query("SELECT `id`, `embedding` FROM `knowledge_note`").use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val csv = if (cursor.isNull(1)) null else cursor.getString(1)
                    val values = csv?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }.orEmpty()
                    if (values.isNotEmpty()) {
                        encoded += id to FloatBytes.encode(values.toFloatArray())
                    }
                }
            }
            encoded.forEach { (id, blob) ->
                db.execSQL("UPDATE `knowledge_note_new` SET `embedding` = ? WHERE `id` = ?", arrayOf(blob, id))
            }

            db.execSQL("DROP TABLE `knowledge_note`")
            db.execSQL("ALTER TABLE `knowledge_note_new` RENAME TO `knowledge_note`")
            // [WHY] 테이블을 새로 만들면 인덱스가 함께 사라진다. 재생성을 빼먹으면 Room 의
            // 스키마 검증이 다음 실행에서 실패한다.
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_note_createdAt` ON `knowledge_note` (`createdAt`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_3_4, MIGRATION_4_5)
}
