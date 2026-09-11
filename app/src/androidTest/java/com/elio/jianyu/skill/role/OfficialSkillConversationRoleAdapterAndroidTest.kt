package com.elio.jianyu.skill.role

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.data.CharacterRepository
import com.elio.jianyu.data.RoundtableDatabase
import com.elio.jianyu.skill.SkillLoader
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogRuntimeResult
import com.elio.jianyu.skill.catalog.createOfficialSkillCatalogRuntime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficialSkillConversationRoleAdapterAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: RoundtableDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, RoundtableDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun packagedFunctionalRoleCanBeMaterializedOnDemandFromOfficialAsset() = runBlocking {
        val runtimeResult = createOfficialSkillCatalogRuntime(context)
        assertTrue(runtimeResult is OfficialSkillCatalogRuntimeResult.Success)
        val runtime = (runtimeResult as OfficialSkillCatalogRuntimeResult.Success).runtime
        val definition = requireNotNull(runtime.catalog.findById("meeting-to-action"))
        assertTrue(definition.availability.executable)
        assertNotNull(definition.assetPath)

        val repository = CharacterRepository(database.characterDao())
        val adapter = OfficialSkillConversationRoleAdapter(context, repository)
        val compatible = requireNotNull(adapter.ensureCompatibleCharacter(definition))
        val stored = requireNotNull(repository.getCharacterById(definition.id))

        assertEquals(definition.id, compatible.id)
        assertEquals(definition.nameZh, stored.name)
        assertEquals(definition.summary, stored.tagline)
        assertEquals(definition.assetPath, stored.skillAssetPath)
        assertEquals(definition.defaultOrder, stored.order)
        assertEquals("", stored.systemPrompt)
        assertTrue(SkillLoader.loadSkill(context, stored.skillAssetPath).isNotBlank())
    }
}
