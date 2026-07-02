package com.elysium369.meet.data.car2db

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Car2DbApiModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `parses Car2DbTrim full response`() {
        val payload = """
        {
          "@context": "/contexts/Trim",
          "@type": "Trim",
          "@id": "/trims/263119",
          "id": 263119,
          "name": "2.5 AT",
          "slug": "camry-25-at",
          "yearBegin": 2018,
          "yearEnd": 2024,
          "breadcrumbs": {
            "make": {"id": 79, "name": "Toyota", "slug": "toyota"},
            "model": {"id": 1234, "name": "Camry", "slug": "camry"}
          },
          "keySpecifications": {
            "engineVolume": 2.496,
            "power": 200.0,
            "torque": 250.0,
            "transmission": "Automatic",
            "drivetrain": "FWD",
            "fuelType": "Gasoline",
            "bodyType": "Sedan",
            "lengthMm": 4885.0,
            "widthMm": 1840.0,
            "heightMm": 1455.0,
            "wheelbaseMm": 2825.0,
            "curbWeightKg": 1605.0
          },
          "specifications": [
            {
              "category": {"id": 1, "name": "Engine"},
              "items": [
                {"id": 1, "name": "Cylinders", "value": "4", "unit": null},
                {"id": 2, "name": "Valvetrain", "value": "DOHC", "unit": null}
              ]
            }
          ],
          "equipments": []
        }
        """.trimIndent()
        val trim = json.decodeFromString(Car2DbTrim.serializer(), payload)
        assertEquals(263119, trim.id)
        assertEquals("2.5 AT", trim.name)
        assertEquals("Toyota", trim.breadcrumbs?.make?.name)
        assertEquals("Camry", trim.breadcrumbs?.model?.name)
        assertEquals(2.496, trim.keySpecifications?.engineVolume!!, 0.001)
        assertEquals(200.0, trim.keySpecifications?.power!!, 0.001)
        assertEquals(1, trim.specifications.size)
        assertEquals("Engine", trim.specifications[0].category.name)
    }

    @Test
    fun `parses search collection`() {
        val payload = """
        {
          "@context": "/contexts/Trim",
          "@type": "Collection",
          "member": [
            {
              "id": 263119,
              "name": "2.5 AT",
              "yearBegin": 2018,
              "yearEnd": 2024,
              "relevanceScore": 0.95,
              "breadcrumbs": {
                "make": {"id": 79, "name": "Toyota", "slug": "toyota"},
                "model": {"id": 1234, "name": "Camry", "slug": "camry"}
              },
              "keySpecifications": {
                "engineVolume": 2.496,
                "power": 200.0
              }
            }
          ],
          "totalItems": 1,
          "view": {
            "@id": "/search/vehicles?q=Toyota+Camry",
            "first": "/search/vehicles?q=Toyota+Camry",
            "last": "/search/vehicles?q=Toyota+Camry&page=1",
            "next": null
          }
        }
        """.trimIndent()
        val collection = json.decodeFromString(
            Car2DbCollection.serializer(Car2DbSearchTrim.serializer()),
            payload
        )
        assertEquals(1, collection.totalItems)
        assertEquals(1, collection.member.size)
        assertEquals(263119, collection.member.first().id)
        assertEquals(0.95, collection.member.first().relevanceScore, 0.001)
        assertNotNull(collection.member.first().breadcrumbs)
    }

    @Test
    fun `parses error response`() {
        val payload = """
        {
          "type": "/errors/authentication-error",
          "title": "Unauthorized",
          "status": 401,
          "detail": "Invalid or missing API key",
          "instance": "/trims/263119/full"
        }
        """.trimIndent()
        val err = json.decodeFromString(Car2DbError.serializer(), payload)
        assertEquals(401, err.status)
        assertEquals("Unauthorized", err.title)
        assertEquals("Invalid or missing API key", err.detail)
    }

    @Test
    fun `toLookup maps trim to lookup model`() {
        val trim = Car2DbTrim(
            id = 263119,
            name = "2.5 AT",
            yearBegin = 2018,
            yearEnd = 2024,
            breadcrumbs = Car2DbBreadcrumbs(
                make = Car2DbMake(id = 79, name = "Toyota", slug = "toyota"),
                model = Car2DbModel(id = 1234, name = "Camry", slug = "camry")
            ),
            keySpecifications = Car2DbKeySpecs(
                engineVolume = 2.496,
                power = 200.0,
                torque = 250.0,
                transmission = "Automatic",
                lengthMm = 4885.0
            )
        )
        val lookup = trim.toLookup()
        assertEquals(263119, lookup.trimId)
        assertEquals("Toyota", lookup.make)
        assertEquals("Camry", lookup.model)
        assertEquals("2.5 AT", lookup.trimName)
        assertEquals(2.496, lookup.engineDisplacementL!!, 0.001)
        assertEquals(200.0, lookup.powerHp!!, 0.001)
        assertTrue("Provenance must mention Car2DB",
            lookup.provenance.contains("Car2DB"))
    }
}