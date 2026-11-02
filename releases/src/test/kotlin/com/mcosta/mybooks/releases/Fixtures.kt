package com.mcosta.mybooks.releases

/** Real OAI-PMH pages from the National Bibliography (October 2026), trimmed to the records the tests need. */
object Fixtures {
    fun stream(name: String) = checkNotNull(Fixtures::class.java.getResourceAsStream("/oai/$name")) { "missing fixture $name" }
    val october: OaiPage by lazy { OaiParser.parse(stream("october.xml")) }
    fun record(id: String): MarcRecord = october.records.single { it.id == id }
}

/** A hand-built record, for cases the real sample doesn't contain. */
fun record(
    id: String = "1",
    datestamp: String = "2026-10-15",
    leader: String = "01174cam  2200277   450 ",
    vararg fields: DataField,
) = MarcRecord(id, datestamp, leader, controls = mapOf("001" to id), fields = fields.toList())

fun field(tag: String, vararg subfields: Pair<Char, String>) = DataField(tag, subfields.toList())
