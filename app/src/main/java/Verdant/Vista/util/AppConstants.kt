package Verdant.Vista.util

object AppConstants {
    const val PREFS_NAME = "VerdantVistaPrefs"
    const val KEY_THEME = "app_theme"
    const val KEY_TAXON_TYPES = "taxon_types"
    const val KEY_PLACE_ID = "place_id"
    const val KEY_LOCATION_INDEX = "location_index"
    const val KEY_UPDATE_INTERVAL = "update_interval"
    const val KEY_PROXIMITY_ENABLED = "proximity_enabled"
    const val KEY_LAST_NOTIFICATION_TAXON = "last_notification_taxon"

    const val THEME_FOREST = "forest"
    const val THEME_DESERT = "desert"
    const val THEME_MIDNIGHT = "midnight"
    const val THEME_OCEAN = "ocean"
    const val THEME_SPRING = "spring"
    const val THEME_SUMMER = "summer"
    const val THEME_AUTUMN = "autumn"
    const val THEME_WINTER = "winter"

    const val TAXON_PLANTS = "plants"
    const val TAXON_BIRDS = "birds"
    const val TAXON_INSECTS = "insects"
    const val TAXON_HERPS = "herps"
    const val TAXON_AQUATIC = "aquatic"
    const val TAXON_FUNGI = "fungi"
    const val TAXON_MAMMALS = "mammals"

    const val ID_PLANTS = "47126" // Kingdom Plantae
    const val ID_BIRDS = "3" // Class Aves
    const val ID_INSECTS_ARACHNIDS = "47158,47119" // Insecta + Arachnida
    const val ID_MAMMALS = "40151" // Class Mammalia
    const val ID_FUNGI = "47170" // Kingdom Fungi
    const val ID_HERPS = "20978,20979" // Reptilia + Amphibia
    const val ID_AQUATIC = "47178,85497,47115" // Actinopterygii (Fish) + Crustacea (Crayfish/Shrimp) + Mollusca (Snails/Mussels)

    const val BIRD_TAXON_ID = 3
    const val MAMMAL_TAXON_ID = 40151
    const val REPTILE_TAXON_ID = 26036
    const val AMPHIBIAN_TAXON_ID = 20978
    const val INSECT_TAXON_ID = 47158
    const val FUNGI_TAXON_ID = 47170
    const val MOLLUSK_TAXON_ID = 47115
    const val FISH_TAXON_ID = 47178

    val DEFAULT_SPECIES_IDS = listOf(47125, 126513, 52110)

    /**
     * Maps a UI category key to its corresponding iNaturalist Taxon ID string.
     */
    fun getTaxonIdString(category: String): String? = when (category) {
        TAXON_BIRDS -> ID_BIRDS
        TAXON_INSECTS -> ID_INSECTS_ARACHNIDS
        TAXON_HERPS -> ID_HERPS
        TAXON_AQUATIC -> ID_AQUATIC
        TAXON_FUNGI -> ID_FUNGI
        TAXON_MAMMALS -> ID_MAMMALS
        TAXON_PLANTS -> ID_PLANTS
        else -> null
    }
}
