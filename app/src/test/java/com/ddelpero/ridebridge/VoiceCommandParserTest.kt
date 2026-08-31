package com.ddelpero.ridebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.ddelpero.ridebridge.voice.VoiceAssist
import com.ddelpero.ridebridge.voice.VoiceCommandParser
import com.ddelpero.ridebridge.voice.VoiceIntent

class VoiceCommandParserTest {
    @Test
    fun playPauseSkip() {
        assertEquals(VoiceIntent.Play, VoiceCommandParser.parse("play"))
        assertEquals(VoiceIntent.Play, VoiceCommandParser.parse("play music"))
        assertEquals(VoiceIntent.Play, VoiceCommandParser.parse("please resume"))
        assertEquals(VoiceIntent.Pause, VoiceCommandParser.parse("pause"))
        assertEquals(VoiceIntent.Pause, VoiceCommandParser.parse("stop the music"))
        assertEquals(VoiceIntent.Next, VoiceCommandParser.parse("next song"))
        assertEquals(VoiceIntent.Next, VoiceCommandParser.parse("skip"))
        assertEquals(VoiceIntent.Previous, VoiceCommandParser.parse("previous"))
    }

    @Test
    fun playSearch() {
        val intent = VoiceCommandParser.parse("play never gonna give you up")
        assertTrue(intent is VoiceIntent.PlaySearch)
        assertEquals("never gonna give you up", (intent as VoiceIntent.PlaySearch).query)

        val byArtist = VoiceCommandParser.parse("Tom Sawyer by Rush")
        assertTrue(byArtist is VoiceIntent.PlaySearch)
        assertEquals("tom sawyer by rush", (byArtist as VoiceIntent.PlaySearch).query)

        val playBy = VoiceCommandParser.parse("play tom sawyer by rush")
        assertEquals("tom sawyer by rush", (playBy as VoiceIntent.PlaySearch).query)
    }

    @Test
    fun call() {
        val mom = VoiceCommandParser.parse("call mom")
        assertTrue(mom is VoiceIntent.Call)
        assertEquals("mom", (mom as VoiceIntent.Call).nameOrNumber)

        val john = VoiceCommandParser.parse("phone john smith")
        assertEquals("john smith", (john as VoiceIntent.Call).nameOrNumber)

        val dial = VoiceCommandParser.parse("dial 5551234")
        assertEquals("5551234", (dial as VoiceIntent.Call).nameOrNumber)
    }

    @Test
    fun navigate() {
        val walmart = VoiceCommandParser.parse("navigate to walmart")
        assertTrue(walmart is VoiceIntent.Navigate)
        assertEquals("walmart", (walmart as VoiceIntent.Navigate).query)

        val gas = VoiceCommandParser.parse("find a gas station")
        assertEquals("gas station", (gas as VoiceIntent.Navigate).query)

        val gasBare = VoiceCommandParser.parse("gas station")
        assertEquals("gas station", (gasBare as VoiceIntent.Navigate).query)

        val takeMe = VoiceCommandParser.parse("take me to the nearest fuel")
        assertEquals("gas station", (takeMe as VoiceIntent.Navigate).query)

        val street = VoiceCommandParser.parse("directions to 123 main street")
        assertEquals("123 main street", (street as VoiceIntent.Navigate).query)

        val giveMe = VoiceCommandParser.parse("give me directions to the meteor")
        assertEquals("meteor", (giveMe as VoiceIntent.Navigate).query)

        val getMe = VoiceCommandParser.parse("get me directions to walmart")
        assertEquals("walmart", (getMe as VoiceIntent.Navigate).query)

        val whereIs = VoiceCommandParser.parse("where is a gas station")
        assertEquals("gas station", (whereIs as VoiceIntent.Navigate).query)

        val toMeteor = VoiceCommandParser.parse("To the meteor")
        assertEquals("meteor", (toMeteor as VoiceIntent.Navigate).query)

        val caseys = VoiceCommandParser.parse(
            "Take me to Casey's gas station in Bella Vista Arkansas"
        )
        assertEquals(
            "casey's gas station in bella vista arkansas",
            (caseys as VoiceIntent.Navigate).query
        )

        val takeGas = VoiceCommandParser.parse("take me to a gas station")
        assertEquals("gas station", (takeGas as VoiceIntent.Navigate).query)

        val gasNearMe = VoiceCommandParser.parse("gas station near me")
        assertEquals("gas station", (gasNearMe as VoiceIntent.Navigate).query)

        val findGasNear = VoiceCommandParser.parse("find a gas station near me")
        assertEquals("gas station", (findGasNear as VoiceIntent.Navigate).query)

        val nearestGas = VoiceCommandParser.parse("nearest gas station")
        assertEquals("gas station", (nearestGas as VoiceIntent.Navigate).query)

        val city = VoiceCommandParser.parse("Bentonville Arkansas")
        assertEquals("bentonville arkansas", (city as VoiceIntent.Navigate).query)
    }

    @Test
    fun assistantPlayPhrase() {
        assertEquals(
            "play tom sawyer by rush on youtube music",
            VoiceAssist.assistantPhrase("tom sawyer by rush")
        )
        assertEquals(
            "play never gonna give you up on youtube music",
            VoiceAssist.assistantPhrase("play never gonna give you up")
        )
        assertEquals(
            "play rush on youtube music",
            VoiceAssist.assistantPhrase("play rush on youtube music")
        )
    }

    @Test
    fun unknown() {
        assertEquals(VoiceIntent.Unknown, VoiceCommandParser.parse("what's the weather"))
        assertEquals(VoiceIntent.Unknown, VoiceCommandParser.parse(""))
    }
}
