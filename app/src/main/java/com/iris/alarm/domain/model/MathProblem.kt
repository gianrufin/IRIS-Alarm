package com.iris.alarm.domain.model

import kotlin.random.Random

enum class MathDifficulty {
    /** Two-digit addition and subtraction. Awake enough to read, basically. */
    EASY,

    /** Carries, borrows, and a two-by-one-digit multiplication. */
    MEDIUM,

    /** Two-by-two-digit multiplication, or a three-term sum. */
    HARD,
    ;

    val displayName: String
        get() = when (this) {
            EASY -> "Easy"
            MEDIUM -> "Medium"
            HARD -> "Hard"
        }

    val example: String
        get() = when (this) {
            EASY -> "47 + 26"
            MEDIUM -> "38 × 7"
            HARD -> "24 × 17"
        }
}

/**
 * One arithmetic question and its answer.
 *
 * The answer is carried alongside rather than recomputed at check time, so the
 * question the user is looking at and the answer being graded can never come
 * from different rolls of the generator.
 */
data class MathProblem(val question: String, val answer: Int) {

    /** Ignores surrounding space so a stray keypad press is not a wrong answer. */
    fun isCorrect(input: String): Boolean = input.trim().toIntOrNull() == answer

    companion object {
        /**
         * Generates a problem at [difficulty].
         *
         * Every problem has a non-negative integer answer: negatives and
         * fractions need a minus key and a decimal point on a keypad someone is
         * poking at half asleep, and the challenge is meant to be arithmetic
         * rather than data entry.
         */
        fun generate(difficulty: MathDifficulty, random: Random = Random): MathProblem =
            when (difficulty) {
                MathDifficulty.EASY -> easy(random)
                MathDifficulty.MEDIUM -> medium(random)
                MathDifficulty.HARD -> hard(random)
            }

        private fun easy(random: Random): MathProblem {
            val a = random.nextInt(11, 60)
            val b = random.nextInt(2, 40)
            return if (random.nextBoolean()) {
                MathProblem("$a + $b", a + b)
            } else {
                // Ordered so the result never goes below zero.
                val (big, small) = if (a >= b) a to b else b to a
                MathProblem("$big − $small", big - small)
            }
        }

        private fun medium(random: Random): MathProblem = when (random.nextInt(3)) {
            0 -> {
                val a = random.nextInt(25, 90)
                val b = random.nextInt(15, 80)
                MathProblem("$a + $b", a + b)
            }

            1 -> {
                val big = random.nextInt(60, 130)
                val small = random.nextInt(15, 59)
                MathProblem("$big − $small", big - small)
            }

            else -> {
                val a = random.nextInt(12, 40)
                val b = random.nextInt(3, 10)
                MathProblem("$a × $b", a * b)
            }
        }

        private fun hard(random: Random): MathProblem = when (random.nextInt(3)) {
            0 -> {
                val a = random.nextInt(12, 30)
                val b = random.nextInt(11, 25)
                MathProblem("$a × $b", a * b)
            }

            1 -> {
                val a = random.nextInt(40, 200)
                val b = random.nextInt(40, 200)
                val c = random.nextInt(10, 90)
                MathProblem("$a + $b − $c", a + b - c)
            }

            else -> {
                // Written as a division but built from a product, so it always
                // divides exactly — no remainders to explain at 6am.
                val divisor = random.nextInt(3, 13)
                val quotient = random.nextInt(11, 40)
                MathProblem("${divisor * quotient} ÷ $divisor", quotient)
            }
        }
    }
}
