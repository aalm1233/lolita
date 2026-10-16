package com.lolita.app.domain.model

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class PersonalModelRequestTest {
    private fun request(mode: PersonalModelMode = PersonalModelMode.PARAMETERS,
                        roles: List<PersonalModelPhotoRole> = emptyList()) =
        PersonalModelRequest("我的模特", mode, BodyMeasurements(165.5, 62.5), roles)

    @Test fun parameterModeRejectsPersonalPhotos() {
        PersonalModelPhotoRole.entries.filter { it != PersonalModelPhotoRole.VIRTUAL_FACE }.forEach { role ->
            assertThrows(IllegalArgumentException::class.java) { request(roles = listOf(role)).validate() }
        }
        request().validatePaths(emptyList())
        request(roles = listOf(PersonalModelPhotoRole.VIRTUAL_FACE)).validatePaths(listOf("/virtual.jpg"))
        assertThrows(IllegalArgumentException::class.java) { request().validatePaths(listOf("/hidden-selfie.jpg")) }
    }

    @Test fun photoModeRequiresFaceOrFront() {
        listOf(emptyList(), listOf(PersonalModelPhotoRole.SIDE), listOf(PersonalModelPhotoRole.VIRTUAL_FACE),
            listOf(PersonalModelPhotoRole.FACE, PersonalModelPhotoRole.FACE)).forEach { roles ->
            assertThrows(IllegalArgumentException::class.java) { request(PersonalModelMode.PHOTO, roles).validate() }
        }
        val photo = request(PersonalModelMode.PHOTO, listOf(PersonalModelPhotoRole.SIDE, PersonalModelPhotoRole.FRONT))
        photo.validatePaths(listOf("/side.jpg", "/front.jpg"))
        assertThrows(IllegalArgumentException::class.java) { photo.validatePaths(listOf("/front.jpg")) }
        assertThrows(IllegalArgumentException::class.java) { photo.validatePaths(listOf("/side.jpg", " ")) }
        assertEquals(listOf(PersonalModelPhotoRole.SIDE, PersonalModelPhotoRole.FRONT), PersonalModelRequest.decode(photo.encode()).photoRoles)
    }

    @Test fun optionalMeasurementsStayUnknown() {
        val original = request()
        val decoded = PersonalModelRequest.decode(original.encode())
        assertEquals(original, decoded)
        assertEquals(165.5, decoded.measurements.heightCm, 0.0)
        assertNull(decoded.measurements.bustCm)
        assertNull(decoded.measurements.waistCm)
        assertNull(decoded.measurements.hipCm)
        assertNull(decoded.measurements.shoulderCm)
        assertNull(decoded.measurements.inseamCm)
        val unusual = original.copy(measurements = BodyMeasurements(50.0, 500.0, 350.0, 10.0, 10.0, 120.0, 180.0))
        unusual.validate() // Broad independent bounds, never body-ratio assumptions.
        assertEquals(unusual, PersonalModelRequest.decode(unusual.encode()))
    }

    @Test fun nonFiniteAndInvalidValuesFail() {
        val invalid = listOf(
            BodyMeasurements(Double.NaN, 60.0), BodyMeasurements(165.0, Double.POSITIVE_INFINITY),
            BodyMeasurements(49.9, 60.0), BodyMeasurements(260.1, 60.0),
            BodyMeasurements(165.0, 9.9), BodyMeasurements(165.0, 500.1),
            BodyMeasurements(165.0, 60.0, bustCm = 9.9), BodyMeasurements(165.0, 60.0, waistCm = 350.1),
            BodyMeasurements(165.0, 60.0, hipCm = Double.NEGATIVE_INFINITY),
            BodyMeasurements(165.0, 60.0, shoulderCm = 120.1), BodyMeasurements(165.0, 60.0, inseamCm = 180.1)
        )
        invalid.forEach { measurements ->
            assertThrows(IllegalArgumentException::class.java) { request().copy(measurements = measurements).validate() }
        }
        val json = request().encode()
        listOf("1e999", "\"NaN\"", "null", "49.9", "true").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                PersonalModelRequest.decode(json.replace("165.5", value))
            }
        }
    }

    @Test fun strictCodecRejectsUnrecognizedMissingAndMistypedFields() {
        val json = request().encode()
        val invalid = listOf(
            json.replace("PARAMETERS", "FUTURE"), json.replace("FEMALE", "UNKNOWN"),
            json.replace("\"heightCm\":165.5", "\"heightCm\":\"165.5\""),
            json.replace("\"name\":\"我的模特\"", "\"name\":null"),
            json.replace("\"instructions\":\"\"", "\"instructions\":200"),
            json.replace("\"photoRoles\":[]", "\"photoRoles\":[\"UNKNOWN\"]")
        )
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { PersonalModelRequest.decode(it) } }
        val root = JsonParser.parseString(json).asJsonObject
        root.addProperty("imagePath", "/private.jpg")
        assertThrows(IllegalArgumentException::class.java) { PersonalModelRequest.decode(root.toString()) }
        root.remove("imagePath"); root.remove("measurements")
        assertThrows(IllegalArgumentException::class.java) { PersonalModelRequest.decode(root.toString()) }
        listOf("", " ", " name", "name ", "名".repeat(61)).forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { request().copy(name = name).validate() }
        }
        assertThrows(IllegalArgumentException::class.java) { request().copy(instructions = "字".repeat(201)).validate() }
    }

    @Test fun promptSeparatesLayoutFromIdentity() {
        val photo = request(PersonalModelMode.PHOTO, listOf(PersonalModelPhotoRole.FRONT, PersonalModelPhotoRole.FACE))
            .copy(instructions = "短发")
        val prompt = photo.prompt()
        assertTrue(prompt.contains("layout/pose template"))
        assertTrue(prompt.contains("not a face or body authority"))
        assertTrue(prompt.contains("\"image\":2,\"role\":\"FRONT\""))
        assertTrue(prompt.contains("\"image\":3,\"role\":\"FACE\""))
        assertTrue(prompt.contains("no automatic slimming or leg elongation", ignoreCase = true))
        assertTrue(prompt.contains("165.5"))
        assertTrue(prompt.contains("短发"))
        assertFalse(prompt.contains("我的模特"))
        assertFalse(prompt.contains("bustCm")) // Unknown optional values must not become invented constraints.
        val parameters = request().prompt()
        assertTrue(parameters.contains("fictional ADULT"))
        assertFalse(parameters.contains("VIRTUAL_FACE"))
        val adjustment = request(roles = listOf(PersonalModelPhotoRole.VIRTUAL_FACE)).prompt()
        assertTrue(adjustment.contains("\"image\":2,\"role\":\"VIRTUAL_FACE\""))
    }
}
