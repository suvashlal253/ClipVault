package com.clipvault.android.keyboard.transliteration

/**
 * Offline-first Bengali & English lexicon with ranked phonetic candidates, frequency weights,
 * and contextual next-word bigram predictions.
 */
object BengaliWordDictionary {

    data class LexiconEntry(
        val romanKey: String,
        val candidates: List<String>,
        val baseFrequency: Int = 100
    )

    // Core offline Banglish -> Bengali ranked dictionary (ordered by frequency & standard spelling preference)
    private val BANGLA_LEXICON: Map<String, LexiconEntry> by lazy {
        val entries = listOf(
            // Pronouns & Possessives
            LexiconEntry("ami", listOf("আমি", "আমিই", "আমির", "অমি"), 250),
            LexiconEntry("amii", listOf("আমিই", "আমি"), 190),
            LexiconEntry("amra", listOf("আমরা", "আমরাই", "আমাদের"), 230),
            LexiconEntry("amar", listOf("আমার", "আমারই", "আমাদের"), 250),
            LexiconEntry("amader", listOf("আমাদের", "আমাদেরই"), 220),
            LexiconEntry("amake", listOf("আমাকে", "আমাকেই"), 210),
            LexiconEntry("tumi", listOf("তুমি", "তুমিই", "তুমিতো"), 250),
            LexiconEntry("tomra", listOf("তোমরা", "তোমরাই"), 210),
            LexiconEntry("tomar", listOf("তোমার", "তোমারই", "তোমাদের"), 240),
            LexiconEntry("tomader", listOf("তোমাদের", "তোমাদেরই"), 200),
            LexiconEntry("tomake", listOf("তোমাকে", "তোমাকেই"), 220),
            LexiconEntry("apni", listOf("আপনি", "আপনিই", "আপনার"), 245),
            LexiconEntry("apnara", listOf("আপনারা", "আপনারাই"), 210),
            LexiconEntry("apnar", listOf("আপনার", "আপনারই", "আপনাদের"), 240),
            LexiconEntry("apnake", listOf("আপনাকে", "আপনাকেই"), 215),
            LexiconEntry("she", listOf("সে", "সেই", "শে"), 200),
            LexiconEntry("se", listOf("সে", "সেই"), 210),
            LexiconEntry("tara", listOf("তারা", "তারাই"), 200),
            LexiconEntry("tar", listOf("তার", "তারই", "তাঁর"), 210),
            LexiconEntry("tader", listOf("তাদের", "তাদেরই"), 195),
            LexiconEntry("tui", listOf("তুই", "তুইই"), 190),
            LexiconEntry("tor", listOf("তোর", "তোরই"), 185),
            LexiconEntry("toke", listOf("তোকে", "তোকেই"), 185),
            LexiconEntry("nijer", listOf("নিজের", "নিজেরই"), 190),
            LexiconEntry("nije", listOf("নিজে", "নিজেই"), 190),

            // Identity, Greetings & Common Conversational Words
            LexiconEntry("nam", listOf("নাম", "নামে", "নামই"), 245),
            LexiconEntry("naam", listOf("নাম", "নামে", "নামই"), 240),
            LexiconEntry("suvash", listOf("সুভাষ", "সুবাশ", "সুভাস"), 245),
            LexiconEntry("subhash", listOf("সুভাষ", "সুভাস", "সুবাশ"), 240),
            LexiconEntry("suvas", listOf("সুভাষ", "সুভাস", "সুবাশ"), 230),
            LexiconEntry("kemon", listOf("কেমন", "কেমনই", "কেমনে"), 250),
            LexiconEntry("kmn", listOf("কেমন", "কেমনে"), 210),
            LexiconEntry("acho", listOf("আছো", "আছ", "আছোই"), 245),
            LexiconEntry("achen", listOf("আছেন", "আছেনই"), 245),
            LexiconEntry("achi", listOf("আছি", "আছিই"), 245),
            LexiconEntry("ache", listOf("আছে", "আছেই"), 240),
            LexiconEntry("bhalo", listOf("ভালো", "ভাল", "ভালোই"), 250),
            LexiconEntry("valo", listOf("ভালো", "ভাল", "ভালোই"), 245),
            LexiconEntry("bhaloi", listOf("ভালোই", "ভালো"), 210),
            LexiconEntry("bhalobashi", listOf("ভালোবাসি", "ভালবাসি", "ভালোবাসা"), 250),
            LexiconEntry("valobashi", listOf("ভালোবাসি", "ভালবাসি", "ভালোবাসা"), 245),
            LexiconEntry("bhalobasha", listOf("ভালোবাসা", "ভালোবাসার", "ভালোবাসি"), 240),
            LexiconEntry("valobasha", listOf("ভালোবাসা", "ভালোবাসার", "ভালোবাসি"), 235),
            LexiconEntry("bangladesh", listOf("বাংলাদেশ", "বাংলাদেশে", "বাংলাদেশের"), 250),
            LexiconEntry("bangla", listOf("বাংলা", "বাংলায়", "বাংলার"), 250),
            LexiconEntry("bangali", listOf("বাঙালি", "বাঙ্গালী", "বাঙালির"), 220),
            LexiconEntry("dhonnobad", listOf("ধন্যবাদ", "ধন্যবাদই"), 240),
            LexiconEntry("dhonyobad", listOf("ধন্যবাদ"), 235),
            LexiconEntry("shuvo", listOf("শুভ", "শুভো"), 230),
            LexiconEntry("shubho", listOf("শুভ", "শুভো"), 230),
            LexiconEntry("shokal", listOf("সকাল", "সকালে", "শকাল"), 225),
            LexiconEntry("sokal", listOf("সকাল", "সকালে"), 225),
            LexiconEntry("dupur", listOf("দুপুর", "দুপুরে"), 200),
            LexiconEntry("bikal", listOf("বিকাল", "বিকালে", "বিকেল"), 205),
            LexiconEntry("ratri", listOf("রাত্রি", "রাত", "রাতে"), 215),
            LexiconEntry("rat", listOf("রাত", "রাতে", "রাত্রি"), 220),
            LexiconEntry("shuveccha", listOf("শুভেচ্ছা", "শুভেচ্ছান্তে"), 215),
            LexiconEntry("shagotom", listOf("স্বাগতম", "স্বাগত"), 230),
            LexiconEntry("bristi", listOf("বৃষ্টি", "বৃষ্টির", "বৃষ্টিতে"), 230),
            LexiconEntry("brishti", listOf("বৃষ্টি", "বৃষ্টির", "বৃষ্টিতে"), 230),
            LexiconEntry("abhinandan", listOf("অভিনন্দন"), 205),
            LexiconEntry("obhinondon", listOf("অভিনন্দন"), 205),
            LexiconEntry("nomoshkar", listOf("নমস্কার"), 215),
            LexiconEntry("salam", listOf("সালাম", "আসসালামু"), 215),

            // Question Words & Connectors
            LexiconEntry("ki", listOf("কি", "কী", "কিই"), 240),
            LexiconEntry("kii", listOf("কী", "কি"), 230),
            LexiconEntry("keno", listOf("কেন", "কেনো", "কেনই"), 230),
            LexiconEntry("kothay", listOf("কোথায়", "কোথায়", "কথায়"), 230),
            LexiconEntry("kobe", listOf("কবে", "কবেই"), 215),
            LexiconEntry("kokhon", listOf("কখন", "কখনো", "কখনই"), 220),
            LexiconEntry("kibhabe", listOf("কিভাবে", "কীভাবে"), 220),
            LexiconEntry("kivabe", listOf("কিভাবে", "কীভাবে"), 220),
            LexiconEntry("ke", listOf("কে", "কেউ", "কেই"), 215),
            LexiconEntry("keu", listOf("কেউ", "কেউই"), 210),
            LexiconEntry("koto", listOf("কত", "কতো"), 215),
            LexiconEntry("kon", listOf("কোন", "কোনো"), 210),
            LexiconEntry("kono", listOf("কোনো", "কোন"), 215),
            LexiconEntry("kichu", listOf("কিছু", "কিছুই"), 225),
            LexiconEntry("kintu", listOf("কিন্তু", "কিন্তুই"), 235),
            LexiconEntry("ebong", listOf("এবং"), 230),
            LexiconEntry("ar", listOf("আর", "আরও", "আরো"), 235),
            LexiconEntry("aro", listOf("আরও", "আরো", "আর"), 225),
            LexiconEntry("ba", listOf("বা", "বাপ"), 210),
            LexiconEntry("jodi", listOf("যদি", "যদিও"), 225),
            LexiconEntry("tahole", listOf("তাহলে", "তাহলেই"), 225),
            LexiconEntry("karon", listOf("কারণ", "কারন"), 220),
            LexiconEntry("jonno", listOf("জন্য", "জন্যে", "জন্যই"), 235),
            LexiconEntry("jonyo", listOf("জন্য", "জন্যে"), 225),
            LexiconEntry("theke", listOf("থেকে", "থেকেই"), 235),
            LexiconEntry("shathe", listOf("সাথে", "সাথেই"), 230),
            LexiconEntry("sathe", listOf("সাথে", "সাথেই"), 230),
            LexiconEntry("moddhe", listOf("মধ্যে", "মধ্যেই"), 220),
            LexiconEntry("upor", listOf("উপর", "উপরে"), 210),
            LexiconEntry("niche", listOf("নিচে", "নীচে"), 205),
            LexiconEntry("kachhe", listOf("কাছে", "কাছেই"), 215),
            LexiconEntry("kache", listOf("কাছে", "কাছেই"), 220),
            LexiconEntry("chara", listOf("ছাড়া", "ছাড়া"), 205),

            // High-Frequency Verbs
            LexiconEntry("koro", listOf("করো", "কর", "করোই"), 230),
            LexiconEntry("koren", listOf("করেন", "করুন"), 225),
            LexiconEntry("korun", listOf("করুন", "করেন"), 225),
            LexiconEntry("kori", listOf("করি", "করিই"), 230),
            LexiconEntry("korchi", listOf("করছি", "করছিই"), 230),
            LexiconEntry("korbo", listOf("করবো", "করব"), 230),
            LexiconEntry("korechi", listOf("করেছি", "করেছিই"), 225),
            LexiconEntry("kore", listOf("করে", "করেই", "করেছে"), 230),
            LexiconEntry("kora", listOf("করা", "করার"), 220),
            LexiconEntry("jabo", listOf("যাবো", "যাব", "যাবোই"), 235),
            LexiconEntry("jai", listOf("যাই", "যায়"), 225),
            LexiconEntry("jacchi", listOf("যাচ্ছি", "যাচ্ছিই"), 220),
            LexiconEntry("jao", listOf("যাও", "যাওয়া"), 220),
            LexiconEntry("jan", listOf("যান", "জান"), 215),
            LexiconEntry("asho", listOf("আসো", "আস"), 220),
            LexiconEntry("ashun", listOf("আসুন", "আসেন"), 220),
            LexiconEntry("ashbo", listOf("আসবো", "আসব"), 220),
            LexiconEntry("ashchi", listOf("আসছি", "আসছিই"), 220),
            LexiconEntry("eshechi", listOf("এসেছি", "এসেছিই"), 215),
            LexiconEntry("bolo", listOf("বলো", "বল", "বলোই"), 225),
            LexiconEntry("bolun", listOf("বলুন", "বলেন"), 225),
            LexiconEntry("boli", listOf("বলি", "বলিই"), 220),
            LexiconEntry("bolchi", listOf("বলছি", "বলছিই"), 220),
            LexiconEntry("dekho", listOf("দেখো", "দেখ"), 220),
            LexiconEntry("dekhun", listOf("দেখুন", "দেখেন"), 220),
            LexiconEntry("dekhi", listOf("দেখি", "দেখিই"), 220),
            LexiconEntry("dekhbo", listOf("দেখবো", "দেখব"), 215),
            LexiconEntry("jani", listOf("জানি", "জানিই", "জানা"), 230),
            LexiconEntry("janina", listOf("জানিনা", "জানি না"), 220),
            LexiconEntry("paro", listOf("পারো", "পার"), 210),
            LexiconEntry("pari", listOf("পারি", "পারিই"), 215),
            LexiconEntry("parbo", listOf("পারবো", "পারব"), 215),
            LexiconEntry("chai", listOf("চাই", "চাইই", "চায়"), 225),
            LexiconEntry("chao", listOf("চাও", "চাওয়া"), 210),
            LexiconEntry("hobe", listOf("হবে", "হবেই"), 235),
            LexiconEntry("hoy", listOf("হয়", "হয়", "হয়ে"), 230),
            LexiconEntry("hoyeche", listOf("হয়েছে", "হয়েছে"), 225),
            LexiconEntry("holo", listOf("হলো", "হল"), 225),
            LexiconEntry("thako", listOf("থাকো", "থাক"), 220),
            LexiconEntry("thakun", listOf("থাকুন", "থাকেন"), 220),
            LexiconEntry("thakbo", listOf("থাকবো", "থাকব"), 215),
            LexiconEntry("khabo", listOf("খাবো", "খাব"), 215),
            LexiconEntry("kheyechi", listOf("খেয়েছি", "খেয়েছি"), 210),
            LexiconEntry("likho", listOf("লিখো", "লেখো"), 205),
            LexiconEntry("likhchi", listOf("লিখছি", "লেখছি"), 205),
            LexiconEntry("poro", listOf("পড়ো", "পরো", "পড়"), 205),
            LexiconEntry("shuno", listOf("শুনো", "শোনো"), 210),
            LexiconEntry("shunun", listOf("শুনুন", "শোনেন"), 210),
            LexiconEntry("bujhechi", listOf("বুঝেছি", "বুঝেছিই"), 210),

            // Time, Places, Daily Life & Nouns
            LexiconEntry("aj", listOf("আজ", "আজকে", "আজই"), 235),
            LexiconEntry("ajke", listOf("আজকে", "আজকেই", "আজ"), 235),
            LexiconEntry("kal", listOf("কাল", "কালকে"), 225),
            LexiconEntry("kalke", listOf("কালকে", "কালকেই", "কাল"), 225),
            LexiconEntry("ekhon", listOf("এখন", "এখনই", "এখনো"), 240),
            LexiconEntry("ekhono", listOf("এখনো", "এখনও", "এখন"), 225),
            LexiconEntry("pore", listOf("পরে", "পরেই", "পড়ে"), 220),
            LexiconEntry("age", listOf("আগে", "আগেই"), 220),
            LexiconEntry("shomoy", listOf("সময়", "সময়", "সময়ে"), 230),
            LexiconEntry("somoy", listOf("সময়", "সময়", "সময়ে"), 230),
            LexiconEntry("din", listOf("দিন", "দিনে", "দিনই"), 220),
            LexiconEntry("bochor", listOf("বছর", "বছরে"), 215),
            LexiconEntry("mash", listOf("মাস", "মাসে"), 210),
            LexiconEntry("shoptaho", listOf("সপ্তাহ", "সপ্তাহে"), 205),
            LexiconEntry("bari", listOf("বাড়ি", "বাড়ি", "বাড়িতে"), 230),
            LexiconEntry("barite", listOf("বাড়িতে", "বাড়িতে", "বাড়ি"), 225),
            LexiconEntry("basay", listOf("বাসায়", "বাসায়", "বাসা"), 220),
            LexiconEntry("basa", listOf("বাসা", "বাসায়"), 215),
            LexiconEntry("desh", listOf("দেশ", "দেশে", "দেশের"), 230),
            LexiconEntry("deshe", listOf("দেশে", "দেশের", "দেশ"), 220),
            LexiconEntry("shohor", listOf("শহর", "শহরে"), 210),
            LexiconEntry("gram", listOf("গ্রাম", "গ্রামে"), 210),
            LexiconEntry("dhaka", listOf("ঢাকা", "ঢাকায়", "ঢাকার"), 230),
            LexiconEntry("kolkata", listOf("কলকাতা", "কলকাতায়"), 220),
            LexiconEntry("sylhet", listOf("সিলেট", "সিলেটে"), 220),
            LexiconEntry("chittagong", listOf("চট্টগ্রাম", "চট্টগ্রামে"), 215),
            LexiconEntry("kaj", listOf("কাজ", "কাজে", "কাজের"), 235),
            LexiconEntry("kaje", listOf("কাজে", "কাজের", "কাজ"), 220),
            LexiconEntry("kotha", listOf("কথা", "কথায়", "কথাই"), 235),
            LexiconEntry("manush", listOf("মানুষ", "মানুষের", "মানুষই"), 230),
            LexiconEntry("bondhu", listOf("বন্ধু", "বন্ধুরা", "বন্ধুর"), 235),
            LexiconEntry("jibon", listOf("জীবন", "জীবনে", "জীবনের"), 225),
            LexiconEntry("mon", listOf("মন", "মনে", "মনের"), 230),
            LexiconEntry("mone", listOf("মনে", "মনের", "মন"), 230),
            LexiconEntry("khabar", listOf("খাবার", "খাবারের"), 215),
            LexiconEntry("pani", listOf("পানি", "পানির"), 215),
            LexiconEntry("jol", listOf("জল", "জলের"), 210),
            LexiconEntry("cha", listOf("চা", "চায়ের"), 215),
            LexiconEntry("bhat", listOf("ভাত", "ভাতের"), 215),
            LexiconEntry("taka", listOf("টাকা", "টাকার", "টাকায়"), 225),
            LexiconEntry("boi", listOf("বই", "বইটি", "বইয়ের"), 215),
            LexiconEntry("gaan", listOf("গান", "গানের"), 215),
            LexiconEntry("chobi", listOf("ছবি", "ছবির"), 215),

            // Family & People
            LexiconEntry("ma", listOf("মা", "মায়ের", "মাকে"), 235),
            LexiconEntry("maa", listOf("মা", "মায়ের", "মাকে"), 235),
            LexiconEntry("baba", listOf("বাবা", "বাবার", "বাবাকে"), 235),
            LexiconEntry("bhai", listOf("ভাই", "ভাইয়া", "ভাইয়ের"), 235),
            LexiconEntry("vai", listOf("ভাই", "ভাইয়া", "ভাইয়ের"), 230),
            LexiconEntry("bhaiya", listOf("ভাইয়া", "ভাই"), 220),
            LexiconEntry("bon", listOf("বোন", "বোনের"), 220),
            LexiconEntry("apu", listOf("আপু", "আপুর"), 220),
            LexiconEntry("dada", listOf("দাদা", "দাদার"), 220),
            LexiconEntry("didi", listOf("দিদি", "দিদির"), 220),
            LexiconEntry("poribar", listOf("পরিবার", "পরিবারের"), 215),

            // Adjectives, Adverbs & Negation
            LexiconEntry("na", listOf("না", "নাই", "নয়"), 245),
            LexiconEntry("nai", listOf("নাই", "নেই", "না"), 230),
            LexiconEntry("nei", listOf("নেই", "নাই"), 230),
            LexiconEntry("noy", listOf("নয়", "নয়", "না"), 220),
            LexiconEntry("ha", listOf("হ্যাঁ", "হা"), 230),
            LexiconEntry("haan", listOf("হ্যাঁ"), 230),
            LexiconEntry("hya", listOf("হ্যাঁ"), 235),
            LexiconEntry("khub", listOf("খুব", "খুবই"), 240),
            LexiconEntry("khubi", listOf("খুবই", "খুব"), 230),
            LexiconEntry("onek", listOf("অনেক", "অনেকেই", "অনেকের"), 240),
            LexiconEntry("shob", listOf("সব", "সবাই", "সবকিছু"), 235),
            LexiconEntry("sob", listOf("সব", "সবাই", "সবকিছু"), 235),
            LexiconEntry("shobai", listOf("সবাই", "সবাইকে", "সবার"), 235),
            LexiconEntry("sobai", listOf("সবাই", "সবাইকে", "সবার"), 235),
            LexiconEntry("shundor", listOf("সুন্দর", "সুন্দরী"), 230),
            LexiconEntry("sundor", listOf("সুন্দর", "সুন্দরী"), 230),
            LexiconEntry("notun", listOf("নতুন", "নতুনই"), 225),
            LexiconEntry("boro", listOf("বড়", "বড়", "বড়ো"), 220),
            LexiconEntry("choto", listOf("ছোট", "ছোটো"), 220),
            LexiconEntry("shohoj", listOf("সহজ", "সহজেই"), 215),
            LexiconEntry("kothin", listOf("কঠিন"), 210),
            LexiconEntry("shotti", listOf("সত্যি", "সত্যিই", "সত্য"), 220),
            LexiconEntry("thik", listOf("ঠিক", "ঠিকই"), 230),
            LexiconEntry("ekdom", listOf("একদম", "একদমই"), 220),
            LexiconEntry("ektu", listOf("একটু", "একটুখানি"), 225),
            LexiconEntry("ekta", listOf("একটা", "একটি"), 230),
            LexiconEntry("ekti", listOf("একটি", "একটা"), 225),
            LexiconEntry("oboshsho", listOf("অবশ্য", "অবশ্যই"), 220),
            LexiconEntry("oboshshoi", listOf("অবশ্যই", "অবশ্য"), 225),
            LexiconEntry("ashole", listOf("আসলে", "আসলেই"), 220),
            LexiconEntry("priyo", listOf("প্রিয়", "প্রিয়"), 220),
            LexiconEntry("kharap", listOf("খারাপ", "খারাপই"), 215),
            LexiconEntry("taratari", listOf("তাড়াতাড়ি", "তাড়াতাড়ি"), 215),

            // Tech, Work & Study Loan/Transliterated Words
            LexiconEntry("office", listOf("অফিস", "অফিসে", "office"), 220),
            LexiconEntry("school", listOf("স্কুল", "স্কুলে", "school"), 215),
            LexiconEntry("college", listOf("কলেজ", "কলেজে", "college"), 215),
            LexiconEntry("university", listOf("ইউনিভার্সিটি", "বিশ্ববিদ্যালয়", "university"), 210),
            LexiconEntry("phone", listOf("ফোন", "ফোনে", "phone"), 220),
            LexiconEntry("mobile", listOf("মোবাইল", "মোবাইলে", "mobile"), 220),
            LexiconEntry("message", listOf("মেসেজ", "বার্তা", "message"), 215),
            LexiconEntry("computer", listOf("কম্পিউটার", "computer"), 210),
            LexiconEntry("internet", listOf("ইন্টারনেট", "internet"), 210)
        )
        entries.associateBy { it.romanKey.lowercase() }
    }

