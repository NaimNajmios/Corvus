package com.najmi.corvus.domain.util

import com.najmi.corvus.domain.model.EntityType
import com.najmi.corvus.domain.model.MediaEntityType

object EntityTypeDetector {

    fun detect(entityName: String, entityTypes: List<EntityType>): MediaEntityType {
        val kgEntityType = entityTypes.firstOrNull()

        if (kgEntityType != null) {
            return when (kgEntityType) {
                EntityType.PERSON, EntityType.POLITICIAN -> MediaEntityType.PERSON
                EntityType.COUNTRY                      -> MediaEntityType.COUNTRY
                EntityType.PLACE, EntityType.CITY        -> MediaEntityType.PLACE
                EntityType.ORGANIZATION, EntityType.GOVERNMENT_AGENCY, 
                EntityType.COMPANY                       -> MediaEntityType.ORGANISATION
                else                                     -> MediaEntityType.UNKNOWN
            }
        }

        val lower = entityName.lowercase().trim()

        if (COUNTRY_NAMES.contains(lower)) return MediaEntityType.COUNTRY

        val orgSuffixes = setOf(
            "berhad", "bhd", "sdn", "corporation", "corp", "limited", "ltd",
            "holdings", "group", "bank", "airlines", "airways", "petroleum",
            "nasional", "authority", "commission", "board", "foundation",
            "ministry", "jabatan", "lembaga", "suruhanjaya", "majlis",
            "agency", "institute", "university", "universiti", "college"
        )
        if (orgSuffixes.any { lower.contains(it) }) return MediaEntityType.ORGANISATION

        val myPlaces = setOf(
            "kuala lumpur", "kl", "petaling jaya", "pj", "shah alam", "johor bahru",
            "penang", "georgetown", "ipoh", "kota kinabalu", "kuching", "putrajaya",
            "cyberjaya", "subang", "klang", "ampang", "seremban", "melaka", "malacca",
            "kota bharu", "alor setar", "kangar", "labuan", "bandar seri begawan",
            "singapore", "jakarta", "bangkok", "manila", "hanoi", "ho chi minh city"
        )
        if (myPlaces.contains(lower)) return MediaEntityType.PLACE

        val words = entityName.trim().split(" ")
        if (words.size in 2..4 && words.all { it.first().isUpperCase() }) {
            return MediaEntityType.PERSON
        }

        return MediaEntityType.UNKNOWN
    }

    private val COUNTRY_NAMES = setOf(
        "malaysia", "singapore", "indonesia", "thailand", "philippines",
        "brunei", "myanmar", "vietnam", "cambodia", "laos", "timor-leste",
        "united states", "usa", "united kingdom", "uk", "china", "japan",
        "south korea", "north korea", "australia", "india", "pakistan",
        "bangladesh", "germany", "france", "italy", "spain", "netherlands",
        "russia", "brazil", "canada", "mexico", "saudi arabia", "uae",
        "israel", "turkey", "egypt", "south africa", "nigeria", "kenya",
        "new zealand", "ireland", "sweden", "norway", "denmark", "finland",
        "switzerland", "austria", "belgium", "portugal", "poland", "ukraine",
        "argentina", "chile", "colombia", "peru", "venezuela", "cuba",
        "iran", "iraq", "qatar", "kuwait", "bahrain", "oman", "jordan",
        "lebanon", "syria", "palestine", "yemen", "afghanistan", "pakistan",
        "taiwan", "hong kong", "macau", "mongolia", "nepal", "sri lanka",
        "bhutan", "maldives", "bangladesh", "myanmar", "cambodia", "laos"
    )
}
