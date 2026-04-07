package com.najmi.corvus.data.remote.media

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
object ClearbitLogoClient {

    fun logoUrl(domain: String): String =
        "https://logo.clearbit.com/${domain.lowercase().trim()}"

    fun guessOrgDomain(orgName: String): String? =
        KNOWN_ORG_DOMAINS[orgName.lowercase().trim()]

    private val KNOWN_ORG_DOMAINS = mapOf(
        "petronas"               to "petronas.com",
        "petroliam nasional"     to "petronas.com",
        "maybank"                to "maybank.com",
        "malayan banking"        to "maybank.com",
        "cimb"                   to "cimb.com",
        "cimb bank"              to "cimb.com",
        "tenaga nasional"        to "tnb.com.my",
        "tnb"                    to "tnb.com.my",
        "malaysia airlines"      to "malaysiaairlines.com",
        "mas"                    to "malaysiaairlines.com",
        "airasia"                to "airasia.com",
        "air asia"               to "airasia.com",
        "sime darby"             to "simedarby.com",
        "khazanah"               to "khazanah.com.my",
        "khazanah nasional"      to "khazanah.com.my",
        "maxis"                  to "maxis.com.my",
        "celcom"                 to "celcom.com.my",
        "digi"                   to "digi.com.my",
        "telekom malaysia"       to "tm.com.my",
        "tm"                     to "tm.com.my",
        "bank negara"            to "bnm.gov.my",
        "bank negara malaysia"   to "bnm.gov.my",
        "bernama"                to "bernama.com",
        "rtm"                    to "rtm.gov.my",
        "astro"                  to "astro.com.my",
        "rhb"                    to "rhb.com.my",
        "rhb bank"               to "rhb.com.my",
        "public bank"            to "pbebank.com",
        "hong leong bank"        to "hlb.com.my",
        "ioi"                    to "ioigroup.com",
        "ioi group"               to "ioigroup.com",
        "ytl"                    to "ytl.com",
        "ytl corporation"        to "ytl.com",
        "axiata"                 to "axiata.com",
        "mimos"                  to "mimos.my",
        "mdec"                   to "mdec.my",
        "mida"                   to "mida.gov.my",
        "pemandu"                to "pemandu.gov.my",
        "google"                 to "google.com",
        "microsoft"              to "microsoft.com",
        "apple"                  to "apple.com",
        "meta"                   to "meta.com",
        "amazon"                 to "amazon.com",
        "openai"                 to "openai.com",
        "anthropic"              to "anthropic.com",
        "facebook"               to "facebook.com",
        "twitter"                to "twitter.com",
        "x corp"                 to "x.com",
        "tesla"                  to "tesla.com",
        "spacex"                 to "spacex.com",
        "netflix"                to "netflix.com",
        "spotify"                to "spotify.com",
        "cnn"                    to "cnn.com",
        "bbc"                    to "bbc.com",
        "reuters"                to "reuters.com",
        "bloomberg"              to "bloomberg.com",
        "who"                    to "who.int",
        "un"                     to "un.org",
        "unicef"                 to "unicef.org",
        "world bank"             to "worldbank.org",
        "imf"                    to "imf.org",
        "asean"                  to "asean.org"
    )
}
