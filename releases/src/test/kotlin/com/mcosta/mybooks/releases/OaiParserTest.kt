package com.mcosta.mybooks.releases

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OaiParserTest {
    @Test
    fun parsesRecordsAndToken() {
        val page = Fixtures.october
        assertEquals(32, page.records.size)
        val first = page.records.first()
        assertEquals("1154749", first.id)
        assertEquals("2026-10-01", first.datestamp)
        assertEquals("am", first.leader.substring(6, 8))
        assertEquals("1793651319290", page.resumptionToken)
    }

    @Test
    fun noRecordsMatchIsEmpty() {
        val page = OaiParser.parse(Fixtures.stream("no-records.xml"))
        assertTrue(page.records.isEmpty())
        assertEquals(null, page.resumptionToken)
    }

    @Test
    fun otherErrorThrows() {
        val xml = """<?xml version="1.0"?><OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/"><error code="badArgument">bad</error></OAI-PMH>"""
        assertFailsWith<OaiException> { OaiParser.parse(xml.byteInputStream()) }
    }

    @Test
    fun deletedRecordsAreSkipped() {
        val xml = """<?xml version="1.0"?><OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/"><ListRecords>
            <record><header status="deleted"><identifier>oai:x/9</identifier><datestamp>2026-10-01</datestamp></header></record>
            </ListRecords></OAI-PMH>"""
        assertTrue(OaiParser.parse(xml.byteInputStream()).records.isEmpty())
    }
}
