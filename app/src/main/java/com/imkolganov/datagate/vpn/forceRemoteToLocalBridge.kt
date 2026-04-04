package com.imkolganov.datagate.vpn

fun forceRemoteToLocalBridge(original: String, port: Int, linkProtocol: VpnLinkProtocol): String {
    val protoLine = linkProtocol.configProtoLine()
    val lines = original.replace("\r\n", "\n").split("\n")
    val out = ArrayList<String>(lines.size)

    var remoteWritten = false
    var protoWritten = false

    for (raw in lines) {
        val line = raw.trimEnd()
        val lower = line.trimStart().lowercase()

        if (lower.startsWith("remote ")) {
            if (!remoteWritten) {
                out.add("remote 127.0.0.1 $port")
                remoteWritten = true
            }
            continue
        }

        if (lower.startsWith("proto ")) {
            out.add(protoLine)
            protoWritten = true
            continue
        }

        out.add(line)
    }

    if (!protoWritten) out.add(0, protoLine)
    if (!remoteWritten) out.add(0, "remote 127.0.0.1 $port")

    return out.joinToString("\n").trimEnd() + "\n"
}
