package com.jeff.stuff

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

open class RegMap(val data: ByteArray) {

    fun read8(offset: Int) : Int {
        return ByteBuffer.wrap(data).get(offset).toInt() and 0xFF
    }

    fun read16(offset: Int) : Int {
        return ByteBuffer.wrap(data).getShort(offset).toInt() and 0xFFFF
    }

    fun read32(offset: Int) : Long {
        return ByteBuffer.wrap(data).getInt(offset).toLong() and 0xFFFFFFFF
    }
}

class HdmiFrontEnd(val reg: RegMap) {
    var tmdsclk: Double = 0.0
    private var totalwidth: Int = 0
    private var totalheight: Int = 0
    private val MHZ = 1000000.0
    private val MHZ_FRAC = 128.0

    init {
        val width = reg.read16(0x7) and 0x00001FFF
        val hsyncfp = reg.read16(0x20)
        val hsyncdura = reg.read16(0x22)
        val hsyncbp = reg.read16(0x24)
        totalwidth = width + hsyncfp + hsyncdura + hsyncbp

        val height = reg.read16(0x9) and 0x00001FFF
        val vsyncfp = reg.read16(0x2A)  / 2
        val vsyncdura = reg.read16(0x2E) / 2
        val vsyncbp = reg.read16(0x32) / 2
        totalheight = height + vsyncfp + vsyncdura + vsyncbp

        val freq_reg = reg.read16(0x51)
        val freq_whole = (freq_reg and 0xFF80) shr 0x7
        val freq_frac: Double = (freq_reg and 0x007F).toDouble() / MHZ_FRAC
        tmdsclk = freq_whole + freq_frac
    }

    fun compute_pixel_clock(color_depth: Double) : Double {
        return (tmdsclk*8.0/color_depth)
    }

    fun compute_vertical_freq() : Double {
           return MHZ * (compute_pixel_clock(10.0) / (totalwidth * totalheight))
    }

    fun status() {
        //filter status
        //val v_filterlocked = reg.read16()

    }
}

//a special flatten for flattening special things
fun List<String>.flatten() : String {
    var flat = ""
    for(c in this) {
        flat += c.substringAfter("|").trim() + " "
    }
    return flat.trim()
}

fun compute_tmds_clock(map: RegMap?) : Double {
    val freq_reg = map!!.read16(0x51)
    val freq_whole = (freq_reg and 0xFF80) shr 0x7
    val freq_frac: Double = (freq_reg and 0x007F).toDouble() / 128.0
    val freq = freq_whole + freq_frac

    println("raw frequency reg = $freq_reg, freq_whole = $freq_whole, freq_frac = $freq_frac")
    return freq
}

fun compute_pixel_clock(freq: Double, color_depth: Double) : Double {
    return (freq*8.0/color_depth)
}

fun compute_vertical_freq(map: RegMap?) {
    if(map != null) {
        val width = map.read16(0x7) and 0x00001FFF
        val hsyncfp = map.read16(0x20)
        val hsyncdura = map.read16(0x22)
        val hsyncbp = map.read16(0x24)
        val totalwidth = width + hsyncfp + hsyncdura + hsyncbp

        val height = map.read16(0x9) and 0x00001FFF
        val vsyncfp = map.read16(0x2A)  / 2
        val vsyncdura = map.read16(0x2E) / 2
        val vsyncbp = map.read16(0x32) / 2
        val totalheight = height + vsyncfp + vsyncdura + vsyncbp

        val tmdsclk = compute_tmds_clock(map)
        val vertfreq = 1000000.0 * (compute_pixel_clock(tmdsclk, 10.0) / (totalwidth * totalheight))
        println("tmdsclk = $tmdsclk")
        println("hsyncfp = $hsyncfp, hsyncdura = $hsyncdura, hsyncbp = $hsyncbp")
        println("vsyncfp = $vsyncfp, vsyncdura = $vsyncdura, vsyncbp = $vsyncbp")
        println("vert freq = $vertfreq, width = $width, total width = $totalwidth, height = $height total height = $totalheight")
    }
}

fun compare_map(r1 : RegMap, r2 : RegMap) {

}

fun process_table(blob: String?) : ByteArray {
    //no line can ever be too long
    val one = blob!!.substring(blob!!.findLastAnyOf(setOf("-"))!!.first+1)
    val two = one.split("\n")
    val three = two.flatten()
    val four = three.split(" ")
    val raw_values = blob!!.substring(blob!!.findLastAnyOf(setOf("-"))!!.first+1).split("\n").flatten().split(" ")
    val data = ByteArray(raw_values.size)
    for((index, value) in raw_values.withIndex()) {
        data[index] = Integer.parseInt(value, 16).toByte()
    }
    return data
}

fun process_reg_file(path: Path) : Map<String, RegMap?> {
    println("The path is $path")

    val data = Files.readAllBytes(path)
    val str =  String(data, Charset.defaultCharset())

    var reg_maps = mutableMapOf<String, RegMap?>("hdmi" to null,
            "rep" to null,
            "edid" to null,
            "if" to null,
            "cec" to null,
            "cp" to null,
            "dpll" to null)

    //There should be an entry for each type
    for(header in reg_maps.keys) {
        val substr = str.substring( str.findAnyOf(listOf(header))?.first!!)
        val endex = substr.findAnyOf(reg_maps.keys.filter { s -> s != header})?.let {
            it.first
        } ?: substr.length
        val result = process_table(substr.substring(substr.findAnyOf(listOf(header))?.first!!, endex))
        reg_maps[header] = RegMap(result)
    }

    return reg_maps
}

fun main(args: Array<String>) {
    println("number of args = ${args.size}")
    val maps = mutableListOf<Map<String, RegMap?>>()
    val bundle_path = Paths.get("./data")
    for(arg in args) {
        println(arg)
        maps.add(process_reg_file(bundle_path.resolve(arg)))
    }

    val fe1 = maps[0]["hdmi"]?.let { HdmiFrontEnd(it) }
    val fe2 = maps[1]["hdmi"]?.let { HdmiFrontEnd(it) }

    println("Vertical frequency ${fe1?.compute_vertical_freq()}")
    println("Vertical frequency ${fe2?.compute_vertical_freq()}")

}