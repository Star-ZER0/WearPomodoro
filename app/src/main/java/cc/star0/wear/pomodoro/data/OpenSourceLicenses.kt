package cc.star0.wear.pomodoro.data

import android.content.res.Resources
import cc.star0.wear.pomodoro.R
import org.xmlpull.v1.XmlPullParser

const val PROJECT_LICENSE_ID = "wearpomodoro"

data class LicenseComponent(
    val id: String,
    val name: String,
    val license: String,
    val file: String,
)

/** License identities and bundled text paths are maintained together in the XML catalog. */
fun loadLicenseComponents(resources: Resources): List<LicenseComponent> = buildList {
    resources.getXml(R.xml.open_source_licenses).use { parser ->
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "component") {
                add(
                    LicenseComponent(
                        id = requireNotNull(parser.getAttributeValue(null, "id")),
                        name = requireNotNull(parser.getAttributeValue(null, "name")),
                        license = requireNotNull(parser.getAttributeValue(null, "license")),
                        file = requireNotNull(parser.getAttributeValue(null, "file")),
                    ),
                )
            }
        }
    }
}