    // Contextual Bangla Next-Word Bigrams
    private val BANGLA_BIGRAMS: Map<String, List<String>> = mapOf(
        "আমার" to listOf("নাম", "বাড়ি", "দেশ", "কাজ", "মনে"),
        "তোমার" to listOf("নাম", "সাথে", "জন্য", "কথা", "বাড়ি"),
        "আপনার" to listOf("নাম", "সাথে", "জন্য", "কাজ", "শরীর"),
        "কেমন" to listOf("আছো", "আছেন", "আছে", "লাগছে"),
        "আপনি" to listOf("কেমন", "কোথায়", "কি", "ভালো"),
        "তুমি" to listOf("কেমন", "কোথায়", "কি", "আমার"),
        "আমি" to listOf("ভালো", "তোমাকে", "এখন", "যাবো", "আছি"),
        "ভালো" to listOf("আছি", "লাগে", "থাকো", "মানুষ", "কথা"),
        "শুভ" to listOf("সকাল", "রাত্রি", "জন্মদিন", "কামনা", "নববর্ষ"),
        "অনেক" to listOf("ধন্যবাদ", "ভালো", "দিন", "সুন্দর", "কাজ"),
        "খুব" to listOf("ভালো", "সুন্দর", "তাড়াতাড়ি", "খুশি")
    )

