package com.relay.chainlang

/**
 * Validates that the reference graph across the whole block store is a DAG (spec §6.6) —
 * generic over however the real store looks up "what does this label directly reference",
 * so the same check works against a Room-backed store or, as in the tests here, a plain map.
 */
object CycleChecker {

    fun checkAcyclic(labels: Collection<String>, directReferencesOf: (String) -> Collection<String>) {
        val state = HashMap<String, VisitState>()

        fun visit(label: String, path: MutableList<String>) {
            when (state[label]) {
                VisitState.DONE -> return
                VisitState.IN_PROGRESS -> {
                    val cycleStart = path.indexOf(label)
                    throw CycleException(path.subList(cycleStart, path.size) + label)
                }
                null -> {
                    state[label] = VisitState.IN_PROGRESS
                    path.add(label)
                    for (next in directReferencesOf(label)) visit(next, path)
                    path.removeAt(path.size - 1)
                    state[label] = VisitState.DONE
                }
            }
        }

        for (label in labels) visit(label, mutableListOf())
    }

    private enum class VisitState { IN_PROGRESS, DONE }
}
