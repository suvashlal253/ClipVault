package com.clipvault.android.keyboard.transliteration

/**
 * Structured offline-first Bengali & English lexicon with ranked phonetic candidates, frequency weights,
 * complex conjunct word coverage, brand/name transliterations, contextual next-word bigram predictions,
 * and general mixed-language token classification (v1.7.2 Requirement 3).
 */
object BengaliWordDictionary {

    data class LexiconEntry(
        val romanKey: String,
        val candidates: List<String>,
        val baseFrequency: Int = 100
    )

    // Raw lexicon list exposed for automated dictionary integrity & key/candidate auditing (v1.7.4 Requirement 5)
    private val RAW_BANGLA_LEXICON_ENTRIES: List<LexiconEntry> by lazy {
        listOf(
            // =========================================================================
            // 1. MANDATORY REGRESSION & HIGH-FREQUENCY EVERYDAY BANGLISH WORDS (v1.7.4)
            // =========================================================================
            LexiconEntry("bektigoto", listOf("ব্যক্তিগত", "ব্যক্তিগতভাবে", "ব্যক্তিগতই"), 260),
            LexiconEntry("byoktigoto", listOf("ব্যক্তিগত", "ব্যক্তিগতভাবে"), 255),
            LexiconEntry("bektigotovabe", listOf("ব্যক্তিগতভাবে", "ব্যক্তিগত"), 250),
            LexiconEntry("bektigotobhabe", listOf("ব্যক্তিগতভাবে", "ব্যক্তিগত"), 250),
            LexiconEntry("bekti", listOf("ব্যক্তি", "ব্যক্তির", "ব্যক্তিত্ব"), 245),
            LexiconEntry("byokti", listOf("ব্যক্তি", "ব্যক্তির", "ব্যক্তিত্ব"), 245),
            LexiconEntry("bidyut", listOf("বিদ্যুৎ", "বিদ্যুতের", "বিদ্যুতে"), 260),
            LexiconEntry("biddut", listOf("বিদ্যুৎ", "বিদ্যুতের"), 255),
            LexiconEntry("krishi", listOf("কৃষি", "কৃষির", "কৃষিকাজ", "কৃষক"), 260),
            LexiconEntry("krishok", listOf("কৃষক", "কৃষকের", "কৃষি"), 245),
            LexiconEntry("chad", listOf("চাঁদ", "চাঁদের", "চাঁদনী", "ছাদ"), 260),
            LexiconEntry("chaad", listOf("চাঁদ", "চাঁদের", "ছাদ"), 255),
            LexiconEntry("utshob", listOf("উৎসব", "উৎসবের", "উৎসবে"), 260),
            LexiconEntry("utsob", listOf("উৎসব", "উৎসবের", "উৎসবে"), 255),
            LexiconEntry("utshab", listOf("উৎসব", "উৎসবের"), 250),
            LexiconEntry("ha", listOf("হ্যাঁ", "হা"), 260),
            LexiconEntry("haa", listOf("হ্যাঁ", "হা"), 260),
            LexiconEntry("haan", listOf("হ্যাঁ"), 250),
            LexiconEntry("hya", listOf("হ্যাঁ"), 255),
            LexiconEntry("valo", listOf("ভালো", "ভাল", "ভালোই", "ভালোবাসি"), 260),
            LexiconEntry("bhalo", listOf("ভালো", "ভাল", "ভালোই", "ভালোবাসি"), 260),
            LexiconEntry("korsi", listOf("করছি", "করেছি", "করছিলাম"), 260),
            LexiconEntry("korchi", listOf("করছি", "করেছি", "করছিই"), 260),
            LexiconEntry("korsilam", listOf("করছিলাম", "করেছিলাম"), 235),
            LexiconEntry("korso", listOf("করছো", "করেছো", "করছ"), 240),
            LexiconEntry("korse", listOf("করছে", "করেছে"), 240),
            LexiconEntry("facebook", listOf("ফেসবুক", "ফেসবুকে", "facebook"), 260),
            LexiconEntry("fb", listOf("ফেসবুক", "fb"), 235),
            LexiconEntry("bkash", listOf("বিকাশ", "বিকাশে", "bkash"), 260),
            LexiconEntry("bikash", listOf("বিকাশ", "বিকাশে"), 255),
            LexiconEntry("onko", listOf("অঙ্ক", "অংক", "অঙ্কের"), 260),
            LexiconEntry("angko", listOf("অঙ্ক", "অংক"), 245),
            LexiconEntry("bakko", listOf("বাক্য", "বাক্যের", "বাক্যে"), 260),
            LexiconEntry("bakyo", listOf("বাক্য", "বাক্যের"), 255),
            LexiconEntry("purkaystha", listOf("পুরকায়স্থ", "পুরকায়স্থ"), 260),
            LexiconEntry("purkayastha", listOf("পুরকায়স্থ", "পুরকায়স্থ"), 255),
            LexiconEntry("dhormo", listOf("ধর্ম", "ধর্মের", "ধর্মীয়"), 260),
            LexiconEntry("dharma", listOf("ধর্ম", "ধর্মের"), 245),
            LexiconEntry("kormo", listOf("কর্ম", "কর্মের", "কর্মী"), 250),

            // =========================================================================
            // 2. ALL 18 MANDATORY COMPLEX CONJUNCT WORD FAMILIES
            //    (ক্ত, ক্ষ, জ্ঞ, ত্র, দ্ধ, দ্য, ন্দ, ন্ধ, ম্প, ষ্ট, স্থ, স্ক, স্ত, ন্ত, ত্ত, দ্ম, হ্ন, ল্প)
            // =========================================================================
            // ক্ত (kt)
            LexiconEntry("rokto", listOf("রক্ত", "রক্তের"), 235),
            LexiconEntry("shokti", listOf("শক্তি", "শক্তির", "শক্তিশালী"), 240),
            LexiconEntry("sokti", listOf("শক্তি", "শক্তির"), 235),
            LexiconEntry("bhokto", listOf("ভক্ত", "ভক্তি"), 225),
            LexiconEntry("mukti", listOf("মুক্তি", "মুক্ত"), 230),
            LexiconEntry("mukto", listOf("মুক্ত", "মুক্তি"), 225),
            LexiconEntry("jukto", listOf("যুক্ত", "যুক্তি"), 230),
            LexiconEntry("jukti", listOf("যুক্তি", "যুক্ত"), 230),
            LexiconEntry("chokti", listOf("চুক্তি", "চুক্তির"), 220),
            LexiconEntry("chukti", listOf("চুক্তি", "চুক্তির"), 225),

            // ক্ষ (ksh / kkh)
            LexiconEntry("shikkha", listOf("শিক্ষা", "শিক্ষার", "শিক্ষক"), 245),
            LexiconEntry("sikkha", listOf("শিক্ষা", "শিক্ষার"), 240),
            LexiconEntry("shikkhok", listOf("শিক্ষক", "শিক্ষিকা"), 235),
            LexiconEntry("porikkha", listOf("পরীক্ষা", "পরীক্ষার", "পরীক্ষায়"), 245),
            LexiconEntry("kkhoma", listOf("ক্ষমা", "ক্ষমার"), 230),
            LexiconEntry("khoma", listOf("ক্ষমা", "ক্ষমার"), 235),
            LexiconEntry("kkhoti", listOf("ক্ষতি", "ক্ষতির"), 230),
            LexiconEntry("khoti", listOf("ক্ষতি", "ক্ষতির"), 235),
            LexiconEntry("lokkho", listOf("লক্ষ্য", "লক্ষ"), 235),
            LexiconEntry("apekkha", listOf("অপেক্ষা", "অপেক্ষায়"), 230),
            LexiconEntry("opekkha", listOf("অপেক্ষা", "অপেক্ষায়"), 235),
            LexiconEntry("rokkha", listOf("রক্ষা", "রক্ষার"), 225),

            // জ্ঞ (gg / gyan / jn)
            LexiconEntry("ggan", listOf("জ্ঞান", "জ্ঞানের", "জ্ঞানী"), 240),
            LexiconEntry("gyan", listOf("জ্ঞান", "জ্ঞানের", "জ্ঞানী"), 245),
            LexiconEntry("biggan", listOf("বিজ্ঞান", "বিজ্ঞানের", "বিজ্ঞানী"), 245),
            LexiconEntry("bigyan", listOf("বিজ্ঞান", "বিজ্ঞানের", "বিজ্ঞানী"), 245),
            LexiconEntry("obhiggo", listOf("অভিজ্ঞ", "অভিজ্ঞতা"), 230),
            LexiconEntry("obhiggota", listOf("অভিজ্ঞতা", "অভিজ্ঞ"), 235),
            LexiconEntry("kritoggo", listOf("কৃতজ্ঞ", "কৃতজ্ঞতা"), 230),
            LexiconEntry("jiggasha", listOf("জিজ্ঞাসা", "জিজ্ঞেস"), 230),
            LexiconEntry("jiggesh", listOf("জিজ্ঞেস", "জিজ্ঞাসা"), 230),

            // ত্র (tr)
            LexiconEntry("chatro", listOf("ছাত্র", "ছাত্রছাত্রী", "ছাত্রী"), 240),
            LexiconEntry("chatri", listOf("ছাত্রী", "ছাত্র"), 235),
            LexiconEntry("potro", listOf("পত্র", "পত্রিকা"), 230),
            LexiconEntry("potrika", listOf("পত্রিকা", "পত্রিকার"), 235),
            LexiconEntry("mitro", listOf("মিত্র"), 220),
            LexiconEntry("pobitro", listOf("পবিত্র"), 230),
            LexiconEntry("chitro", listOf("চিত্র", "চরিত্র"), 225),
            LexiconEntry("choritro", listOf("চরিত্র", "চরিত্রের"), 230),
            LexiconEntry("matro", listOf("মাত্র", "মাত্রই"), 235),

            // দ্ধ (ddh)
            LexiconEntry("juddho", listOf("যুদ্ধ", "যুদ্ধের"), 235),
            LexiconEntry("buddhi", listOf("বুদ্ধি", "বুদ্ধিমান"), 240),
            LexiconEntry("buddho", listOf("বুদ্ধ", "বুদ্ধি"), 225),
            LexiconEntry("shuddho", listOf("শুদ্ধ", "বিশুদ্ধ"), 230),
            LexiconEntry("suddho", listOf("শুদ্ধ", "বিশুদ্ধ"), 225),
            LexiconEntry("briddhi", listOf("বৃদ্ধি", "বৃদ্ধ"), 230),
            LexiconEntry("shomriddhi", listOf("সমৃদ্ধি", "সমৃদ্ধ"), 225),
            LexiconEntry("boddho", listOf("বদ্ধ", "বদ্ধমূল"), 225),

            // দ্য (dy)
            LexiconEntry("bidya", listOf("বিদ্যা", "বিদ্যালয়"), 235),
            LexiconEntry("biddaloy", listOf("বিদ্যালয়", "বিদ্যালয়ে"), 235),
            LexiconEntry("bidyaloy", listOf("বিদ্যালয়", "বিদ্যালয়ে"), 235),
            LexiconEntry("uddog", listOf("উদ্যোগ", "উদ্যোক্তা"), 230),
            LexiconEntry("udyog", listOf("উদ্যোগ", "উদ্যোক্তা"), 235),
            LexiconEntry("uddesho", listOf("উদ্দেশ্য", "উদ্দেশ্যে"), 230),
            LexiconEntry("khaddo", listOf("খাদ্য", "খাদ্যের"), 225),
            LexiconEntry("khadya", listOf("খাদ্য", "খাদ্যের"), 225),

            // ন্দ (nd)
            LexiconEntry("shundor", listOf("সুন্দর", "সুন্দরী", "সৌন্দর্য"), 250),
            LexiconEntry("sundor", listOf("সুন্দর", "সুন্দরী", "সৌন্দর্য"), 250),
            LexiconEntry("anondo", listOf("আনন্দ", "আনন্দিত", "আনন্দের"), 245),
            LexiconEntry("pochondo", listOf("পছন্দ", "পছন্দের"), 245),
            LexiconEntry("poshondo", listOf("পছন্দ", "পছন্দের"), 235),
            LexiconEntry("chondo", listOf("ছন্দ", "চন্দন"), 220),
            LexiconEntry("bondho", listOf("বন্ধ", "বন্ধু"), 240),
            LexiconEntry("mondir", listOf("মন্দির", "মন্দিরে"), 225),

            // ন্ধ (ndh)
            LexiconEntry("bondhu", listOf("বন্ধু", "বন্ধুরা", "বন্ধুত্ব", "বন্ধুর"), 250),
            LexiconEntry("bondhutto", listOf("বন্ধুত্ব", "বন্ধু"), 235),
            LexiconEntry("shondha", listOf("সন্ধ্যা", "সন্ধ্যায়"), 235),
            LexiconEntry("sondha", listOf("সন্ধ্যা", "সন্ধ্যায়"), 235),
            LexiconEntry("sondhya", listOf("সন্ধ্যা", "সন্ধ্যায়"), 240),
            LexiconEntry("ondhokar", listOf("অন্ধকার", "অন্ধকারে"), 230),
            LexiconEntry("gondho", listOf("গন্ধ", "সুগন্ধ"), 220),
            LexiconEntry("shondhan", listOf("সন্ধান"), 220),

            // ম্প (mp)
            LexiconEntry("shomporko", listOf("সম্পর্ক", "সম্পর্কে", "সম্পর্কের"), 245),
            LexiconEntry("somporko", listOf("সম্পর্ক", "সম্পর্কে", "সম্পর্কের"), 245),
            LexiconEntry("shomporke", listOf("সম্পর্কে", "সম্পর্ক"), 240),
            LexiconEntry("somporke", listOf("সম্পর্কে", "সম্পর্ক"), 240),
            LexiconEntry("shompurno", listOf("সম্পূর্ণ", "সম্পূর্ণরূপে"), 240),
            LexiconEntry("sompurno", listOf("সম্পূর্ণ", "সম্পূর্ণরূপে"), 240),
            LexiconEntry("shompod", listOf("সম্পদ", "সম্পত্তি"), 225),
            LexiconEntry("kompiutar", listOf("কম্পিউটার"), 225),

            // ষ্ট (sht / st)
            LexiconEntry("bristi", listOf("বৃষ্টি", "বৃষ্টির", "বৃষ্টিতে"), 245),
            LexiconEntry("brishti", listOf("বৃষ্টি", "বৃষ্টির", "বৃষ্টিতে"), 245),
            LexiconEntry("misti", listOf("মিষ্টি", "মিষ্টির"), 240),
            LexiconEntry("mishti", listOf("মিষ্টি", "মিষ্টির"), 240),
            LexiconEntry("chesta", listOf("চেষ্টা", "চেষ্টার"), 240),
            LexiconEntry("cheshta", listOf("চেষ্টা", "চেষ্টার"), 240),
            LexiconEntry("kosto", listOf("কষ্ট", "কষ্টের"), 245),
            LexiconEntry("koshto", listOf("কষ্ট", "কষ্টের"), 245),
            LexiconEntry("nosto", listOf("নষ্ট"), 230),
            LexiconEntry("noshto", listOf("নষ্ট"), 230),
            LexiconEntry("sposto", listOf("স্পষ্ট"), 225),
            LexiconEntry("sristi", listOf("সৃষ্টি", "সৃষ্টির"), 230),
            LexiconEntry("dristi", listOf("দৃষ্টি", "দৃষ্টির"), 230),

            // স্থ (sth)
            LexiconEntry("shastho", listOf("স্বাস্থ্য", "স্বাস্থ্যের"), 240),
            LexiconEntry("swasthyo", listOf("স্বাস্থ্য", "স্বাস্থ্যের"), 240),
            LexiconEntry("bebostha", listOf("ব্যবস্থা", "ব্যবস্থাপনা"), 245),
            LexiconEntry("byabostha", listOf("ব্যবস্থা", "ব্যবস্থাপনা"), 245),
            LexiconEntry("obostha", listOf("অবস্থা", "অবস্থায়", "অবস্থান"), 245),
            LexiconEntry("obosthan", listOf("অবস্থান", "অবস্থা"), 235),
            LexiconEntry("sthan", listOf("স্থান", "স্থানে", "স্থানীয়"), 240),
            LexiconEntry("sthayi", listOf("স্থায়ী", "স্থায়ী"), 230),
            LexiconEntry("uposthit", listOf("উপস্থিত", "উপস্থিতি"), 235),
            LexiconEntry("shustho", listOf("সুস্থ", "সুস্থতা"), 240),
            LexiconEntry("sustho", listOf("সুস্থ", "সুস্থতা"), 240),

            // স্ক (sk)
            LexiconEntry("nomoshkar", listOf("নমস্কার"), 240),
            LexiconEntry("nomoskar", listOf("নমস্কার"), 240),
            LexiconEntry("shongskriti", listOf("সংস্কৃতি", "সাংস্কৃতিক"), 235),
            LexiconEntry("sanskriti", listOf("সংস্কৃতি", "সাংস্কৃতিক"), 235),
            LexiconEntry("puroshkar", listOf("পুরস্কার", "পুরস্কারের"), 235),
            LexiconEntry("porishkar", listOf("পরিষ্কার"), 235),
            LexiconEntry("abishkar", listOf("আবিষ্কার"), 230),
            LexiconEntry("skul", listOf("স্কুল", "স্কুলে"), 230),

            // স্ত (st)
            LexiconEntry("rasta", listOf("রাস্তা", "রাস্তায়", "রাস্তার"), 245),
            LexiconEntry("rastay", listOf("রাস্তায়", "রাস্তায়", "রাস্তা"), 235),
            LexiconEntry("bostu", listOf("বস্তু", "বস্তুর"), 230),
            LexiconEntry("besto", listOf("ব্যস্ত", "ব্যস্ততা"), 240),
            LexiconEntry("byasto", listOf("ব্যস্ত", "ব্যস্ততা"), 240),
            LexiconEntry("shosta", listOf("সস্তা", "সস্তায়"), 225),
            LexiconEntry("sosta", listOf("সস্তা", "সস্তায়"), 225),
            LexiconEntry("prostab", listOf("প্রস্তাব"), 225),
            LexiconEntry("prostut", listOf("প্রস্তুত", "প্রস্তুতি"), 235),
            LexiconEntry("bisshosto", listOf("বিশ্বস্ত"), 225),

            // ন্ত (nt)
            LexiconEntry("kintu", listOf("কিন্তু", "কিন্তুই"), 250),
            LexiconEntry("shanto", listOf("শান্ত", "শান্তি"), 240),
            LexiconEntry("shanti", listOf("শান্তি", "শান্তির", "শান্ত"), 245),
            LexiconEntry("santi", listOf("শান্তি", "শান্ত"), 235),
            LexiconEntry("chinta", listOf("চিন্তা", "চিন্তার"), 245),
            LexiconEntry("anonto", listOf("অনন্ত"), 225),
            LexiconEntry("pranto", listOf("প্রান্ত", "প্রান্তে"), 225),
            LexiconEntry("shiddhanto", listOf("সিদ্ধান্ত"), 235),
            LexiconEntry("siddhanto", listOf("সিদ্ধান্ত"), 235),
            LexiconEntry("antorikota", listOf("আন্তরিকতা", "আন্তরিক"), 230),
            LexiconEntry("antorik", listOf("আন্তরিক", "আন্তরিকতা"), 230),

            // ত্ত (tt)
            LexiconEntry("uttor", listOf("উত্তর", "উত্তরে"), 245),
            LexiconEntry("shotti", listOf("সত্যি", "সত্যিই", "সত্য"), 245),
            LexiconEntry("sotti", listOf("সত্যি", "সত্যিই", "সত্য"), 240),
            LexiconEntry("gurutto", listOf("গুরুত্ব", "গুরুত্বপূর্ণ"), 240),
            LexiconEntry("guruttopurno", listOf("গুরুত্বপূর্ণ", "গুরুত্ব"), 245),
            LexiconEntry("chitto", listOf("চিত্ত", "চিত্তাকর্ষক"), 220),
            LexiconEntry("bitti", listOf("ভিত্তি", "বৃত্তি"), 220),
            LexiconEntry("bhitti", listOf("ভিত্তি", "ভিত্তিতে"), 230),

            // দ্ম (dm)
            LexiconEntry("podmo", listOf("পদ্ম", "পদ্মা"), 235),
            LexiconEntry("poddo", listOf("পদ্ম", "পদ্য"), 235),
            LexiconEntry("podma", listOf("পদ্মা", "পদ্ম"), 235),
            LexiconEntry("chodmobesh", listOf("ছদ্মবেশ", "ছদ্মবেশী"), 225),
            LexiconEntry("choddo", listOf("ছদ্ম", "চৌদ্দ"), 220),

            // হ্ন (hn)
            LexiconEntry("chihno", listOf("চিহ্ন", "চিহ্নিত"), 240),
            LexiconEntry("chinno", listOf("চিহ্ন", "চিহ্নিত"), 235),
            LexiconEntry("moddhahno", listOf("মধ্যাহ্ন", "মধ্যাহ্নভোজ"), 230),
            LexiconEntry("oporahno", listOf("অপরাহ্ন"), 225),
            LexiconEntry("purbahno", listOf("পূর্বাহ্ন"), 220),

            // ল্প (lp)
            LexiconEntry("golpo", listOf("গল্প", "গল্পের", "গল্পটি"), 245),
            LexiconEntry("kolpona", listOf("কল্পনা", "কল্পনার"), 240),
            LexiconEntry("prokolpo", listOf("প্রকল্প", "প্রকল্পের"), 240),
            LexiconEntry("olpo", listOf("অল্প", "অল্পই"), 240),
            LexiconEntry("bikolpo", listOf("বিকল্প"), 230),
            LexiconEntry("shilpo", listOf("শিল্প", "শিল্পী"), 235),

            // =========================================================================
            // 3. PRONOUNS, POSSESSIVES, GREETINGS & COMMON CONVERSATIONAL WORDS
            // =========================================================================
            LexiconEntry("ami", listOf("আমি", "আমিই", "আমির", "অমি"), 255),
            LexiconEntry("amii", listOf("আমিই", "আমি"), 195),
            LexiconEntry("amra", listOf("আমরা", "আমরাই", "আমাদের"), 235),
            LexiconEntry("amar", listOf("আমার", "আমারই", "আমাদের"), 255),
            LexiconEntry("amader", listOf("আমাদের", "আমাদেরই"), 225),
            LexiconEntry("amake", listOf("আমাকে", "আমাকেই"), 215),
            LexiconEntry("tumi", listOf("তুমি", "তুমিই", "তুমিতো"), 255),
            LexiconEntry("tomra", listOf("তোমরা", "তোমরাই"), 215),
            LexiconEntry("tomar", listOf("তোমার", "তোমারই", "তোমাদের"), 245),
            LexiconEntry("tomader", listOf("তোমাদের", "তোমাদেরই"), 205),
            LexiconEntry("tomake", listOf("তোমাকে", "তোমাকেই"), 225),
            LexiconEntry("apni", listOf("আপনি", "আপনিই", "আপনার"), 250),
            LexiconEntry("apnara", listOf("আপনারা", "আপনারাই"), 215),
            LexiconEntry("apnar", listOf("আপনার", "আপনারই", "আপনাদের"), 245),
            LexiconEntry("apnake", listOf("আপনাকে", "আপনাকেই"), 220),
            LexiconEntry("she", listOf("সে", "সেই", "শে"), 205),
            LexiconEntry("se", listOf("সে", "সেই"), 215),
            LexiconEntry("tara", listOf("তারা", "তারাই"), 205),
            LexiconEntry("tar", listOf("তার", "তারই", "তাঁর"), 215),
            LexiconEntry("tader", listOf("তাদের", "তাদেরই"), 200),
            LexiconEntry("tui", listOf("তুই", "তুইই"), 195),
            LexiconEntry("tor", listOf("তোর", "তোরই"), 190),
            LexiconEntry("toke", listOf("তোকে", "তোকেই"), 190),
            LexiconEntry("nijer", listOf("নিজের", "নিজেরই"), 195),
            LexiconEntry("nije", listOf("নিজে", "নিজেই"), 195),

            // Identity, Names, Surnames & Greetings
            LexiconEntry("nam", listOf("নাম", "নামে", "নামই"), 250),
            LexiconEntry("naam", listOf("নাম", "নামে", "নামই"), 245),
            LexiconEntry("suvash", listOf("সুভাষ", "সুবাশ", "সুভাস"), 255),
            LexiconEntry("subhash", listOf("সুভাষ", "সুভাস", "সুবাশ"), 250),
            LexiconEntry("suvas", listOf("সুভাষ", "সুভাস", "সুবাশ"), 240),
            LexiconEntry("lal", listOf("লাল", "লালের"), 230),
            LexiconEntry("kemon", listOf("কেমন", "কেমনই", "কেমনে"), 255),
            LexiconEntry("kmn", listOf("কেমন", "কেমনে"), 215),
            LexiconEntry("acho", listOf("আছো", "আছ", "আছোই"), 250),
            LexiconEntry("achen", listOf("আছেন", "আছেনই"), 250),
            LexiconEntry("achi", listOf("আছি", "আছিই"), 250),
            LexiconEntry("ache", listOf("আছে", "আছেই"), 245),
            LexiconEntry("bhaloi", listOf("ভালোই", "ভালো"), 215),
            LexiconEntry("bhalobashi", listOf("ভালোবাসি", "ভালবাসি", "ভালোবাসা"), 255),
            LexiconEntry("valobashi", listOf("ভালোবাসি", "ভালবাসি", "ভালোবাসা"), 250),
            LexiconEntry("bhalobasha", listOf("ভালোবাসা", "ভালোবাসার", "ভালোবাসি"), 245),
            LexiconEntry("valobasha", listOf("ভালোবাসা", "ভালোবাসার", "ভালোবাসি"), 240),
            LexiconEntry("bangladesh", listOf("বাংলাদেশ", "বাংলাদেশে", "বাংলাদেশের"), 255),
            LexiconEntry("bangla", listOf("বাংলা", "বাংলায়", "বাংলার"), 255),
            LexiconEntry("bangali", listOf("বাঙালি", "বাঙ্গালী", "বাঙালির"), 225),
            LexiconEntry("dhonnobad", listOf("ধন্যবাদ", "ধন্যবাদই"), 245),
            LexiconEntry("dhonyobad", listOf("ধন্যবাদ"), 240),
            LexiconEntry("shuvo", listOf("শুভ", "শুভো"), 235),
            LexiconEntry("shubho", listOf("শুভ", "শুভো"), 235),
            LexiconEntry("shokal", listOf("সকাল", "সকালে", "শকাল"), 230),
            LexiconEntry("sokal", listOf("সকাল", "সকালে"), 230),
            LexiconEntry("dupur", listOf("দুপুর", "দুপুরে"), 205),
            LexiconEntry("bikal", listOf("বিকাল", "বিকালে", "বিকেল"), 210),
            LexiconEntry("ratri", listOf("রাত্রি", "রাত", "রাতে"), 220),
            LexiconEntry("rat", listOf("রাত", "রাতে", "রাত্রি"), 225),
            LexiconEntry("shuveccha", listOf("শুভেচ্ছা", "শুভেচ্ছান্তে"), 220),
            LexiconEntry("shagotom", listOf("স্বাগতম", "স্বাগত"), 235),
            LexiconEntry("abhinandan", listOf("অভিনন্দন"), 215),
            LexiconEntry("obhinondon", listOf("অভিনন্দন"), 215),
            LexiconEntry("salam", listOf("সালাম", "আসসালামু"), 220),

            // Question Words & Connectors
            LexiconEntry("ki", listOf("কি", "কী", "কিই"), 245),
            LexiconEntry("kii", listOf("কী", "কি"), 235),
            LexiconEntry("keno", listOf("কেন", "কেনো", "কেনই"), 235),
            LexiconEntry("kothay", listOf("কোথায়", "কোথায়", "কথায়"), 235),
            LexiconEntry("kobe", listOf("কবে", "কবেই"), 220),
            LexiconEntry("kokhon", listOf("কখন", "কখনো", "কখনই"), 225),
            LexiconEntry("kibhabe", listOf("কিভাবে", "কীভাবে"), 225),
            LexiconEntry("kivabe", listOf("কিভাবে", "কীভাবে"), 225),
            LexiconEntry("ke", listOf("কে", "কেউ", "কেই"), 220),
            LexiconEntry("keu", listOf("কেউ", "কেউই"), 215),
            LexiconEntry("koto", listOf("কত", "কতো"), 220),
            LexiconEntry("kon", listOf("কোন", "কোনো"), 215),
            LexiconEntry("kono", listOf("কোনো", "কোন"), 220),
            LexiconEntry("kichu", listOf("কিছু", "কিছুই"), 230),
            LexiconEntry("ebong", listOf("এবং"), 235),
            LexiconEntry("ar", listOf("আর", "আরও", "আরো"), 240),
            LexiconEntry("aro", listOf("আরও", "আরো", "আর"), 230),
            LexiconEntry("ba", listOf("বা", "বাপ"), 215),
            LexiconEntry("e", listOf("এ", "এই"), 225),
            LexiconEntry("jodi", listOf("যদি", "যদিও"), 230),
            LexiconEntry("tahole", listOf("তাহলে", "তাহলেই"), 230),
            LexiconEntry("karon", listOf("কারণ", "কারন"), 225),
            LexiconEntry("jonno", listOf("জন্য", "জন্যে", "জন্যই"), 240),
            LexiconEntry("jonyo", listOf("জন্য", "জন্যে"), 230),
            LexiconEntry("theke", listOf("থেকে", "থেকেই"), 240),
            LexiconEntry("shathe", listOf("সাথে", "সাথেই"), 235),
            LexiconEntry("sathe", listOf("সাথে", "সাথেই"), 235),
            LexiconEntry("moddhe", listOf("মধ্যে", "মধ্যেই"), 225),
            LexiconEntry("upor", listOf("উপর", "উপরে"), 215),
            LexiconEntry("niche", listOf("নিচে", "নীচে"), 210),
            LexiconEntry("kachhe", listOf("কাছে", "কাছেই"), 220),
            LexiconEntry("kache", listOf("কাছে", "কাছেই"), 225),
            LexiconEntry("chara", listOf("ছাড়া", "ছাড়া"), 210),

            // High-Frequency Verbs & Everyday Banglish Verb Forms
            LexiconEntry("koro", listOf("করো", "কর", "করোই"), 235),
            LexiconEntry("koren", listOf("করেন", "করুন"), 230),
            LexiconEntry("korun", listOf("করুন", "করেন"), 230),
            LexiconEntry("kori", listOf("করি", "করিই"), 235),
            LexiconEntry("korbo", listOf("করবো", "করব"), 235),
            LexiconEntry("korechi", listOf("করেছি", "করেছিই"), 230),
            LexiconEntry("koresi", listOf("করেছি", "করছি"), 230),
            LexiconEntry("kore", listOf("করে", "করেই", "করেছে"), 235),
            LexiconEntry("kora", listOf("করা", "করার"), 225),
            LexiconEntry("jabo", listOf("যাব", "যাবো", "যাবোই"), 245),
            LexiconEntry("jai", listOf("যাই", "যায়"), 230),
            LexiconEntry("jacchi", listOf("যাচ্ছি", "যাচ্ছিই"), 225),
            LexiconEntry("jassi", listOf("যাচ্ছি", "যাই"), 220),
            LexiconEntry("jao", listOf("যাও", "যাওয়া"), 225),
            LexiconEntry("jan", listOf("যান", "জান"), 220),
            LexiconEntry("asho", listOf("আসো", "আস"), 225),
            LexiconEntry("ashun", listOf("আসুন", "আসেন"), 225),
            LexiconEntry("ashbo", listOf("আসবো", "আসব"), 225),
            LexiconEntry("ashchi", listOf("আসছি", "আসছিই"), 225),
            LexiconEntry("ashtesi", listOf("আসতেছি", "আসছি"), 220),
            LexiconEntry("eshechi", listOf("এসেছি", "এসেছিই"), 220),
            LexiconEntry("bolo", listOf("বলো", "বল", "বলোই"), 230),
            LexiconEntry("bolun", listOf("বলুন", "বলেন"), 230),
            LexiconEntry("boli", listOf("বলি", "বলিই"), 225),
            LexiconEntry("bolchi", listOf("বলছি", "বলছিই"), 225),
            LexiconEntry("bolsi", listOf("বলছি", "বলেছি"), 225),
            LexiconEntry("dekho", listOf("দেখো", "দেখ"), 225),
            LexiconEntry("dekhun", listOf("দেখুন", "দেখেন"), 225),
            LexiconEntry("dekhi", listOf("দেখি", "দেখিই"), 225),
            LexiconEntry("dekhsi", listOf("দেখছি", "দেখেছি"), 225),
            LexiconEntry("dekhchi", listOf("দেখছি", "দেখেছি"), 225),
            LexiconEntry("dekhbo", listOf("দেখবো", "দেখব"), 220),
            LexiconEntry("jani", listOf("জানি", "জানিই", "জানা"), 235),
            LexiconEntry("janina", listOf("জানিনা", "জানি না"), 225),
            LexiconEntry("paro", listOf("পারো", "পার"), 215),
            LexiconEntry("pari", listOf("পারি", "পারিই"), 220),
            LexiconEntry("parbo", listOf("পারবো", "পারব"), 220),
            LexiconEntry("chai", listOf("চাই", "চাইই", "চায়"), 230),
            LexiconEntry("chao", listOf("চাও", "চাওয়া"), 215),
            LexiconEntry("hobe", listOf("হবে", "হবেই"), 240),
            LexiconEntry("hoy", listOf("হয়", "হয়", "হয়ে"), 235),
            LexiconEntry("hoyeche", listOf("হয়েছে", "হয়েছে"), 230),
            LexiconEntry("hoise", listOf("হয়েছে", "হইছে"), 225),
            LexiconEntry("holo", listOf("হলো", "হল"), 230),
            LexiconEntry("thako", listOf("থাকো", "থাক"), 225),
            LexiconEntry("thakun", listOf("থাকুন", "থাকেন"), 225),
            LexiconEntry("thakbo", listOf("থাকবো", "থাকব"), 220),
            LexiconEntry("khabo", listOf("খাবো", "খাব"), 220),
            LexiconEntry("khacchi", listOf("খাচ্ছি", "খাই"), 220),
            LexiconEntry("khaisi", listOf("খেয়েছি", "খাইছি"), 220),
            LexiconEntry("kheyechi", listOf("খেয়েছি", "খেয়েছি"), 215),
            LexiconEntry("likho", listOf("লিখো", "লেখো"), 210),
            LexiconEntry("likhchi", listOf("লিখছি", "লেখছি"), 210),
            LexiconEntry("likhsi", listOf("লিখছি", "লিখেছি"), 210),
            LexiconEntry("poro", listOf("পড়ো", "পরো", "পড়"), 210),
            LexiconEntry("porsi", listOf("পড়ছি", "পড়েছি"), 210),
            LexiconEntry("shuno", listOf("শুনো", "শোনো"), 215),
            LexiconEntry("shunun", listOf("শুনুন", "শোনেন"), 215),
            LexiconEntry("bujhechi", listOf("বুঝেছি", "বুঝেছিই"), 215),
            LexiconEntry("bujhsi", listOf("বুঝেছি", "বুঝছি"), 215),

            // Time, Places, Daily Life & Nouns
            LexiconEntry("aj", listOf("আজ", "আজকে", "আজই"), 240),
            LexiconEntry("ajke", listOf("আজকে", "আজকেই", "আজ"), 240),
            LexiconEntry("kal", listOf("কাল", "কালকে"), 230),
            LexiconEntry("kalke", listOf("কালকে", "কালকেই", "কাল"), 230),
            LexiconEntry("agamikal", listOf("আগামীকাল", "আগামী"), 235),
            LexiconEntry("ekhon", listOf("এখন", "এখনই", "এখনো"), 245),
            LexiconEntry("ekhono", listOf("এখনো", "এখনও", "এখন"), 230),
            LexiconEntry("pore", listOf("পরে", "পরেই", "পড়ে"), 225),
            LexiconEntry("age", listOf("আগে", "আগেই"), 225),
            LexiconEntry("shomoy", listOf("সময়", "সময়", "সময়ে"), 235),
            LexiconEntry("somoy", listOf("সময়", "সময়", "সময়ে"), 235),
            LexiconEntry("din", listOf("দিন", "দিনে", "দিনই"), 225),
            LexiconEntry("bochor", listOf("বছর", "বছরে"), 220),
            LexiconEntry("mash", listOf("মাস", "মাসে"), 215),
            LexiconEntry("shoptaho", listOf("সপ্তাহ", "সপ্তাহে"), 210),
            LexiconEntry("bari", listOf("বাড়ি", "বাড়ি", "বাড়িতে"), 235),
            LexiconEntry("barite", listOf("বাড়িতে", "বাড়িতে", "বাড়ি"), 230),
            LexiconEntry("basay", listOf("বাসায়", "বাসায়", "বাসা"), 225),
            LexiconEntry("basa", listOf("বাসা", "বাসায়"), 220),
            LexiconEntry("desh", listOf("দেশ", "দেশে", "দেশের"), 235),
            LexiconEntry("deshe", listOf("দেশে", "দেশের", "দেশ"), 225),
            LexiconEntry("shohor", listOf("শহর", "শহরে"), 215),
            LexiconEntry("gram", listOf("গ্রাম", "গ্রামে"), 215),
            LexiconEntry("dhaka", listOf("ঢাকা", "ঢাকায়", "ঢাকার"), 235),
            LexiconEntry("kolkata", listOf("কলকাতা", "কলকাতায়"), 225),
            LexiconEntry("sylhet", listOf("সিলেট", "সিলেটে"), 230),
            LexiconEntry("chittagong", listOf("চট্টগ্রাম", "চট্টগ্রামে"), 225),
            LexiconEntry("rajshahi", listOf("রাজশাহী", "রাজশাহীতে"), 220),
            LexiconEntry("khulna", listOf("খুলনা", "খুলনায়"), 220),
            LexiconEntry("barishal", listOf("বরিশাল", "বরিশালে"), 220),
            LexiconEntry("rangpur", listOf("রংপুর", "রংপুরে"), 220),
            LexiconEntry("mymensingh", listOf("ময়মনসিংহ", "ময়মনসিংহ"), 220),
            LexiconEntry("kaj", listOf("কাজ", "কাজে", "কাজের"), 240),
            LexiconEntry("kaje", listOf("কাজে", "কাজের", "কাজ"), 225),
            LexiconEntry("kotha", listOf("কথা", "কথায়", "কথাই"), 240),
            LexiconEntry("manush", listOf("মানুষ", "মানুষের", "মানুষই"), 235),
            LexiconEntry("jibon", listOf("জীবন", "জীবনে", "জীবনের"), 230),
            LexiconEntry("mon", listOf("মন", "মনে", "মনের"), 235),
            LexiconEntry("mone", listOf("মনে", "মনের", "মন"), 235),
            LexiconEntry("khabar", listOf("খাবার", "খাবারের"), 220),
            LexiconEntry("pani", listOf("পানি", "পানির"), 220),
            LexiconEntry("jol", listOf("জল", "জলের"), 215),
            LexiconEntry("cha", listOf("চা", "চায়ের"), 220),
            LexiconEntry("bhat", listOf("ভাত", "ভাতের"), 220),
            LexiconEntry("taka", listOf("টাকা", "টাকার", "টাকায়"), 230),
            LexiconEntry("boi", listOf("বই", "বইটি", "বইয়ের"), 220),
            LexiconEntry("gaan", listOf("গান", "গানের"), 220),
            LexiconEntry("chobi", listOf("ছবি", "ছবির"), 220),
            LexiconEntry("akash", listOf("আকাশ", "আকাশে"), 225),
            LexiconEntry("shurjo", listOf("সূর্য", "সূর্যের"), 225),
            LexiconEntry("surjo", listOf("সূর্য", "সূর্যের"), 225),
            LexiconEntry("nod", listOf("নদ", "নদী"), 215),
            LexiconEntry("nodi", listOf("নদী", "নদীর"), 225),

            // Family & People
            LexiconEntry("ma", listOf("মা", "মায়ের", "মাকে"), 240),
            LexiconEntry("maa", listOf("মা", "মায়ের", "মাকে"), 240),
            LexiconEntry("baba", listOf("বাবা", "বাবার", "বাবাকে"), 240),
            LexiconEntry("bhai", listOf("ভাই", "ভাইয়া", "ভাইয়ের"), 240),
            LexiconEntry("vai", listOf("ভাই", "ভাইয়া", "ভাইয়ের"), 235),
            LexiconEntry("bhaiya", listOf("ভাইয়া", "ভাই"), 225),
            LexiconEntry("bon", listOf("বোন", "বোনের"), 225),
            LexiconEntry("apu", listOf("আপু", "আপুর"), 225),
            LexiconEntry("dada", listOf("দাদা", "দাদার"), 225),
            LexiconEntry("didi", listOf("দিদি", "দিদির"), 225),
            LexiconEntry("poribar", listOf("পরিবার", "পরিবারের"), 225),

            // Adjectives, Adverbs & Negation
            LexiconEntry("na", listOf("না", "নাই", "নয়"), 250),
            LexiconEntry("nai", listOf("নাই", "নেই", "না"), 235),
            LexiconEntry("nei", listOf("নেই", "নাই"), 235),
            LexiconEntry("noy", listOf("নয়", "নয়", "না"), 225),
            LexiconEntry("khub", listOf("খুব", "খুবই"), 245),
            LexiconEntry("khubi", listOf("খুবই", "খুব"), 235),
            LexiconEntry("onek", listOf("অনেক", "অনেকেই", "অনেকের"), 245),
            LexiconEntry("shob", listOf("সব", "সবাই", "সবকিছু"), 240),
            LexiconEntry("sob", listOf("সব", "সবাই", "সবকিছু"), 240),
            LexiconEntry("shobai", listOf("সবাই", "সবাইকে", "সবার"), 240),
            LexiconEntry("sobai", listOf("সবাই", "সবাইকে", "সবার"), 240),
            LexiconEntry("notun", listOf("নতুন", "নতুনই"), 230),
            LexiconEntry("boro", listOf("বড়", "বড়", "বড়ো"), 225),
            LexiconEntry("choto", listOf("ছোট", "ছোটো"), 225),
            LexiconEntry("shohoj", listOf("সহজ", "সহজেই"), 220),
            LexiconEntry("kothin", listOf("কঠিন"), 215),
            LexiconEntry("thik", listOf("ঠিক", "ঠিকই"), 235),
            LexiconEntry("ekdom", listOf("একদম", "একদমই"), 225),
            LexiconEntry("ektu", listOf("একটু", "একটুখানি"), 230),
            LexiconEntry("ekta", listOf("একটা", "একটি"), 235),
            LexiconEntry("ekti", listOf("একটি", "একটা"), 230),
            LexiconEntry("oboshsho", listOf("অবশ্য", "অবশ্যই"), 225),
            LexiconEntry("oboshshoi", listOf("অবশ্যই", "অবশ্য"), 230),
            LexiconEntry("ashole", listOf("আসলে", "আসলেই"), 225),
            LexiconEntry("priyo", listOf("প্রিয়", "প্রিয়"), 230),
            LexiconEntry("kharap", listOf("খারাপ", "খারাপই"), 220),
            LexiconEntry("taratari", listOf("তাড়াতাড়ি", "তাড়াতাড়ি"), 225),
            LexiconEntry("hothat", listOf("হঠাৎ", "হঠাৎই"), 230),

            // =========================================================================
            // 4. BRAND NAMES, APPS, TECH & WORK LOANWORDS WRITTEN IN BENGALI
            // =========================================================================
            LexiconEntry("youtube", listOf("ইউটিউব", "ইউটিউবে", "youtube"), 235),
            LexiconEntry("whatsapp", listOf("হোয়াটসঅ্যাপ", "হোয়াটসঅ্যাপ", "whatsapp"), 235),
            LexiconEntry("messenger", listOf("মেসেঞ্জার", "মেসেঞ্জারে", "messenger"), 235),
            LexiconEntry("google", listOf("গুগল", "গুগলে", "google"), 235),
            LexiconEntry("nagad", listOf("নগদ", "নগদে", "nagad"), 235),
            LexiconEntry("rocket", listOf("রকেট", "rocket"), 220),
            LexiconEntry("grameenphone", listOf("গ্রামীণফোন", "grameenphone"), 225),
            LexiconEntry("android", listOf("অ্যান্ড্রয়েড", "অ্যান্ড্রয়েড", "android"), 230),
            LexiconEntry("clipvault", listOf("ক্লিপভল্ট", "ClipVault"), 235),
            LexiconEntry("office", listOf("অফিস", "অফিসে", "office"), 230),
            LexiconEntry("meeting", listOf("মিটিং", "মিটিংয়ে", "meeting"), 225),
            LexiconEntry("project", listOf("প্রজেক্ট", "প্রকল্প", "project"), 220),
            LexiconEntry("school", listOf("স্কুল", "স্কুলে", "school"), 220),
            LexiconEntry("college", listOf("কলেজ", "কলেজে", "college"), 220),
            LexiconEntry("university", listOf("ইউনিভার্সিটি", "বিশ্ববিদ্যালয়", "university"), 215),
            LexiconEntry("phone", listOf("ফোন", "ফোনে", "phone"), 225),
            LexiconEntry("mobile", listOf("মোবাইল", "মোবাইলে", "mobile"), 225),
            LexiconEntry("message", listOf("মেসেজ", "বার্তা", "message"), 220),
            LexiconEntry("email", listOf("ইমেইল", "মেইল", "email"), 220),
            LexiconEntry("computer", listOf("কম্পিউটার", "computer"), 220),
            LexiconEntry("internet", listOf("ইন্টারনেট", "internet"), 220)
        )
    }

