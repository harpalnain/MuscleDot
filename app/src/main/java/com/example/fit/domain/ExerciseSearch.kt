package com.example.fit.domain

private val WHITESPACE = Regex("\\s+")

/**
 * Exercises for the search list, sorted by name. Hidden exercises never match.
 * Every whitespace-separated term of [query] must appear somewhere in the name,
 * ignoring case; a blank query matches everything. [group] null means all groups.
 */
fun filterExercises(all: List<Exercise>, query: String, group: MuscleGroup?): List<Exercise> {
    val terms = query.trim().lowercase().split(WHITESPACE).filter { it.isNotEmpty() }
    return all
        .filter { !it.isHidden && (group == null || it.group == group) }
        .filter { exercise ->
            val name = exercise.name.lowercase()
            terms.all { it in name }
        }
        .sortedBy { it.name.lowercase() }
}
