package com.kosmos.app.testing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kosmos.app.data.local.db.KosmosDatabase
import org.junit.rules.ExternalResource

/**
 * 테스트마다 새 in-memory [KosmosDatabase] 를 열고 닫는 JUnit Rule 입니다 (Robolectric 러너 필요).
 *
 * [WHY] 같은 `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries().build()` + `close()` 가 저장소
 * 테스트 다섯 곳에 복제돼 있었다. 빌더 옵션을 바꿀 일(예: 쿼리 콜백으로 SQL 검증)이 생기면 한 곳만
 * 고쳐지는 부류다. Rule 의 before() 는 @Before 보다 먼저 돌므로 setUp 에서 [db] 를 바로 쓸 수 있다.
 */
class InMemoryKosmosDbRule : ExternalResource() {

    lateinit var db: KosmosDatabase
        private set

    override fun before() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, KosmosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    override fun after() {
        db.close()
    }
}
