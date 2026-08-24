package dres.run.validation

import dev.dres.run.validation.QaAnswerSetValidator
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QaAnswerSetValidatorTest {
    private val validator = QaAnswerSetValidator(
        listOf(
            QaAnswerSetValidator.Target(listOf(Regex("DOG")), "L22_V025", 740_000, 750_000),
            QaAnswerSetValidator.Target(listOf(Regex("CANINE"), Regex("PUPPY")), "L22_V025", 740_000, 750_000),
            QaAnswerSetValidator.Target(listOf(Regex("CAT")), "L22_V040", 20_000, 30_000)
        )
    )

    @Test
    fun `accepts any alternative text in the same query target`() {
        assertTrue(validator.matches("DOG", "L22_V025", 745_000, 745_000))
        assertTrue(validator.matches("CANINE", "L22_V025", 745_000, 745_000))
        assertTrue(validator.matches("PUPPY", "L22_V025", 745_000, 745_000))
    }

    @Test
    fun `accepts an independent query target but never mixes its text and range`() {
        assertTrue(validator.matches("CAT", "L22_V040", 25_000, 25_000))
        assertFalse(validator.matches("CAT", "L22_V025", 745_000, 745_000))
        assertFalse(validator.matches("DOG", "L22_V040", 25_000, 25_000))
    }

    @Test
    fun `requires one temporal point inside the matching range`() {
        assertFalse(validator.matches("DOG", "L22_V025", 750_001, 750_001))
        assertFalse(validator.matches("DOG", "L22_V025", 745_000, 745_001))
    }
}
