package com.lolita.app.domain.model

import com.lolita.app.data.local.entity.ItemEvidence
import com.lolita.app.data.local.entity.RecognitionTask
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class RecognitionSharedResourceTest {
    @Test fun distinctRegionsMayShareOneDeduplicatedRestoredFile() {
        val draft=UUID.randomUUID().toString()
        val request=RecognitionRequest(draftId=draft,targetItemId=null,baseline=emptyMap(),
            regions=listOf("a","b").map {RecognitionRegion(it,"0".repeat(64),8,8)})
        RecognitionTask(UUID.randomUUID().toString(),draft,null,request.encode(),listOf("/same.png","/same.png")).validate()
        val candidate=RecognitionCandidate("color",RecognitionField.COLOR,"蓝色",RecognitionSource.VISUAL,listOf("a","b"),"")
        val evidence=ItemEvidence(UUID.randomUUID().toString(),null,draft,RecognitionField.COLOR,
            RecognitionEvidencePayload(taskId=UUID.randomUUID().toString(),appearanceOnly=false,candidate=candidate).encode(),listOf("/same.png","/same.png"))
        assertEquals(candidate,evidence.validateRecognitionEvidence().candidate)
        assertThrows(IllegalArgumentException::class.java) {evidence.copy(regionImagePaths=listOf("/same.png")).validateRecognitionEvidence()}
        assertThrows(IllegalArgumentException::class.java) {request.copy(regions=listOf(request.regions.first(),request.regions.first())).validate()}
    }
    @Test fun evidenceProtocolRequiresNumericVersionAndAStringTaskIdentity() {
        val candidate=RecognitionCandidate("name",RecognitionField.NAME,"原名",RecognitionSource.TEXT,listOf("a"),"原名")
        val raw=RecognitionEvidencePayload(taskId=UUID.randomUUID().toString(),appearanceOnly=false,candidate=candidate).encode()
        assertThrows(IllegalArgumentException::class.java) {RecognitionEvidencePayload.decode(raw.replace("\"version\":1","\"version\":\"1\""))}
        assertThrows(IllegalArgumentException::class.java) {RecognitionResult.decode(RecognitionResult(candidates=listOf(candidate)).encode().replace("\"version\":1","\"version\":\"1\""),setOf("a"))}
    }
}
