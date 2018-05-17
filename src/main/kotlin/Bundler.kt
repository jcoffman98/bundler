package com.jeff.stuff

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import com.util.html.*

import java.nio.charset.StandardCharsets

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

class HdmiFrontEnd(val reg_maps: Map<String, RegMap?>) {
    var tmdsclk: Double = 0.0
    private var totalwidth: Int = 0
    private var totalheight: Int = 0
    private var width: Int = 0
    private var height: Int = 0
    private var v_polarity = 0
    private var h_polarity = 0
    private var interlaced = false
    private val MHZ = 1000000.0
    private val MHZ_FRAC = 128.0
    private var mode : String
    private var tmdspll_lck : String
    private var tmds_clk_a_det : String
    private var vert_f_locked : String
    private var horz_f_locked : String
    private var hdmi_hdcp : String
    private var cable_det : String

    init {
        val inter = reg_maps["hdmi"]!!.read8(0x0B)
        interlaced = ((inter shr 5) and 0x1) == 1

        width = reg_maps["hdmi"]!!.read16(0x7) and 0x00001FFF
        val hsyncfp = reg_maps["hdmi"]!!.read16(0x20)
        val hsyncdura = reg_maps["hdmi"]!!.read16(0x22)
        val hsyncbp = reg_maps["hdmi"]!!.read16(0x24)
        totalwidth = width + hsyncfp + hsyncdura + hsyncbp

        height = reg_maps["hdmi"]!!.read16(0x9) and 0x00001FFF
        val vsyncfp = reg_maps["hdmi"]!!.read16(0x2A)  / 2
        val vsyncdura = reg_maps["hdmi"]!!.read16(0x2E) / 2
        val vsyncbp = reg_maps["hdmi"]!!.read16(0x32) / 2
        totalheight = height + vsyncfp + vsyncdura + vsyncbp

        val freq_reg = reg_maps["hdmi"]!!.read16(0x51)
        val freq_whole = (freq_reg and 0xFF80) shr 0x7
        val freq_frac: Double = (freq_reg and 0x007F).toDouble() / MHZ_FRAC
        tmdsclk = freq_whole + freq_frac

        //polarity
        val auxpol = reg_maps["hdmi"]!!.read8(0x5)
        v_polarity = (auxpol shr 4) and 0x1
        h_polarity = (auxpol shr 5) and 0x1

        //interrupt status's
        val irq2status = reg_maps["io"]!!.read8(0x65)
        val irq3status = reg_maps["io"]!!.read8(0x6A)
        val irq4status = reg_maps["io"]!!.read8(0x6F)

        mode = when(((irq2status shr 0x3) and 0x1)) {
            1 -> "HDMI"
            0 -> "DVI"
            else -> "unknown"
        }

        tmdspll_lck = when(((irq3status shr 6) and 0x1)) {
            1 -> "Locked"
            0 -> "not Locked"
            else -> "Idunnoknow"
        }

        tmds_clk_a_det = when(((irq3status shr 4) and 0x1)) {
            1 -> "detected"
            0 -> "not detected"
            else -> "Idunnoknow"
        }

        vert_f_locked = when(((irq3status shr 1) and 0x1)) {
            1 -> "locked"
            0 -> "not locked"
            else -> "Idunnoknow"
        }

        horz_f_locked = when((irq3status and 0x1)) {
            1 -> "locked"
            0 -> "not locked"
            else -> "Idunnoknow"
        }

        hdmi_hdcp = when(((irq4status shr 2)and 0x1)) {
            1 -> "encrypted"
            0 -> "not encrypted"
            else -> "Idunnoknow"
        }

        cable_det = when((irq4status and 0x1)) {
            1 -> "detected"
            0 -> "not detected"
            else -> "Idunnoknow"
        }
    }

    fun getpixelclock(color_depth: Double) : Double {
        return (tmdsclk*8.0/color_depth)
    }

    fun getverticalfreq() : Double {
        return MHZ * (getpixelclock(10.0) / (totalwidth * totalheight))
    }

    fun getcolorspace() : String {
        val cs_reg = reg_maps["hdmi"]!!.read8(0x53)
        return when(cs_reg) {
            0 -> "RGB_LIMITED_CS"
            1 -> "RGB_FULL_CS"
            2 -> "YUV_601_CS"
            3 -> "YUV_709_CS"
            4 -> "XVYCC_601_CS"
            5 -> "XVYCC_709_CS"
            6 -> "YUV_601_FULL_CS"
            7 -> "YUV_709_FULL_CS"
            8 -> "SYYC_CS"
            9 -> "ADOBE_YCC_601_CS"
            10 -> "ADOBE_RGB"
            else -> "UKNOWN_CS"
        }
    }

    fun generatereport() : String {
        val report =  html {
            body {
                h1 { +"HDMI RX report"}
                h2 { +"Input Status"}
                p { +"${width}x${height}@${getverticalfreq()} "}
                p { +"colorspace = ${getcolorspace()}"}
                p { +"vertical polarity = $v_polarity"}
                p { +"horizontal polarity = $h_polarity"}
                p { +"interlaced = $interlaced"}
                p { +"HDMI mode = $mode"}
                p { +"TMDS PLL  = $tmdspll_lck"}
                p { +"TMDS clk  = $tmds_clk_a_det"}
                p { +"Vertical filter  = $vert_f_locked"}
                p { +"Horizontal filter  = $horz_f_locked"}
                p { +"HDCP  = $hdmi_hdcp"}
                p { +"Cable = $cable_det"}
            }
        }
        return report.toString()
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


fun process_table(blob: String) : ByteArray {
    //no line can ever be too long
    val raw_values = blob.substring(blob.findLastAnyOf(setOf("-"))!!.first+1).split("\n").flatten().split(" ")
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
            "io" to null,
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

    val fe1 = HdmiFrontEnd(maps[0])

    println("html ${fe1.generatereport()}")
    Files.write(Paths.get("./data/report.html"), fe1.generatereport().toByteArray(StandardCharsets.UTF_8))
}