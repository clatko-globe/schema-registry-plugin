package com.github.imflog.schema.registry.register

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import org.apache.avro.Schema
import org.assertj.core.api.Assertions
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class RegisterTaskActionTest {
    lateinit var folderRule: TemporaryFolder

    @BeforeEach
    fun setUp() {
        folderRule = TemporaryFolder()
        folderRule.create()
    }

    @AfterEach
    fun tearDown() {
        folderRule.delete()
    }

    @Test
    fun `Should register new schema`() {
        // given

        // TODO: Update README and examples.
        val registryClient = MockSchemaRegistryClient()
        folderRule.newFolder("src", "main", "avro", "external")
        File(folderRule.root, "src/main/avro/external/test.avsc").writeText(
            """
            {"type": "record",
             "name": "test",
             "fields": [
                {"name": "name", "type": "string" },
                {"name": "newField", "type": "string", "default": ""}
             ]
            }
        """.trimIndent()
        )

        val subjects = listOf(
            RegisterSubject("test", "src/main/avro/external/test.avsc", AvroSchema.TYPE, listOf())
        )

        // when
        val errorCount = RegisterTaskAction(
            registryClient,
            subjects,
            folderRule.root
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
    }

    @Test
    fun `Should update version for same schema`() {
        // given
        val parser = Schema.Parser()
        val testSchema =
            parser.parse("{\"type\": \"record\", \"name\": \"test\", \"fields\": [{ \"name\": \"name\", \"type\": \"string\" }]}")
        val registryClient = MockSchemaRegistryClient()
        registryClient.register("test", testSchema)

        folderRule.newFolder("src", "main", "avro", "external")
        File(folderRule.root, "src/main/avro/external/test.avsc").writeText(
            """
            {"type": "record",
             "name": "test",
             "fields": [
                {"name": "name", "type": "string" },
                {"name": "newField", "type": "string", "default": ""}
             ]
            }
        """.trimIndent()
        )

        val subjects = listOf(
            RegisterSubject(
                "test",
                "src/main/avro/external/test.avsc",
                AvroSchema.TYPE,
                listOf()
            )
        )

        // when
        val errorCount = RegisterTaskAction(
            registryClient,
            subjects,
            folderRule.root
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
        Assertions.assertThat(registryClient.getLatestSchemaMetadata("test").version)
            .isEqualTo(2)
    }

    @Test
    internal fun `Should register schema with dependencies in another file`() {
        // given
        val parser = Schema.Parser()
        val testSchema =
            parser.parse("{\"type\": \"record\", \"name\": \"test\", \"fields\": [{ \"name\": \"name\", \"type\": \"string\" }]}")
        val registryClient = MockSchemaRegistryClient()
        registryClient.register("test", testSchema)

        folderRule.newFolder("src", "main", "avro", "external")

        // Register dependency
        registryClient.register(
            "Street",
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "Street",
                    "fields": [
                        {"name": "street", "type": "string" }
                    ]
                }""",
                listOf()
            ).get()
        )

        registryClient.register(
            "Address",
            registryClient.parseSchema(
                AvroSchema.TYPE,
                """{
                    "type": "record",
                    "name": "Address",
                    "fields": [
                        {"name": "city", "type": "string" },
                        {"name": "street", "type": "Street" }
                    ]
                }""",
                listOf(SchemaReference("Street", "Street", 1))
            ).get()
        )

        File(folderRule.root, "src/main/avro/external/test.avsc").writeText(
            """
            {"type": "record",
             "name": "test",
             "fields": [
                {"name": "name", "type": "string" },
                {"name": "address", "type": "Address"}
             ]
            }
        """.trimIndent()
        )


        val subjects = listOf(
            RegisterSubject(
                "test",
                "src/main/avro/external/test.avsc",
                AvroSchema.TYPE,
                listOf(
                    SchemaReference("Address", "Address", 1),
                    SchemaReference("Street", "Street", 1)
                )
            )
        )

        // when
        val errorCount = RegisterTaskAction(
            registryClient,
            subjects,
            folderRule.root
        ).run()

        // then
        Assertions.assertThat(errorCount).isEqualTo(0)
    }
}
