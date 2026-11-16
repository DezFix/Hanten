package hanten.wre.app.core.model

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreferredBranchesTest {

	@After
	fun tearDown() {
		PreferredBranches.update(null)
	}

	@Test
	fun parseSplitsAndTrims() {
		assertEquals(
			listOf("AnimeLib", "Mint Manga", "Team X"),
			PreferredBranches.parse(" AnimeLib, Mint Manga ;\nTeam X ,, "),
		)
		assertEquals(emptyList<String>(), PreferredBranches.parse(null))
	}

	@Test
	fun pickFollowsUserPriority() {
		PreferredBranches.update("second team, first")
		val branches = mapOf<String?, Int>("First Team" to 10, "Second Team" to 3, null to 1)
		assertEquals("Second Team", PreferredBranches.pick(branches))
	}

	@Test
	fun pickIsCaseInsensitiveAndNullWithoutMatch() {
		PreferredBranches.update("mint")
		assertEquals("MintManga", PreferredBranches.pick(mapOf<String?, Int>("MintManga" to 1)))
		PreferredBranches.update("nobody")
		assertNull(PreferredBranches.pick(mapOf<String?, Int>("MintManga" to 1, null to 2)))
	}
}