    // Contextual English Next-Word Bigrams
    private val ENGLISH_BIGRAMS: Map<String, List<String>> = mapOf(
        "i" to listOf("am", "will", "have", "need", "think"),
        "how" to listOf("are", "is", "to", "about", "much"),
        "are" to listOf("you", "we", "they", "available"),
        "thank" to listOf("you", "goodness", "everyone"),
        "my" to listOf("name", "phone", "work", "home", "number"),
        "name" to listOf("is", "and", "card"),
        "good" to listOf("morning", "night", "evening", "job", "idea"),
        "see" to listOf("you", "the", "if", "soon"),
        "can" to listOf("you", "we", "i", "be"),
        "please" to listOf("send", "let", "check", "call")
    )

    // Common English Words for Prefix Completion & Typo Correction
    private val ENGLISH_COMMON_WORDS: List<String> = listOf(
        "the", "that", "this", "there", "their", "they", "thank", "thanks", "today", "tomorrow", "time", "together",
        "and", "about", "after", "again", "always", "around", "available", "address", "application", "android",
        "because", "before", "better", "between", "business", "brother", "beautiful", "bangladesh", "bangla",
        "can", "Clipboard", "ClipVault", "come", "could", "call", "check", "change", "company", "complete",
        "day", "do", "don't", "does", "down", "during", "different", "document", "developer",
        "email", "every", "everything", "everyone", "example", "experience",
        "for", "from", "first", "Family", "friend", "friends", "found", "future", "following",
        "good", "great", "Going", "give", "group", "general", "government",
        "have", "hello", "help", "here", "home", "house", "however", "happy",
        "important", "information", "into", "interest", "include", "internet",
        "just", "job", "join",
        "keep", "keyboard", "know", "knowledge",
        "like", "look", "love", "little", "language", "location", "looking",
        "make", "many", "more", "most", "morning", "message", "meeting", "mobile", "money", "moment",
        "name", "need", "never", "next", "night", "number", "nothing", "national",
        "office", "only", "other", "online", "order", "official",
        "people", "phone", "please", "place", "project", "problem", "possible", "personal", "password",
        "question", "quick", "quickly",
        "really", "right", "remember", "receive", "report", "request",
        "should", "some", "something", "someone", "system", "service", "school", "schedule", "support",
        "think", "through", "together", "tonight",
        "under", "understand", "until", "update", "using", "university",
        "very", "version", "video", "value",
        "want", "well", "where", "when", "which", "while", "with", "without", "work", "working", "world", "would",
        "year", "years", "yesterday", "you", "your", "yourself"
    )

