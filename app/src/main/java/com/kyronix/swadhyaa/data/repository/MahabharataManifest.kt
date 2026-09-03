package com.kyronix.swadhyaa.data.repository

/** One entry per parba, ported from mahabharata_kaliprasanna/manifest.json in the DB repo. */
data class ParbaInfo(
    val parbaNo: Int,
    val name: String,
    val adhyayCount: Int,
    val upakhyanCount: Int,
    val packFile: String,
    val packSizeBytes: Long
)

object MahabharataManifest {
    val PARBAS: List<ParbaInfo> = listOf(
        ParbaInfo(1, "আদিপর্ব", 233, 321, "mahabharata_parba_1.db.gz", 1425682),
        ParbaInfo(2, "সভাপর্ব", 79, 140, "mahabharata_parba_2.db.gz", 464311),
        ParbaInfo(3, "বনপর্ব", 314, 560, "mahabharata_parba_3.db.gz", 2019095),
        ParbaInfo(4, "বিরাটপর্ব", 72, 105, "mahabharata_parba_4.db.gz", 382003),
        ParbaInfo(5, "উদ্যোগপর্ব", 194, 367, "mahabharata_parba_5.db.gz", 1218896),
        ParbaInfo(6, "ভীষ্মপর্ব", 124, 264, "mahabharata_parba_6.db.gz", 900992),
        ParbaInfo(7, "দ্রোণপর্ব", 203, 416, "mahabharata_parba_7.db.gz", 1463899),
        ParbaInfo(8, "কর্ণপর্ব", 97, 208, "mahabharata_parba_8.db.gz", 795091),
        ParbaInfo(9, "শল্যপর্ব", 45, 96, "mahabharata_parba_9.db.gz", 360586),
        ParbaInfo(10, "সৌপ্তিকপর্ব", 18, 33, "mahabharata_parba_10.db.gz", 130908),
        ParbaInfo(11, "স্ত্রীপর্ব", 27, 37, "mahabharata_parba_11.db.gz", 137168),
        ParbaInfo(12, "শান্তিপর্ব", 366, 624, "mahabharata_parba_12.db.gz", 2476325),
        ParbaInfo(13, "অনুশাসনপর্ব", 168, 332, "mahabharata_parba_13.db.gz", 1254484),
        ParbaInfo(14, "আশ্বমেধিকপর্ব", 92, 148, "mahabharata_parba_14.db.gz", 480184),
        ParbaInfo(15, "আশ্রমবাসিকপর্ব", 39, 57, "mahabharata_parba_15.db.gz", 176662),
        ParbaInfo(16, "মৌসলপর্ব", 8, 27, "mahabharata_parba_16.db.gz", 60956),
        ParbaInfo(17, "মহাপ্রস্থানিকপর্ব", 3, 12, "mahabharata_parba_17.db.gz", 25518),
        ParbaInfo(18, "স্বর্গারোহনপর্ব", 6, 19, "mahabharata_parba_18.db.gz", 60040)
    )
}