    fun getAllRawLexiconEntries(): List<LexiconEntry> = RAW_BANGLA_LEXICON_ENTRIES

    // Structured offline Banglish -> Bengali ranked dictionary (ordered by frequency & standard spelling preference)
    private val BANGLA_LEXICON: Map<String, LexiconEntry> by lazy {
        RAW_BANGLA_LEXICON_ENTRIES.associateBy { it.romanKey.trim().lowercase() }
    }

    // Bilingual English <-> Bengali semantic & loanword equivalents (Feature 5 & Feature 9)
    private val BILINGUAL_WORD_EQUIVALENTS: Map<String, String> = mapOf(
        "today" to "আজ",
        "tomorrow" to "আগামীকাল",
        "yesterday" to "গতকাল",
        "office" to "অফিস",
        "meeting" to "মিটিং",
        "project" to "প্রজেক্ট",
        "school" to "স্কুল",
        "college" to "কলেজ",
        "university" to "বিশ্ববিদ্যালয়",
        "phone" to "ফোন",
        "mobile" to "মোবাইল",
        "message" to "মেসেজ",
        "email" to "ইমেইল",
        "time" to "সময়",
        "work" to "কাজ",
        "home" to "বাড়ি",
        "friend" to "বন্ধু",
        "good" to "ভালো",
        "thank" to "ধন্যবাদ",
        "thanks" to "ধন্যবাদ",
        "water" to "পানি",
        "book" to "বই",
        "name" to "নাম"
    )

