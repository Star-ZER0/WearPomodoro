package cc.star0.wear.pomodoro.data

import android.content.res.Resources
import cc.star0.wear.pomodoro.R
import org.xmlpull.v1.XmlPullParser

data class Sponsor(val username: String, val amount: String)

/** The bundled, editable list lives in res/xml/sponsors.xml. */
fun loadSponsors(resources: Resources): List<Sponsor> = buildList {
    resources.getXml(R.xml.sponsors).use { parser ->
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "sponsor") {
                val username = parser.getAttributeValue(null, "username").orEmpty().trim()
                val amount = parser.getAttributeValue(null, "amount").orEmpty().trim()
                if (username.isNotEmpty() && amount.isNotEmpty()) add(Sponsor(username, amount))
            }
        }
    }
}