    private val ENGLISH_TYPO_CORRECTIONS: Map<String, String> = mapOf(
        "teh" to "the",
        "adn" to "and",
        "recieve" to "receive",
        "definately" to "definitely",
        "seperate" to "separate",
        "occured" to "occurred",
        "untill" to "until",
        "tommorow" to "tomorrow",
        "im" to "I'm",
        "ive" to "I've",
        "id" to "I'd",
        "ill" to "I'll",
        "dont" to "don't",
        "cant" to "can't",
        "wont" to "won't",
        "didnt" to "didn't",
        "doesnt" to "doesn't",
        "isnt" to "isn't",
        "arent" to "aren't",
        "wasnt" to "wasn't",
        "werent" to "weren't",
        "havent" to "haven't",
        "hasnt" to "hasn't",
        "wouldnt" to "wouldn't",
        "couldnt" to "couldn't",
        "shouldnt" to "shouldn't",
        "thats" to "that's",
        "whats" to "what's",
        "lets" to "let's"
    )

    private val COMMON_ENGLISH_LOANWORDS: Set<String> = setOf(
        "today", "tomorrow", "office", "phone", "mobile", "email", "meeting", "project",
        "time", "online", "message", "link", "update", "group", "class", "exam", "sir", "boss",
        "ok", "okay", "yes", "no", "please", "sorry", "thanks", "hello", "hi", "bye"
    )

