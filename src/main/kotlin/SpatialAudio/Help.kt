package SpatialAudio

import SpatialAudio.SpatialAudioFun as s

/**
 * Provides useful troubleshooting information: Call help() to run.
 */
object Help {

    private val _self = s._self
    private val startTime: Long = System.currentTimeMillis()
    private var run: Boolean = false

    /**
     * Primary function within Help CLASS. Allows for the user to navigate
     * lists for troubleshooting information.
     */
    fun help() {
        println("For helpful information, please type 'H' in the terminal to enter the Help menu.")

        while (true) {
            Thread.sleep(1000)

            when (readLine() ?: " ") {
                "h", "H" -> {

                    println("What would you like to know?")
                    menu()

                    while (true) {
                        when (readLine() ?: ' ') {
                            "1" -> {
                                val numPorts = s.portsAudio.size

                                if (numPorts > 1) {
                                    println("Currently, there are ${numPorts - 1} operators connected to the server.")

                                    println("The operators connected are: ")
                                    for (key in s.operators.keys) {
                                        if (s.operators[key]?.OperatorName != _self.OperatorName) {
                                            println("$key:   ${s.operators[key]?.OperatorName} on Port: ${s.operators[key]?.OperatorPort}"
                                            )
                                        }
                                    }
                                }else if (numPorts == 1){
                                    println("Currently, you are the only operator connected to the server.")
                                } else {
                                    println("There are no operators connected.")
                                    println("The server is in progress of being initialized.")
                                }

                                menu()
                            }

                            "2" -> {
                                println("You are currently connected to Audio Port: ${_self.OperatorPort} " +
                                        "and Hyper IMU port: ${s.IMUPort}.")

                                menu()
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

                                menu()
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

                                menu()
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

                                menu()
                            }

                            "6" -> {
                                run = true
                                data()

                                menu()
                            }

                            "7" -> {
                                if (s.portsAudio.size > 1) {
                                    println("Current operator Azimuths and Distances:")
                                    for (key in s.operators.keys){
                                        println("$key   Azimuth: ${s.operators[key]?.OperatorAzimuth}   Distance: ${s.operators[key]?.OperatorDistance}")
                                    }
                                } else {
                                    println("There are no operators currently connected besides yourself.")
                                }

                                menu()
                            }

                            "q", "Q" -> {
                                println("Exiting help menu.")
                                println("Type 'H' in the terminal to open up the help menu. ")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun menu(){
        Thread.sleep(2000)
        println("\n[1] Current connected operators.               [6] Nose data.")
        println("[2] Current ports you are connected to.        [7] Operator Azimuth data.")
        println("[3] Elapsed time.")
        println("[4] All self opInfo data.")
        println("[5] Am I running Demo mode?")
        println("\n[Q] Exit.")
    }
    private fun data(){
        var directionABS: String = " "
        println("To stop data readout, type 'Q' in the terminal.")

        while (run) {
            if (!s.notified) {
                when (_self.OperatorNose) {
                    0.0 -> directionABS = "N"
                    in 30.0..45.0 -> directionABS = "NE"
                    90.0 -> directionABS = "E"
                    in 125.0..145.0 -> directionABS = "SE"
                    180.0 -> directionABS = "S"
                    in 215.0..235.0 -> directionABS = "SW"
                    270.0 -> directionABS = "W"
                    in 305.0..325.0 -> directionABS = "NW"
                }
                println("Current facing: ${_self.OperatorNose} $directionABS")
        } else {
            println("Hyper IMU is not running or you are not receiving data from Hyper IMU!")
            println(s.infoString)

            println("Exiting data readout!")
            run = false
        }
            when (readLine() ?: " "){
                "q", "Q" -> {

                    println("Stopping data readout.")
                    println("Returning to help menu.")
                    run = false
                }
            }
        }
    }
}
