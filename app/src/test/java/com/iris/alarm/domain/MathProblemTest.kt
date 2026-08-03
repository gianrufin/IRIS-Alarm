package com.iris.alarm.domain

import com.iris.alarm.domain.model.MathDifficulty
import com.iris.alarm.domain.model.MathProblem
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The generator is swept across many seeds rather than spot-checked: a bad
 * branch that only fires on one roll of the dice would still be a question
 * nobody can answer, at 6am, with an alarm going.
 */
class MathProblemTest {

    @Test
    fun `every generated answer is a non-negative integer`() {
        // Negatives would need a minus key on the keypad, and the challenge is
        // meant to be arithmetic rather than data entry.
        MathDifficulty.entries.forEach { difficulty ->
            repeat(SWEEP) { seed ->
                val problem = MathProblem.generate(difficulty, Random(seed))
                assertTrue(
                    "$difficulty seed=$seed produced ${problem.question} = ${problem.answer}",
                    problem.answer >= 0,
                )
            }
        }
    }

    @Test
    fun `the stated answer is the arithmetically correct one`() {
        MathDifficulty.entries.forEach { difficulty ->
            repeat(SWEEP) { seed ->
                val problem = MathProblem.generate(difficulty, Random(seed))
                assertEquals(
                    "$difficulty seed=$seed: ${problem.question}",
                    evaluate(problem.question),
                    problem.answer,
                )
            }
        }
    }

    @Test
    fun `divisions always come out exactly`() {
        // Built from a product for this reason; a remainder would need a rule
        // nobody wants explained to them before coffee.
        repeat(SWEEP) { seed ->
            val problem = MathProblem.generate(MathDifficulty.HARD, Random(seed))
            val parts = problem.question.split(" ")
            if (parts.getOrNull(1) != "÷") return@repeat
            assertEquals(0, parts[0].toInt() % parts[2].toInt())
        }
    }

    @Test
    fun `harder difficulties are actually harder`() {
        // Not a proof, but a regression guard: if HARD ever starts producing
        // single-digit sums the setting has stopped meaning anything.
        fun meanAnswer(difficulty: MathDifficulty) = (0 until SWEEP)
            .map { MathProblem.generate(difficulty, Random(it)).answer }
            .average()

        assertTrue(meanAnswer(MathDifficulty.MEDIUM) > meanAnswer(MathDifficulty.EASY))
        assertTrue(meanAnswer(MathDifficulty.HARD) > meanAnswer(MathDifficulty.MEDIUM))
    }

    @Test
    fun `grading ignores surrounding whitespace but nothing else`() {
        val problem = MathProblem("2 + 2", 4)
        assertTrue(problem.isCorrect("4"))
        assertTrue(problem.isCorrect(" 4 "))
        assertFalse(problem.isCorrect("04 5"))
        assertFalse(problem.isCorrect(""))
        assertFalse(problem.isCorrect("four"))
        assertFalse(problem.isCorrect("5"))
    }

    @Test
    fun `a leading zero still grades as the number it is`() {
        // The keypad can produce "04" if someone types over an empty field.
        assertTrue(MathProblem("2 + 2", 4).isCorrect("04"))
    }

    /** Left-to-right, which is all the generator ever produces. */
    private fun evaluate(question: String): Int {
        val tokens = question.split(" ")
        var result = tokens.first().toInt()
        var i = 1
        while (i < tokens.size) {
            val operand = tokens[i + 1].toInt()
            result = when (tokens[i]) {
                "+" -> result + operand
                "−" -> result - operand
                "×" -> result * operand
                "÷" -> result / operand
                else -> error("Unknown operator ${tokens[i]} in $question")
            }
            i += 2
        }
        return result
    }

    private companion object {
        const val SWEEP = 500
    }
}
