package SpatialAudio

import org.lwjgl.openal.AL10
import org.lwjgl.openal.AL10.alDeleteBuffers
import org.lwjgl.util.WaveData
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.*
import java.util.*
import javax.sound.sampled.*
import javax.swing.JFrame
import javax.swing.JLabel
import kotlin.concurrent.timerTask
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.properties.Delegates

object SpatialAudioFun {
    /**Data class that stores operator information
     * Stores:
     *      IP
     *      Port
     *      Host Name
     *      Longitude, Latitude, Distance Azimuth and Nose
     * Note:
     *      offset, activeTime, isActive are used for dynamic port removal
     */
    data class opInfo(var OperatorName: String, var OperatorPort: String = "") {
        var OperatorIP: String = ""
        var OperatorLongitude: Double = 0.0
        var OperatorLatitude: Double = 0.0
        var OperatorNose: Double = 0.0
        var OperatorAzimuth: Double = 0.0
        var OperatorDistance: Double = 0.0
        var offset: Int = 0
        var activeTime: Int = 0
        var isActive: Boolean = false
    }

    //region PRIVATE VARIABLES
    /**
     * TARGETDATALINE: targets primary microphone on device for audio recording.
     */
    private lateinit var mic: TargetDataLine

    /**
     * Hyper IMU socket
     */
    private lateinit var IMUSocket: DatagramSocket

    /**
     * Port in which strings are sent over
     */
    private lateinit var stringSocket: DatagramSocket

    /**
     *  Multicast Socket on port 8010
     */
    private lateinit var socketMultiConnect: MulticastSocket

    /**
     * Data variable which handles TargetDataLine data
     */
    private lateinit var data: ByteArray

    /**
     * Detect whether the specified button has been pressed/released
     */
    private var voice_Chat = 0

    /**
     * Buffer size in Bytes for storing audio data
     */
    private const val buffer = 1024

    /**
     * DatagramSocket used for sending audio data over multicast network.
     */
    private val socketSendAudio = DatagramSocket()

    /**
     * Audio port of self. Ranging from set Port -> Port + 7.
     */
    private var portAudio = 0

    /**
     * Placeholder for delegating operator ports for sending audio
     */
    private var port by Delegates.notNull<Int>()

    /**
     * List of operators connected to server
     */
    private var operators = mutableMapOf<String, opInfo>()

    /**
     * List of connected ports
     */
    private val portsAudio = mutableSetOf<String>()

    /**
     * List of IP's
     */
    private val addresses = mutableSetOf<String>()

    /**
     * Detects new connection request
     */
    private var opDetected = false

    /**
     * Determines whether server has been initialized.
     * If server has not been initialized, this tells the user that they are
     * the first to initialize the server.
     */
    private var timeOutOp = false

    /**
     * Determines if self has been initialized.
     */
    private var selfAdded = false

    /**
     * Determines if operator is already contained within data base.
     */
    private var opNotFound = false

    /**
     * List of all potential operators to be contained in data base.
     */
    private val potentialOP = listOf<String>("OP1","OP2","OP3","OP4","OP5","OP6","OP7","OP8")

    /**
     * Mutable list which is used for storing GPS data until it is saved within a data class.
     */
    private var opGPS = mutableListOf<String>("","","","","")

    /**
     * Determines whether the thread which sends off own audio has been suspended
     */
    private var suspended: Boolean = false

