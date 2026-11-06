package com.lolita.app.data.square

import org.junit.Assert.*
import org.junit.Test

class SquareNetworkConfigTest {
    @Test fun localHttpAndHttpsRootsAreAccepted(){
        assertEquals("",normalizeSquareServiceUrl("  "))
        assertEquals("http://192.168.1.8:18765",normalizeSquareServiceUrl(" http://192.168.1.8:18765/ "))
        assertEquals("http://10.0.2.2:18765",normalizeSquareServiceUrl("http://10.0.2.2:18765"))
        assertEquals("https://square.example.com",normalizeSquareServiceUrl("https://square.example.com/"))
    }
    @Test fun arbitraryCleartextCredentialsAndPathsAreRejected(){
        for(url in listOf("http://public.example.com","http://10.attacker.0.0.1","file:///tmp/private","https://user:pass@example.com","https://example.com/v1","https://example.com?token=secret","https://example.com#fragment","http://192.168.1.1:99999","http://192.168.1.1/\nprivate")){
            try{normalizeSquareServiceUrl(url);fail(url)}catch(_:IllegalArgumentException){}
        }
    }
}
