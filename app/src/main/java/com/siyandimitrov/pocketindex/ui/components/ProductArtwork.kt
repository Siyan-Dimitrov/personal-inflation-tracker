package com.siyandimitrov.pocketindex.ui.components

/**
 * Deterministic product artwork: an emoji chosen from the product name, falling back to the
 * category, then to a neutral basket. A local-first app has no product-photo database, so this
 * gives every product a recognisable face without any network or bundled images.
 */
fun productEmoji(name: String, categoryName: String? = null): String {
    val lowered = name.lowercase()
    keywordArtwork.forEach { (keywords, emoji) ->
        if (keywords.any { it in lowered }) return emoji
    }
    val category = categoryName?.lowercase().orEmpty()
    return when {
        "grocer" in category || "food" in category -> "🛒"
        "energy" in category || "utilit" in category -> "⚡"
        "transport" in category -> "🚗"
        "household" in category || "home" in category -> "🏠"
        else -> "📦"
    }
}

/** First match wins, so multi-word and brand-specific entries sit above their generic words. */
private val keywordArtwork: List<Pair<List<String>, String>> = listOf(
    listOf("coconut milk") to "🥥",
    listOf("milk chocolate", "chocolate", "choc") to "🍫",
    listOf("milk", "mlk") to "🥛",
    listOf("watermelon") to "🍉",
    listOf("melon") to "🍈",
    listOf("bread", "loaf", "baguette", "bagel") to "🍞",
    listOf("egg") to "🥚",
    listOf("cheese") to "🧀",
    listOf("butter", "spread") to "🧈",
    listOf("yoghurt", "yogurt") to "🥣",
    listOf("chicken", "chkn") to "🍗",
    listOf("burger") to "🍔",
    listOf("sausage", "frankfurter", "hot dog") to "🌭",
    listOf("bacon", "ham", "pork") to "🥓",
    listOf("beef", "steak", "mince", "lamb") to "🥩",
    listOf("salmon", "tuna", "cod", "fish") to "🐟",
    listOf("prawn", "shrimp") to "🦐",
    listOf("apple") to "🍎",
    listOf("banana") to "🍌",
    listOf("orange", "clementine", "satsuma", "tangerine") to "🍊",
    listOf("lemon", "lime") to "🍋",
    listOf("grape") to "🍇",
    listOf("strawberr", "raspberr", "blueberr", "berr") to "🍓",
    listOf("peach", "nectarine") to "🍑",
    listOf("avocado") to "🥑",
    listOf("tomato") to "🍅",
    listOf("potato") to "🥔",
    listOf("onion") to "🧅",
    listOf("garlic") to "🧄",
    listOf("carrot") to "🥕",
    listOf("pepper", "chilli", "chili") to "🌶",
    listOf("broccoli") to "🥦",
    listOf("cucumber", "courgette") to "🥒",
    listOf("lettuce", "salad", "spinach", "kale", "coriander", "herb") to "🥬",
    listOf("mushroom") to "🍄",
    listOf("corn", "maiz") to "🌽",
    listOf("rice") to "🍚",
    listOf("pasta", "spaghetti", "penne", "fusilli", "noodle") to "🍝",
    listOf("pizza") to "🍕",
    listOf("flour", "oat", "porridge", "cereal", "granola") to "🌾",
    listOf("salt") to "🧂",
    listOf("olive", "evoo", "oil") to "🫒",
    listOf("coffee") to "☕",
    listOf("tea") to "🍵",
    listOf("juice") to "🧃",
    listOf("beer", "lager", "ale", "cider") to "🍺",
    listOf("wine") to "🍷",
    listOf("water") to "💧",
    listOf("biscuit", "cookie") to "🍪",
    listOf("cake") to "🍰",
    listOf("ice cream") to "🍨",
    listOf("honey") to "🍯",
    listOf("bean", "soup", "tin") to "🥫",
    listOf("cashew", "peanut", "almond", "nut") to "🥜",
    listOf("sock") to "🧦",
    listOf("shirt", "jean", "trouser") to "👕",
    listOf("razor", "shave", "gillette", "gel") to "🪒",
    listOf("soap", "shampoo", "wash") to "🧼",
    listOf("tooth") to "🪥",
    listOf("toilet", "tissue", "kitchen towel") to "🧻",
    listOf("detergent", "laundry") to "🧺",
    listOf("electric", "energy") to "⚡",
    listOf("broadband", "internet", "wifi", "mobile", "sim") to "📶",
    listOf("council") to "🏛",
    listOf("gas") to "🔥",
    listOf("rent", "mortgage") to "🏠",
    listOf("insurance") to "🛡",
    listOf("petrol", "diesel", "fuel") to "⛽",
)
