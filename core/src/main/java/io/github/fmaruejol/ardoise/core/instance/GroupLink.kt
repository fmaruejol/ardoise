package io.github.fmaruejol.ardoise.core.instance

/**
 * Getting a group id out of whatever was pasted: a full URL, a URL to a page
 * inside the group, or the bare id.
 */
object GroupLink {
    /**
     * The group id in [input], or null. Only read from a URL with a `groups/`
     * segment to anchor on: ids are nanoid, so any segment could look like one.
     */
    fun parse(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val candidate = when {
            GROUPS_SEGMENT.containsMatchIn(trimmed) -> {
                trimmed.substringAfter(GROUPS_PREFIX).takeWhile { it !in PATH_DELIMITERS }
            }

            // An unrecognised URL: a stray last segment read as an id would
            // turn a wrong link into a group that never loads.
            '/' in trimmed -> {
                return null
            }

            else -> {
                trimmed
            }
        }

        if (candidate.isEmpty() || candidate in RESERVED_SEGMENTS) return null
        return candidate.takeIf { ID.matches(it) }
    }

    private const val GROUPS_PREFIX = "groups/"

    private val GROUPS_SEGMENT = Regex("(^|/)groups/")

    private val PATH_DELIMITERS = charArrayOf('/', '?', '#').toSet()

    /** Pages under `/groups/` that are not a group. */
    private val RESERVED_SEGMENTS = setOf("create")

    /** The nanoid alphabet. Length is left loose: it has not always been 21. */
    private val ID = Regex("^[A-Za-z0-9_-]{1,64}$")
}