    // Contextual Bangla Next-Word Bigrams (Feature 1)
    private val BANGLA_BIGRAMS: Map<String, List<String>> = mapOf(
        "আমি" to listOf("তুমি", "আজ", "একটি", "ভালো", "যাবো", "এখন"),
        "আমার" to listOf("নাম", "বাড়ি", "দেশ", "কাজ", "মনে"),
        "তোমার" to listOf("নাম", "সাথে", "জন্য", "কথা", "বাড়ি"),
        "আপনার" to listOf("নাম", "সাথে", "জন্য", "কাজ", "শরীর"),
        "কেমন" to listOf("আছো", "আছেন", "আছে", "লাগছে"),
        "আপনি" to listOf("কেমন", "কোথায়", "কি", "ভালো"),
        "তুমি" to listOf("কেমন", "কোথায়", "কি", "আমার"),
        "ভালো" to listOf("আছি", "লাগে", "থাকো", "মানুষ", "কথা"),
        "শুভ" to listOf("সকাল", "রাত্রি", "জন্মদিন", "কামনা", "নববর্ষ"),
        "অনেক" to listOf("ধন্যবাদ", "ভালো", "দিন", "সুন্দর", "কাজ"),
        "খুব" to listOf("ভালো", "সুন্দর", "তাড়াতাড়ি", "খুশি"),
        "আজ" to listOf("অফিস", "বাড়ি", "কাজ", "সকালে", "যাব"),
        "ব্যক্তিগত" to listOf("তথ্য", "কাজ", "জীবন", "মতামত")
    )

