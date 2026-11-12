package hanten.wre.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MangaRelationTest {

	@Test
	fun reverseRelations() {
		assertEquals(MangaRelationType.PREQUEL, MangaRelationType.SEQUEL.reverse)
		assertEquals(MangaRelationType.SEQUEL, MangaRelationType.PREQUEL.reverse)
		assertEquals(MangaRelationType.ADAPTATION, MangaRelationType.ADAPTATION.reverse)
		assertEquals(MangaRelationType.SPIN_OFF, MangaRelationType.SPIN_OFF.reverse)
		assertEquals(MangaRelationType.SAME_UNIVERSE, MangaRelationType.SAME_UNIVERSE.reverse)
	}
}