    /**
     * Exact lookup in the Bangla Phonetic lexicon.
     */
    fun lookupExactBangla(romanWord: String): LexiconEntry? {
        return BANGLA_LEXICON[romanWord.trim().lowercase()]
    }

    /**
     * Prefix lookup in the Bangla Phonetic lexicon for incremental composition completions.
     */
    fun lookupPrefixBangla(romanPrefix: String, limit: Int = 4): List<LexiconEntry> {
        val clean = romanPrefix.trim().lowercase()
        if (clean.length < 2) return emptyList()
        return BANGLA_LEXICON.values
            .asSequence()
            .filter { it.romanKey.startsWith(clean) && it.romanKey != clean }
            .sortedByDescending { it.baseFrequency }
            .take(limit)
            .toList()
    }

    /**
     * Returns true if the given token is a common English word often mixed in Banglish sentences
     * (e.g., "ami today office jabo").
     */
    fun isCommonEnglishLoanWord(word: String): Boolean {
        return COMMON_ENGLISH_LOANWORDS.contains(word.trim().lowercase())
    }

    /**
     * Returns contextual next-word predictions after committing [previousWord].
     */
    fun getNextWordPredictions(
        previousWord: String,
        languageMode: KeyboardLanguageMode
    ): List<String> {
        val clean = previousWord.trim()
        return when (languageMode) {
            KeyboardLanguageMode.BANGLA_PHONETIC -> {
                BANGLA_BIGRAMS[clean] ?: listOf("আমি", "তুমি", "ভালো", "কেমন", "ধন্যবাদ")
            }
            KeyboardLanguageMode.ENGLISH -> {
                ENGLISH_BIGRAMS[clean.lowercase()] ?: listOf("the", "I", "you", "and", "is")
            }
        }
    }