    // Contextual English Next-Word Bigrams (Feature 1)
    private val ENGLISH_BIGRAMS: Map<String, List<String>> = mapOf(
        "i" to listOf("am", "will", "have", "can", "need"),
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

    // Common English Words for Prefix Completion, Typo Correction & Mixed-Language Detection
    private val ENGLISH_COMMON_WORDS: List<String> = listOf(
        "i", "am", "is", "are", "was", "were", "be", "been", "my", "me", "we", "us", "he", "she", "it", "his", "her",
        "in", "on", "at", "by", "of", "or", "so", "if", "no", "not", "to",
        "the", "that", "this", "there", "their", "they", "thank", "thanks", "today", "tomorrow", "time", "together",
        "and", "about", "after", "again", "always", "around", "available", "address", "application", "android",
        "because", "before", "better", "between", "business", "brother", "beautiful",
        "can", "Clipboard", "ClipVault", "come", "could", "call", "check", "change", "company", "complete",
        "day", "do", "don't", "does", "down", "during", "different", "document", "developer",
        "email", "every", "everything", "everyone", "example", "experience",
        "for", "from", "first", "Family", "friend", "friends", "found", "future", "following", "file",
        "good", "great", "Going", "give", "group", "general", "government",
        "have", "hello", "help", "here", "home", "house", "however", "happy",
        "important", "information", "into", "interest", "include", "internet",
        "just", "job", "join",
        "keep", "keyboard", "know", "knowledge",
        "like", "look", "love", "little", "language", "location", "looking", "link",
        "make", "many", "more", "most", "morning", "message", "meeting", "mobile", "money", "moment",
        "name", "need", "never", "next", "night", "number", "nothing", "national",
        "office", "only", "other", "online", "order", "official", "okay",
        "people", "phone", "please", "place", "project", "problem", "possible", "personal", "password",
        "question", "quick", "quickly",
        "really", "right", "remember", "receive", "report", "request",
        "should", "some", "something", "someone", "system", "service", "school", "schedule", "support",
        "think", "through", "together", "tonight", "team",
        "under", "understand", "until", "update", "using", "university",
        "very", "version", "video", "value",
        "want", "well", "where", "when", "which", "while", "with", "without", "work", "working", "world", "would",
        "year", "years", "yesterday", "you", "your", "yourself"
    )

    private val ENGLISH_COMMON_SET: Set<String> by lazy {
        ENGLISH_COMMON_WORDS.map { it.lowercase() }.toSet()
    }

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

    // Common Banglish spelling typos mapped to canonical Banglish keys (Fix 6)
    private val BANGLISH_TYPO_CORRECTIONS: Map<String, String> = mapOf(
        "bhalobasi" to "bhalobashi",
        "valobasi" to "valobashi",
        "bangaldesh" to "bangladesh",
        "bnagladesh" to "bangladesh",
        "kemno" to "kemon",
        "kmon" to "kemon",
        "dhonnobadh" to "dhonnobad",
        "sundhor" to "shundor",
        "agamikaal" to "agamikal",
        "manus" to "manush",
        "porikha" to "porikkha",
        "bektigto" to "bektigoto",
        "purkayastho" to "purkaystha"
    )

    // Common Bengali Unicode spelling mistakes mapped to standard Bengali spellings (Fix 6)
    private val BENGALI_UNICODE_SPELLING_CORRECTIONS: Map<String, List<String>> = mapOf(
        "ভালবাসি" to listOf("ভালোবাসি", "ভালবাসা"),
        "ধন্যবধ" to listOf("ধন্যবাদ"),
        "বাংগলাদেশ" to listOf("বাংলাদেশ"),
        "মানুস" to listOf("মানুষ"),
        "পরিক্ষা" to listOf("পরীক্ষা"),
        "ভূল" to listOf("ভুল"),
        "দুর" to listOf("দূর", "দূরে"),
        "শারিরীক" to listOf("শারীরিক"),
        "প্রানী" to listOf("প্রাণী"),
        "কিভাবে" to listOf("কীভাবে", "কিভাবে")
    )

    // Ambiguous Bengali words with useful contextual alternatives (Fix 6)
    private val AMBIGUOUS_BENGALI_ALTERNATIVES: Map<String, List<String>> = mapOf(
        "কি" to listOf("কী", "কি"),
        "কী" to listOf("কি", "কী"),
        "কোন" to listOf("কোনো", "কোন"),
        "কোনো" to listOf("কোন", "কোনো"),
        "ভালো" to listOf("ভাল", "ভালোই"),
        "ভাল" to listOf("ভালো", "ভালোই"),
        "কোথায়" to listOf("কথায়", "কোথায়"),
        "যাব" to listOf("যাবো", "যাবোই"),
        "যাবো" to listOf("যাব", "যাবোই")
    )

    private val COMMON_ENGLISH_LOANWORDS: Set<String> = setOf(
        "today", "tomorrow", "yesterday", "office", "phone", "mobile", "email", "meeting", "project",
        "time", "online", "message", "link", "update", "group", "class", "exam", "sir", "boss",
        "ok", "okay", "yes", "no", "please", "sorry", "thanks", "hello", "hi", "bye", "file", "report", "team"
    )

    /**
     * Exact lookup in the Bangla Phonetic lexicon.
     * When [includeTypoCorrections] is true, also falls back to [BANGLISH_TYPO_CORRECTIONS].
     */
    fun lookupExactBangla(romanWord: String, includeTypoCorrections: Boolean = true): LexiconEntry? {
        val clean = romanWord.trim().lowercase()
        val exact = BANGLA_LEXICON[clean]
        if (exact != null || !includeTypoCorrections) return exact
        return BANGLISH_TYPO_CORRECTIONS[clean]?.let { BANGLA_LEXICON[it] }
    }

    /**
     * Returns true if [romanWord] is a known Banglish spelling typo rather than a canonical lexicon key.
     */
    fun isBanglishTypoKey(romanWord: String): Boolean {
        val clean = romanWord.trim().lowercase()
        return !BANGLA_LEXICON.containsKey(clean) && BANGLISH_TYPO_CORRECTIONS.containsKey(clean)
    }

    /**
     * Returns Banglish typo correction entry if [romanWord] matches a known Banglish typo.
     */
    fun getBanglishTypoCorrection(romanWord: String): LexiconEntry? {
        val clean = romanWord.trim().lowercase()
        val canonicalKey = BANGLISH_TYPO_CORRECTIONS[clean] ?: return null
        return BANGLA_LEXICON[canonicalKey]
    }

    /**
     * Returns Bengali Unicode spelling corrections for a misspelled Bengali script word (Fix 6).
     */
    fun getBengaliUnicodeSpellingCorrections(word: String): List<String> {
        return BENGALI_UNICODE_SPELLING_CORRECTIONS[word.trim()] ?: emptyList()
    }

    /**
     * Returns useful alternatives for ambiguous Bengali words (Fix 6).
     */
    fun getAmbiguousWordAlternatives(word: String): List<String> {
        return AMBIGUOUS_BENGALI_ALTERNATIVES[word.trim()] ?: emptyList()
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
     * Returns true if the given token is a common English word often mixed in Banglish sentences.
     */
    fun isCommonEnglishLoanWord(word: String): Boolean {
        val lower = word.trim().lowercase()
        return COMMON_ENGLISH_LOANWORDS.contains(lower) || BILINGUAL_WORD_EQUIVALENTS.containsKey(lower)
    }

    /**
     * Returns the Bengali semantic/loan equivalent for a common English word (e.g. "tomorrow" -> "আগামীকাল",
     * "office" -> "অফিস", "today" -> "আজ") if known.
     */
    fun getBengaliEquivalentForEnglishWord(englishWord: String): String? {
        return BILINGUAL_WORD_EQUIVALENTS[englishWord.trim().lowercase()]
    }

    /**
     * Returns true if [romanWord] is a known Banglish lexicon word or known Banglish typo.
     */
    fun isKnownBanglishWord(romanWord: String): Boolean {
        val clean = romanWord.trim().lowercase()
        return BANGLA_LEXICON.containsKey(clean) || BANGLISH_TYPO_CORRECTIONS.containsKey(clean)
    }

    /**
     * Checks whether an out-of-lexicon Latin token exhibits English consonant clusters or suffixes
     * so unknown English tokens (e.g. "kubernetes", "cluster", "deploy", "docker") are never
     * transliterated into gibberish (Fix 3).
     */
    private fun looksLikeUnknownEnglishToken(lower: String): Boolean {
        if (lower.length < 3) return false
        if (BANGLA_LEXICON.containsKey(lower) || BANGLISH_TYPO_CORRECTIONS.containsKey(lower)) return false
        val englishPrefixes = listOf(
            "cl", "cr", "dr", "fl", "fr", "gl", "gr", "pl", "pr", "sc", "sk", "sl",
            "sm", "sn", "sp", "st", "sw", "tr", "tw", "wr", "wh", "kn", "de", "re", "pre", "pro", "ku"
        )
        val englishSuffixes = listOf(
            "tion", "sion", "ment", "ight", "ould", "ness", "ful", "less", "able", "ible",
            "ive", "ous", "ing", "ed", "er", "ers", "es", "ly", "oy", "le", "les", "ty",
            "ry", "cy", "sy", "zy", "ck", "ct", "ft", "ld", "lf", "lk", "lm", "lp", "lt",
            "mp", "nk", "nt", "pt", "rd", "rk", "rm", "rn", "rp", "rt", "st", "tes", "ter"
        )
        if (englishPrefixes.any { lower.startsWith(it) } || englishSuffixes.any { lower.endsWith(it) }) {
            return true
        }
        if (lower.contains("qu") || lower.contains("x") || lower.contains("z") || lower.contains("w") || lower.contains("v")) {
            return true
        }
        return false
    }

    /**
     * General offline token classifier for Mixed Language Typing & Smart Language Detection (Part 2 #2, Feature 9 & 11).
     */
    fun classifyToken(rawToken: String): TokenLanguageClassification {
        val trimmed = rawToken.trim()
        if (trimmed.isEmpty()) return TokenLanguageClassification.NUMBER_OR_CODE

        // 1. Check pure Bengali Unicode script (\u0980..\u09FF)
        if (trimmed.any { it.code in 0x0980..0x09FF }) {
            return TokenLanguageClassification.PURE_BENGALI_SCRIPT
        }

        val lower = trimmed.lowercase()

        // 2. Check URLs, emails, handles, or domain names
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.") ||
            lower.contains("@") || lower.endsWith(".com") || lower.endsWith(".org") ||
            lower.endsWith(".net") || lower.endsWith(".io") || lower.contains("/")
        ) {
            return TokenLanguageClassification.URL_OR_EMAIL
        }

        // 3. Check numbers, versions, or alphanumeric codes
        if (trimmed.all { !it.isLetter() } || (trimmed.any { it.isDigit() } && trimmed.any { it.isLetter() })) {
            return TokenLanguageClassification.NUMBER_OR_CODE
        }

        // 4. Check general English words, loanwords, and unknown English tokens (e.g., tomorrow, meeting, office, kubernetes, cluster)
        if (COMMON_ENGLISH_LOANWORDS.contains(lower) ||
            BILINGUAL_WORD_EQUIVALENTS.containsKey(lower) ||
            (ENGLISH_COMMON_SET.contains(lower) && !BANGLA_LEXICON.containsKey(lower)) ||
            ENGLISH_TYPO_CORRECTIONS.containsKey(lower) ||
            looksLikeUnknownEnglishToken(lower)
        ) {
            return TokenLanguageClassification.ENGLISH_WORD
        }

        // 5. Check ambiguous short words that exist in both English and Banglish (e.g., "am", "to")
        if (ENGLISH_COMMON_SET.contains(lower) && BANGLA_LEXICON.containsKey(lower)) {
            return TokenLanguageClassification.AMBIGUOUS_BILINGUAL
        }

        // 6. High-confidence Banglish lexicon or phonetic words
        return TokenLanguageClassification.BANGLISH_WORD
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
                BANGLA_BIGRAMS[clean] ?: listOf("আমি", "তুমি", "আজ", "একটি", "ভালো")
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
