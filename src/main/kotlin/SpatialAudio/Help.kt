package SpatialAudio

import SpatialAudio.SpatialAudioFun as s

/**
 * Provides useful troubleshooting information: Call help() to run.
 */
object Help {

    private val startTime: Long = System.currentTimeMillis()

    /**
     * Primary function within Help CLASS. Allows for the user to navigate
     * lists for troubleshooting information.
     */
    fun help(_self: s.opInfo) {
        println("For helpful information, please type 'H' in the terminal to enter the Help menu.")

        while (true) {
            Thread.sleep(1000)
            val txt = readLine() ?: ' '

            when (txt.toString()) {
                "h", "H" -> {
                    println("What would you like to know?")
                    println("[1] Current connected operators.")
                    println("[2] Current ports you are connected to.")
                    println("[3] Elapsed time.")
                    println("[4] All self opInfo data.")
                    println("[5] Am I running Demo mode?")
                    println("[Q] Exit.")
                    while (true) {
                        val txt2 = readLine() ?: ' '

                        when (txt2.toString()) {
                            "1" -> {
                                val numPorts = s.portsAudio.size

                                if (numPorts > 0) {
                                    println("Currently, there are ${numPorts} operators connected.")

                                    println("The operators connected are: ")
                                    for (i in 0 until numPorts) {
                                        println("${s.potentialOP[i]}:   ${connectedOps(i)?.OperatorName} on Port: ${connectedOps(i)?.OperatorPort}")
                                    }
                                } else {
                                    println("There are no operators connected.")
                                    println("The server is in progress of being initialized.")
                                }
                            }

                            "2" -> {
                                println("You are currently connected to Audio Port: ${_self.OperatorPort} " +
                                        "and Hyper IMU port: ${s.IMUPort}.")
                            }

                            "3" -> {
                                val currentTime: Long = System.currentTimeMillis()
                                var minutes: Int = 0
                                var seconds: Long = (currentTime - startTime)/1000

                                if(seconds >= 60 ){
                                    while (seconds >= 60) {
                                        seconds -= 60
                                        minutes += 1
                                    }
                                    if(minutes > 1) {
                                        println("The program has been running for $minutes minutes and $seconds seconds.")
                                    } else {
                                        println("The program has been running for $minutes minute and $seconds seconds.")
                                    }
                                } else {
                                    println("The program has been running for $seconds seconds.")
                                }

                            }

                            "4" -> {
                                println("This is your current information stored.")
                                for (key in s.operators.keys) {
                                    if (s.operators[key]?.OperatorName == _self.OperatorName) {
                                        println("Operator:  $key")
                                        println("Host name: ${_self.OperatorName}")
                                        println("Host IP:   ${_self.OperatorIP}")
                                        println("Port:      ${_self.OperatorPort}")
                                        println("Longitude: ${_self.OperatorLongitude} ")
                                        println("Latitude:  ${_self.OperatorLatitude}")
                                        println("Nose Direction: ${_self.OperatorNose}")
                                    }
                                }
                            }

                            "5" -> {
                                when (s.DEMO) {

                                    true -> {
                                        println("Demo mode is running.")
                                        println("If you would like to exit Demo mode, please press 'Q'")
                                    }

                                    false -> {
                                        println("Demo mode is not running.")
                                        println("If you would like to enter Demo mode, please press 'P'")
                                    }

                                }
                            }

                            "q", "Q" -> {
                                println("Exiting help menu.")
                                println("Type 'H' in the terminal at anytime to re-enter the help menu.")
                                break
                                }
                            }
                        }
                    }
                }
            }
        }

    private fun connectedOps(i: Int): s.opInfo? {
        return(s.operators[s.potentialOP[i]])
    }
}