    /**
     * Audio format that has been constructed with specific parameters:
     *
     *      AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
     *                  sampleRate: 44100F,
     *                  sampleSizeInBits: 16,
     *                  channels: 2,
     *                  frameSize: 4,
     *                  frameRate: 44100F,
     *                  bigEndian: true)
     */
    private val format = AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        44100F,
        16,
        2,
        4,
        44100F,
        true)

    /**
     * List of 8 ByteArrayOutputStreams used for storing operator audio data for use with
     * OpenAL.
     */
    private val outDataBuffer = listOf<ByteArrayOutputStream>(
        ByteArrayOutputStream(),
        ByteArrayOutputStream(),
        ByteArrayOutputStream(),
        ByteArrayOutputStream(),
        ByteArrayOutputStream(),
        ByteArrayOutputStream(),
        ByteArrayOutputStream(),
        ByteArrayOutputStream()
    )

    /**
     * List of dedicated DatagramSockets available for use for up to 8 operators.
     *
     * Sockets range from ports set Port -> Port + 7.
     */
    private var socketRecAudio = mutableListOf<DatagramSocket>()

    /**
     * Int variable which is used to store the overload of the alGenBuffers()
     */
    private var buffer3D: Int = 0

    /**
     * Int variable for incrementing Port number within Functions
     */
    private var incPort: Int = 0

    /**
     * Stores data received from the TARGETDATALINE
     */
    private var numBytesRead: Int = 0

    /**
     * Locates host IP
     */
    private val findIP = """(\d+)\.(\d+)\.(\d+)\.(\d+)""".toRegex()

    /**
     * Locates host name
     */
    private val findName = """([A-Z])\w+""".toRegex()

    /**
     * Name of own device
     */
    private val hostName = findName.find(Inet4Address.getLocalHost().toString())?.value

    /**
     * IP of own device
     */
    private val hostAdd = findIP.find(Inet4Address.getLocalHost().toString())?.value
    //endregion

    /**
     * This FUNCTION sets the initial port on which audio should be received and bases
     * all other operators off of initial port.
     */
    fun setInitPort(Port: Int){
        portAudio = Port
        incPort = Port

        for (i in 0 until 8){
            socketRecAudio.add(DatagramSocket(Port + i))
        }

    }

    /**
     * This FUNCTION returns host name and host IP as opInfo data class
     */
    fun getHost(): opInfo{
        val host = opInfo(hostName.toString())
        host.OperatorIP = hostAdd.toString()

        return host
    }

    /**
     * This FUNCTION initializes detected microphone as the TargetDataLine and start recording audio
     */
    fun initMic(){
        mic = AudioSystem.getTargetDataLine(format)
        val info = DataLine.Info(TargetDataLine::class.java, format)
        mic = AudioSystem.getLine(info) as TargetDataLine
        mic.open(format)
        /**
         * ByteArray equal to the TARGETDATALINE's buffersize / 5
         */
        data = ByteArray(mic.bufferSize / 5)
        mic.start()
    }

    /**
     * This FUNCTION initializes the Hyper IMU port on a DatagramSocket designated within the HyperIMU app.
     */
    fun initHyperIMU(Port: Int){
        IMUSocket = DatagramSocket(Port)
    }

    /**
     * This FUNCTION initializes the String port on a DatagramSocket to receive operator data
     */
    fun initStringPort(Port: Int){
        stringSocket = DatagramSocket(Port)
    }

    /**
     * This FUNCTION intializes a MulticastSocket created for use with portConnect
     */
    fun initMulticast(HostName: String, Port: Int){
        socketMultiConnect = MulticastSocket(Port)
        socketMultiConnect.joinGroup(InetSocketAddress(HostName, Port), null)
    }

    /**
     * This FUNCTION initializes a Key listener for Push-to-Talk functionality.
     *
     * Note:
     *      PTT won't work without the window being visible.
     *      Setting frame.isVisible = false will deactivate PTT functionality
     *      In the final version, after integration with ATAK or Android, JFrame will not be used.
     */
    fun initPTT(c: Char){
        JFrame.setDefaultLookAndFeelDecorated(true)
        val frame = JFrame("KeyListener Example")       //Set-up title for separate window
        frame.setSize(300, 150)                 //Set-up dimensions of window
        val label = JLabel()
        frame.add(label)
        frame.addKeyListener(object : KeyListener {
            override fun keyTyped(ke: KeyEvent) {           // Detects key typed
            }
            override fun keyPressed(ke: KeyEvent) {         // Detects Key Pressed
                if(ke.keyChar == c) {
                    voice_Chat = 1
                }
            }
            override fun keyReleased(ke: KeyEvent) {        // Detects if key pressed was released
                if(ke.keyChar == c) {
                    voice_Chat = 0
                }
            }
        })
        frame.isVisible = true                              //Open Window
    }

    /**
     * This FUNCTION sends Operator join requests to all operators on MultiCast network.
     */
    fun sendRequest(Host: opInfo, portConnect: Int){
        // Initialize first operator (self) on server
        Thread.sleep(1000)
        if(timeOutOp) {
            Thread.sleep(1000)
            if (addresses.isNullOrEmpty()) {
                /** Send own information over server
                 * This is used until at least one operator joins
                 */
                val dataString = "OP REQUEST: OPNAME: $hostName IP: $hostAdd PORT_AUDIO: $portAudio"
                val datagramPacket = DatagramPacket(
                    dataString.toByteArray(),
                    dataString.toByteArray().size,
                    InetAddress.getByName("230.0.0.0"),
                    portConnect
                )
                socketMultiConnect.send(datagramPacket)

                //Set own port and Add own port to list of operators
                portsAudio.add(portAudio.toString())
                Host.OperatorPort = portAudio.toString()
                allocatePort(hostName.toString(), portAudio.toString(), hostAdd.toString())
                selfAdded = true
                Host.isActive = true // Will always be true
            }
        } else if (opDetected && selfAdded && !timeOutOp) {
            /** Send all operator information over server
             * This is used until all operators leave the server
             */
            val dataString =
                "OP REQUEST: OPNAME: $hostName IP: $hostAdd PORT_AUDIO: $portAudio PORTS_CONNECTED: $portsAudio"
            val datagramPacket = DatagramPacket(
                dataString.toByteArray(),
                dataString.toByteArray().size,
                InetAddress.getByName("230.0.0.0"),
                portConnect
            )
            socketMultiConnect.send(datagramPacket)
            if(!Host.isActive){
                Host.isActive = true
            }
            opDetected = false
            timeOutOp = false
        }
    }

    /**
     * This FUNCTION receives Operator join requests that were sent over the Multicast network.
     */
    fun receiveOP(Host: opInfo){
        // Wait 5 seconds before server initialization
        if(!opDetected && !selfAdded){
            Thread.sleep(100)
            Timer().schedule(timerTask {
                timeOutOp = true
            }, 1000 * 5)
        }

        // Initialize values and receive coordinates
        val buffer2 = ByteArray(1024)
        val response2 = DatagramPacket(buffer2, 1024)
        socketMultiConnect.receive(response2)
        val data2 = response2.data
        val dataString = String(data2, 0, data2.size)

        // Identify IP and ports //
        val sample = arrayOf<String>("","","","")   // Initialize array for storage of multiple regex's
        when {
            """OP REQUEST: """.toRegex()            // Identifying address of operators
                .containsMatchIn(dataString) -> {

                // Cancel 5 second Timer if server has been initialize by another operator
                if (!selfAdded) {
                    Timer().cancel()
                    println("[Line: ${LineNumberGetter()}] Timer Cancelled.")
                }

                /* Variables used to store and recognize parsed data from received packets
                 * Variables will Regex:
                 *      operator IP, Name, Port and total Ports on server
                 */
                val opIP = """(\d+)\.(\d+)\.(\d+)\.(\d+)""".toRegex().find(dataString)?.value
                val opName = """(?<=OPNAME: )\w+""".toRegex().find(dataString)?.value.toString()
                val opPort = """(?<=PORT_AUDIO: )\d\d\d\d""".toRegex().find(dataString)?.value.toString()
                val opPortR = """\d\d\d\d""".toRegex()
                val patt = opPortR.findAll(dataString)

                if (!addresses.contains(opIP)) { // New operator detected
                    try {
                        if (opIP != Host.OperatorIP) {  // New operator is not self
                            var i = 0

                            /* Sort through all Ports found
                             * Add all ports to portsAudio set
                             */
                            patt.forEach { f ->
                                sample[i] = f.value
                                if (sample[i] != "") {
                                    portsAudio.add(sample[i])
                                    i++
                                }
                            }

                            allocatePort(opName, opPort, opIP.toString())   // Set operator information
                            addresses.add(opIP.toString())                  // Add IP to addresses set
                            println("[Line: ${LineNumberGetter()}] OP FOUND @ $opIP $opPort")
                        }

                        /* Determine whether to take initial Port
                         * Will only be used if port initial Port has left server and removed from portsAudio set
                         */
                        if (!portsAudio.contains(portAudio.toString()) && !selfAdded) {
                            portsAudio.add(portAudio.toString())
                            Host.OperatorPort = portAudio.toString()
                            allocatePort(hostName.toString(), portAudio.toString(), hostAdd.toString())
                            selfAdded = true
                        }

                    } catch (e: BindException) { // Catch a Bind exception if portAudio is already bound
                        println("[Line: ${LineNumberGetter()}] Port $opPortR is already bound.")
                        println("[Line: ${LineNumberGetter()}] " + e.message)
                    }
                    /** Dynamically set port
                     * In order of statements:
                     *      First:
                     *          Compare own port, starting at initial Port, to received ports.
                     *          If port exists, own port is increased.
                     *          Repeats until own port does not exist within set.
                     *          Will not exceed 8.
                     *      Second:
                     *          If there exists more ports than operators on server.
                     *          Compare existing ports to current operators.
                     *          Remove extra port.
                     */
                    val portsInUse = portsAudio.toList()
                    if(!selfAdded) {
                        Thread.sleep(100)
                        Timer().schedule(timerTask {
                            if (portsAudio.contains(portAudio.toString()) && !selfAdded) {
                                for (i in 0 until portsAudio.size) {
                                    if (portsAudio.contains(portAudio.toString())) {
                                        portAudio = portAudio + 1
                                    }
                                }
                                portsAudio.add(portAudio.toString())
                                Host.OperatorPort = portAudio.toString()
                                allocatePort(hostName.toString(), portAudio.toString(), hostAdd.toString())
                                selfAdded = true
                            }
                        }, (1000..3000).random().toLong())
                    }else if (operators.size < portsAudio.size && selfAdded){
                        for(i in 0 until portsAudio.size) {
                            removePort(portsInUse[i])
                        }
                    }
                }
                opDetected = true

                // If no operators are on server except self
                timeOutOp = false
                }
            }
        }

    /**
     * This FUNCTION sends own GPS data from hyper IMU over Multicast network.
     */
    fun sendData(Host: opInfo, portString: Int){
        Thread.sleep(2000)
        // Obtain GPS data from Hyper IMU
        val myData = getData(Host, IMUSocket)

        // Time since joined server
        Host.activeTime += 1
        println("[Line: ${LineNumberGetter()}] Host: Time-${Host.activeTime} portsAudio: $portsAudio addresses: $addresses operators: $operators")


        // Initialize values and send coordinates
        val time = Date().toString()
        val messageTo = "OP-$hostName IP: $hostAdd PORT_AUDIO: $portAudio COORDs: $myData-- "
        val mes1 = (messageTo + time).toByteArray()
        for(i in 0 until addresses.size) {
            val request = DatagramPacket(
                mes1,
                mes1.size,
                Inet4Address.getByName(addresses.elementAtOrNull(i)),
                portString
            )
            stringSocket.send(request)
        }
        /* This statement will investigate each operator contained within operators.
         * Will calculate the time the operator has been inactive.
         * to determine whether or not the operator should be removed from sets.
         * By doing so, will open up a port for Audio connection for new operators.
         */
        try {
            for (key in operators.keys) {

                if ((Host.activeTime - operators[key]!!.activeTime) - operators[key]!!.offset > 1 && operators[key]?.OperatorName != Host.OperatorName) {
                    operators[key]!!.isActive = false
                    portsAudio.remove(operators[key]!!.OperatorPort)
                    addresses.remove(operators[key]?.OperatorIP)
                    operators.remove(key)
                    println("[Line: ${LineNumberGetter()}] PortsAudio: $portsAudio addresses: $addresses operators: $operators")
                }
                try {
                    if (operators[key]?.OperatorName != Host.OperatorName) {
                        println("[Line: ${LineNumberGetter()}] Op active? ${operators[key]?.isActive} Time Active: ${operators[key]?.activeTime} offset: ${(Host.activeTime - operators[key]?.activeTime!!.toInt()) - operators[key]!!.offset}")
                    }
                } catch (e: NullPointerException) {
                    println("[Line: ${LineNumberGetter()}] $key has timed out! $key as been removed!")
                }
            }
        } catch (e: ConcurrentModificationException){
            println("[Line: ${LineNumberGetter()}] Caught ConcurrentModificationException." + e.message)
        }
    }

    /**
     * This FUNCTION receives operator GPS data sent over the Multicast network.
     */
    fun receiveData(Host: opInfo){
        val buffer2 = ByteArray(1024)
        val response2 = DatagramPacket(buffer2, 1024)

        stringSocket.receive(response2)

        val data2 = response2.data
        val dataString = String(data2, 0, data2.size)
        println("[Line: ${LineNumberGetter()}] Printing received response: $dataString")

        /** Variables used to store and recognize parsed data from received packets
         * Variables will Regex:
         *      operator IP, Name, Port and Coordinates
         */
        val opName = """(?<=OP-)\w+""".toRegex().find(dataString)?.value.toString()
        val opIP =
            """(?<=IP: )(\d+).(\d+).(\d+).(\d+)""".toRegex().find(dataString)?.value.toString()
        val opPort = """(?<=PORT_AUDIO: )\d+""".toRegex().find(dataString)?.value.toString()
        val opCoords = """-?(\d+)\.\d+""".toRegex()
        val patt = opCoords.findAll(dataString)

        var i = 0
        patt.forEach { f ->
            opGPS[i] = f.value
            i++
        }

        //Allocate received coordinates to correct operator
        allocateCoords(opPort)

        for (key in operators.keys) {
            if (operators[key]?.OperatorName != hostName) {

                println("[Line: ${LineNumberGetter()}] Printing Operator GPS data: Op: ${operators[key]?.OperatorName} Long: ${operators[key]?.OperatorLongitude} Lat: ${operators[key]?.OperatorLatitude}")

                // Calculate Azimuth between self and operator
                operators[key]?.OperatorAzimuth = AzimuthCalc(
                    Host.OperatorLongitude,
                    Host.OperatorLatitude,
                    operators[key]!!.OperatorLongitude,
                    operators[key]!!.OperatorLatitude,
                    Host.OperatorNose
                )

                //Calculate distance between self and operator
                operators[key]?.OperatorDistance = OperatorDistance(
                    Host.OperatorLongitude,
                    Host.OperatorLatitude,
                    operators[key]!!.OperatorLongitude,
                    operators[key]!!.OperatorLatitude
                )

                println("[Line: ${LineNumberGetter()}] $operators")
                println("[Line: ${LineNumberGetter()}] ${operators[key]?.OperatorName}")
                println("[Line: ${LineNumberGetter()}] Host:     ${Host.OperatorName} Nose: ${Host.OperatorNose} Port: ${Host.OperatorPort}")
                println("[Line: ${LineNumberGetter()}] Operator: ${operators[key]?.OperatorName} Azimuth: ${operators[key]?.OperatorAzimuth}")
                println("[Line: ${LineNumberGetter()}] Host      Longitude: ${Host.OperatorLongitude} Latitude: ${Host.OperatorLatitude}")
                println("[Line: ${LineNumberGetter()}] Operator  Distance: ${operators[key]?.OperatorDistance} feet")
                println("\n")
            }
        }

        if (!portsAudio.contains(opPort)) {
            if(!opDetected && !operators.containsKey(opPort)){
                Timer().schedule(timerTask {
                    opNotFound = true
                }, 1000 * 5)
            }
        }
        if (opNotFound == true){
            portsAudio.add(opPort)
            addresses.add(opIP)
            allocatePort(opName, opPort, opIP)
            opNotFound = false
        }

        operatorTimeOut(Host, opName)
    }

    /**
     * This FUNCTION sends TargetDataLine audio over Multicast network.
     */
    fun sendAudio(){
        numBytesRead = mic.read(data, 0, buffer)

        println(addresses)
        println(portsAudio)
        for (i in 0 until addresses.size) {

            for(key in operators.keys) {
                if (addresses.elementAtOrNull(i) == operators[key]?.OperatorIP) {
                    port = operators[key]?.OperatorPort?.toInt()!!
                }
            }

            val request = DatagramPacket(
                data,
                numBytesRead,
                InetAddress.getByName(addresses.elementAtOrNull(i)),
                port
            )
            if (voice_Chat == 1) {
                socketSendAudio.send(request)
            }
        }
    }

    /**
     * This FUNCTION primary use is to handle received audio for processing
     */
    fun recAudio(operator: String){
        /*
         * Set buffer and initialize a type of Datagram packet to receive
         */
        val bufferRec = ByteArray(1024)
        val responseRec = DatagramPacket(bufferRec, bufferRec.size)

        /*
         * Create variables for use with creating a buffer to input into OpenAL.
         *
         * opRecording: Detect if audio is being received
         *
         * reset: Detect whether buffers for OpenAL have been flushed
         *
         * opSocket/opOutput: audio socket the operator is connected to and
         *          the ByteArrayOutputStream() the audio data is being recorded to
         *
         * startOutputSize: size of buffer that contains operator audio data
         */
        var opRecording: Boolean = false
        var reset: Int = 1
        var opSocket = DatagramSocket()
        var opOutput = ByteArrayOutputStream()
        var startOutputSize = opOutput.size()
        var call: Int = 0

        //Direct to the correct Port the operator is sending audio on and allocate Data to the correct buffer
        for (i in potentialOP.indices){
            when (operator) {
                potentialOP[i] -> {
                    opSocket = socketRecAudio[i]
                    opOutput = outDataBuffer[i]
                }
            }
        }

        //Creates a timer for when to move past a .receive() call
        opSocket.setSoTimeout(250)
        while (true) {
            try {
                //Receive audio on connected port
                opSocket.receive(responseRec)

                //Write audio to specified ByteArrayOutputStream()
                opOutput.write(responseRec.data, 0, responseRec.data.size)

                //Assign current size of ByteArrayOutputStream() to a variable
                val currentOutputSize = opOutput.size()

                //Process audio whenever enough data has been generated
                if(currentOutputSize - startOutputSize >= buffer){
                    try {
                        //Send buffer data and Azimuth to SpatialAudioFormat Function for audio processing
                        SpatialAudioFormat(opOutput, operators[operator]!!.OperatorAzimuth)
                    } catch (e: java.lang.NullPointerException){
                        //If no Azimuth data is being sent, default azimuth to 0.0
                        if(call == 0) {
                            println("[Line: ${LineNumberGetter()}] Not receiving Azimuth data from $operator! Azimuth set to 0.0!")
                        }
                        call = 1
                        SpatialAudioFormat(opOutput, 0.0)
                    }

                    //Reset buffersize offset
                    startOutputSize = currentOutputSize

                    //Reset ByteArrayOutputStream() to allow for new data
                    //Prevents repeating of data
                    opOutput.reset()
                }
                opRecording = true
                reset = 0
            } catch (e: SocketTimeoutException){
                opRecording = false
            }

            //Clear all buffers
            if(!opRecording && reset == 0) {
                reset = 1
                opOutput.reset()
                alDeleteBuffers(buffer3D)
            }
        }
    }

    /**
     * This FUNCTION Suspend designated audio thread to prevent the continuous sending of audio.
     */
    fun suspendThread(audioThread: Thread){
        Thread.sleep(100)
        if (!suspended && voice_Chat == 0) {
            println("[Line: ${LineNumberGetter()}] SendThread Suspended!")
            audioThread.suspend()
            suspended = true
        } else if (suspended && voice_Chat == 1){
            println("[Line: ${LineNumberGetter()}] SendThread Resumed!")
            audioThread.resume()
            suspended = false
        }
    }

    /**
     * This FUNCTION returns the line number that it is called in.
     */
    fun LineNumberGetter(): Int{
        return __thisIsMyLineNumber()
    }

    /**
     * PRIVATE: This FUNCTION receives GPS data from Hyper IMU
     */
    private fun getData(Host: opInfo, IMUSocket: DatagramSocket): List<Double> {
        val azimuthData = arrayOf("", "", "", "", "", "")
        var Longitude: Double
        var Latitude: Double
        var Nose: Double

        val buffer2 = ByteArray(1024)
        val packet = DatagramPacket(buffer2, buffer2.size)

        //Listen for packet on port 9000

        IMUSocket.receive(packet)

        val message = packet.data
        val dataString = String(message, 0, message.size)
        val azimuthRegex = """-?(\d+)\.\d+""".toRegex()
        val patt = azimuthRegex.findAll(dataString)
        var i = 0

        patt.forEach { f ->
            azimuthData[i] = f.value
            i++
            if (i > 5) {
                i = 0
            }
        }

        try {
            Longitude = azimuthData[4].toDouble()
            Latitude = azimuthData[3].toDouble()
            Nose = azimuthData[0].toDouble()

            Host.OperatorLongitude = Longitude
            Host.OperatorLatitude = Latitude
            Host.OperatorNose = Nose
        } catch (e: NumberFormatException){

            println("[Line: ${LineNumberGetter()}] Caught NumberFormatException.")
            println("[Line: ${LineNumberGetter()}] " + e.message)
            Longitude = 0.0
            Latitude = 0.0
            Nose = 0.0
        }
        return listOf(Longitude, Latitude, Nose)
    }

    /**
     * PRIVATE: This FUNCTION detects if an operator has left the Multicast network.
     */
    private fun operatorTimeOut(Host: opInfo, Name: String) {
        for(key in operators.keys){
            if (operators[key]!!.OperatorName == Name) {
                if(!operators[key]!!.isActive) {
                    operators[key]?.isActive = true
                }
                operators[key]!!.activeTime += 1

                operators[key]!!.offset = Host.activeTime - operators[key]!!.activeTime
            }
        }
    }

    /**
     * PRIVATE: This FUNCTION takes input data from the designated ByteArrayOutputStream() and the Azimuth data contained
     * within the operators class. This FUNCTION will, in turn, process the audio with respect to the azimuth to that
     * operator. Outputting the audio from their respective direction.
     */
    private fun SpatialAudioFormat(audioDataOutput: ByteArrayOutputStream, azimuth: Double){

        //Calulate the operators position in space based on azimuth
        val y: Float = Math.cos(Math.toRadians(azimuth)).toFloat()
        val x: Float = Math.sin(Math.toRadians(azimuth)).toFloat()
        val r: Float = 4.0F
        val Quad1: Double = 0.0
        val Quad2: Double = 90.0
        val Quad3: Double = 180.0
        val Quad4: Double = 270.0

        //Get frameSize from the current audio format
        val frameSizeInBytes = format.frameSize
        buffer3D = AL10.alGenBuffers()

        //Convert designated ByteArrayOutputStream() into a ByteArrayInputStream()
        val bais = ByteArrayInputStream(audioDataOutput.toByteArray())

        //Utilize the newly created input stream and generate an AudioInputStream()
        val audioInputStream = AudioInputStream(bais, format, (audioDataOutput.toByteArray().size / frameSizeInBytes).toLong())

        //Use WaveData to generate the correct format for Spatial Audio purposes
        val sound = WaveData.create(audioInputStream)

        //Assign data from WaveData into a buffer for augmentation
        AL10.alBufferData(buffer3D, AL10.AL_FORMAT_MONO16, sound.data, sound.samplerate * 2)

        //Dispose of WaveData information as it is no longer required
        sound.dispose()
        val source = AL10.alGenSources()

        AL10.alSourcei(source, AL10.AL_BUFFER, buffer3D)
        AL10.alSourcef(source, AL10.AL_GAIN, 1f)

        /*
         * Note: Orientation of alSource3f: AL_POSITION uses right-hand coordinate system where
         * X points to the right, Y points up and Z points towards the listener. This is the reason
         * as to why Y is determined by the cos of the azimuth and X is determined by the sin of the azimuth.
         *
         * Also, because v2: Y in the alSource3f faces up and elevation is not used currently, it is replaced
         * with zero.
         */

        //Set initial listener orientation, position and orientation

        //In front of Listener
        if((azimuth in Quad1..Quad2) || (azimuth >= Quad4)){
            AL10.alSource3f(source, AL10.AL_POSITION, x * r, 0f, y)
        }

        //Behind listener
        else if ((azimuth > Quad2 && azimuth < Quad3) || (azimuth >= Quad3 && azimuth < Quad4)){
            AL10.alSource3f(source, AL10.AL_POSITION, x * r, 0f, y * r)
        }

        //Set Position and Orientation of self
        AL10.alListener3f(AL10.AL_POSITION, 0f, 0f, 0f)
        AL10.alListener3f(AL10.AL_ORIENTATION, 0f, 0f, 0f)

        //Play received audio in buffer
        AL10.alSourcePlay(source)

        //Allow for the continuous change in operator position due to operator movement
        while(true) {
            if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) {

                //In front of listener
                if((azimuth in Quad1..Quad2) || (azimuth >= Quad4)){
                    AL10.alSource3f(source, AL10.AL_POSITION, x * r, 0f, y * (r / 2))
                }

                //Behind listener
                else if ((azimuth > Quad2 && azimuth < Quad3) || (azimuth >= Quad3 && azimuth < Quad4)){
                    AL10.alSource3f(source, AL10.AL_POSITION, x * r, 0f, y * r)
                }
            } else break
        }
    }


    /**
     * This FUNCTION will remove an operator and disassociate them from their port if their
     * connection is interrupted and disconnect.
     */
    private fun removePort(Port: String){
        for (i in potentialOP.indices) {
            when (Port.toInt()) {
                incPort + i -> {
                    if (!operators.containsKey(potentialOP[i])) {
                        portsAudio.remove(Port)
                    }
                }
            }
        }
    }


    /**
     * This FUNCTION will assign each operator their unique Longitude and Latitude data
     * based upon their coordinates sent via Hyper IMU.
     */
    private fun allocateCoords(Port: String) {
        for (i in potentialOP.indices){
            when (Port.toInt()) {
                incPort + i -> {
                    operators[potentialOP[i]]?.OperatorLongitude = opGPS[2].toDouble()
                    operators[potentialOP[i]]?.OperatorLatitude = opGPS[3].toDouble()
                }
            }
        }
    }


    /**
     * This FUNCTION will allocate an operators Name, Port and IP appropriately according to their received data.
     */
    private fun allocatePort(Name: String, Port: String, IP: String){
        for(i in 0 until potentialOP.size) {
            when (Port.toInt()) {
                incPort + i -> {
                    operators[potentialOP[i]] = opInfo(OperatorName = Name)
                    operators[potentialOP[i]]?.OperatorPort = Port
                    operators[potentialOP[i]]?.OperatorIP = IP
                }
            }
        }
    }


    /**
     * This FUNCTION will utilize the GPS data held in the operators DATA CLASS to calculate their azimuth.
     * The azimuth is taken with respect to self and altered based on direction self is facing.
     */
    private fun AzimuthCalc(myLongitude: Double, myLatitude: Double, opLongitude: Double, opLatitude: Double, Nose: Double): Double {
        val endLongitude: Double = Math.toRadians(opLongitude - myLongitude)
        val endLatitude: Double = Math.toRadians(opLatitude)
        val startLatitude = Math.toRadians(myLatitude)
        var phi = Math.toDegrees(atan2(sin(endLongitude) * cos(endLatitude), cos(startLatitude) * sin(endLatitude) - sin(startLatitude) * cos(endLatitude) * cos(endLongitude)))

        (phi + 360) % 360

        if(Nose < phi){
            phi = phi - Nose
        } else if(Nose > phi && ((Nose - phi) < 180)){
            phi = Nose - phi
        } else if (Nose > phi && ((Nose - phi) > 180)){
            phi = 360 - (Nose - phi)
        }

        return(phi)
    }


    /**
     * This FUNCTION will utilize the GPS data held in the operators DATA CLASS to calculate their distance. The
     * distance is relative to self.
     */
    private fun OperatorDistance(myLongitude: Double, myLatitude: Double, opLongitude: Double, opLatitude: Double): Double{
        val dLat: Double = Math.toRadians(opLatitude - myLatitude)
        val dLong: Double = Math.toRadians(opLongitude - myLongitude)
        val a = Math.pow(Math.sin(dLat/2), 2.0) + Math.cos(myLatitude) * Math.cos(opLatitude) * Math.pow(Math.sin(dLong/2), 2.0)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val R = 6300000
        val feet = ((R * c) * 100)/(2.54 * 12)

        return (feet)
    }

    /**
     * PRIVATE: This FUNCTION obtains the line number from which it was called.
     */
    private fun __thisIsMyLineNumber(): Int{
        val elements = Thread.currentThread().stackTrace
        var Detected = false
        var thisLine = false

        for (element in elements){
            val elementName = element.methodName
            val line = element.lineNumber

            if (Detected && thisLine){
                return line
            } else if(Detected){
                thisLine = true
            }
            if (elementName == "__thisIsMyLineNumber"){
                Detected = true
            }
        }
        return -1
    }



}