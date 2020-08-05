package com.github.imflog.schema.registry.register

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import org.gradle.api.logging.Logging
import java.io.File


class RegisterTaskAction(
    private val client: SchemaRegistryClient,
    private val subjects: List<RegisterSubject>,
    private val rootDir: File
) {

    private val logger = Logging.getLogger(RegisterTaskAction::class.java)

    fun run(): Int {
        var errorCount = 0
        subjects.forEach { (subject, path, type, dependencies) ->
            try {
                registerSchema(subject, path, type, dependencies)
            } catch (e: Exception) {
                logger.error("Could not register schema for '$subject'", e)
                errorCount++
            }
        }
        return errorCount
    }

    private fun registerSchema(subject: String, path: String, type: String, dependencies: List<SchemaReference>) {
        val schemaString = File(rootDir, path).readText()
        val parsedSchema = client.parseSchema(type, schemaString, dependencies)
        logger.debug("Calling register ($subject, $path)")
        // TODO: Handle optional
        client.register(subject, parsedSchema.get())
    }
}
