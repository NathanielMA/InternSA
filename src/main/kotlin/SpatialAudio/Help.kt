package SpatialAudio

/**
 * This OBJECT provides useful troubleshooting information.
 */
object Help {

    var Ports = SpatialAudioFun.portsAudio.size

    /**
     * Primary function within Help CLASS. Allows for the user to navigate
     * lists for troubleshooting information.
     */
    fun help() {
        print("For helpful information, please type 'H' for a list of options.")

        while (true) {
            println("Hello")
            Thread.sleep(1000)
            val txt = readLine() ?: ' '

            when (txt.toString()) {
                "h", "H" -> {
                    println("What would you like to know?")
                    println("[1] Current connected operators.")
                    println("[2] Current ports you are connected to.")
                    println("[3] Elapsed time.")
                    println("[4] All self opInfo data.")
                    while (true) {
                        val txt2 = readLine() ?: ' '

                        when (txt2.toString()) {
                            "1" -> {
                                println("Currently, there are ${Ports} connected.")
                                println("The operators connected are: ")
                                for (i in 0 until Ports) {
                                    println("       ${connectedOps(i)}")
                                }
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    private fun connectedOps(i: Int): SpatialAudioFun.opInfo? {
        return(SpatialAudioFun.operators[SpatialAudioFun.potentialOP[i]])
    }
}
