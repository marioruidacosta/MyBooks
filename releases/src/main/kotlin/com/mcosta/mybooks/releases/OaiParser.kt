package com.mcosta.mybooks.releases

import java.io.InputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants.END_ELEMENT
import javax.xml.stream.XMLStreamConstants.START_ELEMENT
import javax.xml.stream.XMLStreamReader

data class OaiPage(val records: List<MarcRecord>, val resumptionToken: String?)

/** An OAI-PMH error other than "no records match" (which is just an empty page). */
class OaiException(message: String) : Exception(message)

/** Reads ListRecords pages in the `marcxchange` format. */
object OaiParser {
    private val factory = XMLInputFactory.newInstance().apply {
        setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true)
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    }

    fun parse(xml: InputStream): OaiPage {
        val r = factory.createXMLStreamReader(xml, "UTF-8")
        val records = mutableListOf<MarcRecord>()
        var token: String? = null
        try {
            while (r.hasNext()) {
                if (r.next() != START_ELEMENT) continue
                when (r.localName) {
                    "error" -> {
                        val code = r.getAttributeValue(null, "code")
                        val text = r.elementText
                        if (code == "noRecordsMatch") return OaiPage(emptyList(), null)
                        throw OaiException("OAI error $code: $text")
                    }
                    "record" -> readRecord(r)?.let(records::add)
                    "resumptionToken" -> token = r.elementText.trim().ifEmpty { null }
                }
            }
        } finally {
            r.close()
        }
        return OaiPage(records, token)
    }

    /** Reads one record, leaving the reader at its end tag; null for deleted records. */
    private fun readRecord(r: XMLStreamReader): MarcRecord? {
        var datestamp = ""
        var deleted = false
        var leader = ""
        val controls = mutableMapOf<String, String>()
        val fields = mutableListOf<DataField>()
        var tag: String? = null
        var subs = mutableListOf<Pair<Char, String>>()
        while (r.hasNext()) {
            when (r.next()) {
                START_ELEMENT -> when (r.localName) {
                    "header" -> deleted = r.getAttributeValue(null, "status") == "deleted"
                    "datestamp" -> datestamp = r.elementText.trim().take(10)
                    "leader" -> leader = r.elementText
                    "controlfield" -> {
                        val t = r.getAttributeValue(null, "tag")
                        controls[t] = r.elementText.trim()
                    }
                    "datafield" -> {
                        tag = r.getAttributeValue(null, "tag")
                        subs = mutableListOf()
                    }
                    "subfield" -> {
                        val code = r.getAttributeValue(null, "code")?.firstOrNull() ?: ' '
                        subs += code to r.elementText
                    }
                }
                END_ELEMENT -> when (r.localName) {
                    "datafield" -> {
                        tag?.let { fields += DataField(it, subs) }
                        tag = null
                    }
                    "record" -> {
                        val id = controls["001"] ?: return null
                        return if (deleted) null else MarcRecord(id, datestamp, leader, controls, fields)
                    }
                }
            }
        }
        return null
    }
}