    /**
     * Returns English typo correction if applicable.
     */
    fun getEnglishAutocorrect(word: String): String? {
        return ENGLISH_TYPO_CORRECTIONS[word.trim().lowercase()]
    }

    /**
     * Returns English word completions matching [prefix].
     */
    fun getEnglishCompletions(prefix: String, limit: Int = 5): List<String> {
        val clean = prefix.trim()
        if (clean.isEmpty()) return emptyList()
        val lower = clean.lowercase()
        val isCapitalized = clean[0].isUpperCase()
        val results = LinkedHashSet<String>()

        // 1. Typo correction first
        ENGLISH_TYPO_CORRECTIONS[lower]?.let { results.add(it) }

        // 2. Exact match
        ENGLISH_COMMON_WORDS.firstOrNull { it.equals(lower, ignoreCase = true) }?.let { matched ->
            results.add(if (isCapitalized) matched.replaceFirstChar { it.uppercase() } else matched)
        }

        // 3. Prefix completions
        for (candidate in ENGLISH_COMMON_WORDS) {
            if (results.size >= limit) break
            if (candidate.lowercase().startsWith(lower) && !candidate.equals(lower, ignoreCase = true)) {
                results.add(if (isCapitalized) candidate.replaceFirstChar { it.uppercase() } else candidate)
            }
        }
        return results.toList()
    }
}
