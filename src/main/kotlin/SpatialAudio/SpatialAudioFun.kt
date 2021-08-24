package SpatialAudio

import org.lwjgl.openal.AL
import org.lwjgl.openal.AL10
import org.lwjgl.openal.AL10.alDeleteBuffers
import org.lwjgl.openal.AL10.alGenBuffers
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

/**
 * Main library which consists of all necessary tools for processing operator audio with OpenAL.
 */
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

    //region DEMO VARIABLES
    private var directionDemo: Int = 0

    var DEMO: Boolean = false
    //endregion

    //region PUBLIC VARIABLES
    /**
     * List of connected ports
     */
    val portsAudio = mutableSetOf<String>()

    /**
     * List of operators connected to server
     */
    var operators = mutableMapOf<String, opInfo>()

    /**
     * List of all potential operators to be contained in data base.
     */
    val potentialOP = listOf<String>("OP1","OP2","OP3","OP4","OP5","OP6","OP7","OP8")

    /**
     * Int variable for storing the designated Hpper IMU port.
     */
    var IMUPort: Int = 0

    /**
     * Detects whether self has been notified for being unable to receive Hyper IMU data.
     */
    var notified: Boolean = false

    /**
     * This String is used to display information for connecting Hyper IMU if it is not running
     */
    lateinit var infoString: String
    //endregion

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
     * JFrame variable for creating a JFrame key listener.
     */
    private lateinit var frame: JFrame

    /**
     * Used alongside the comparator variable to detect when new operators join or leave the server.
     */
    private var currentOps = mutableMapOf<String, opInfo>()

    /**
     * Used alongside the currentOps variable to detect when new operates join or leave the server.
     */
    private var comparator: Int = 0

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
     * This FUNCTION asks the user to set the initial port on which audio should be received and bases
     * all other operators off of initial port.
     *
     * It also sets the Hyper IMU port required for receiving Hyper IMU data
     */
    fun setInitPorts(){
        print("Enter a starting Port value: ")
        val txt = readLine() ?: "6011"
        portAudio = txt.toInt()

        incPort = portAudio

        print("\nEnter the desired Hyper IMU port: ")
        val txt2 = readLine() ?: "9000"
        IMUPort = txt2.toInt()

        infoString = "Hyper IMU information:\nHost IP:  $hostAdd\nPort:     $IMUPort\n"

        IMUSocket = DatagramSocket(IMUPort)

        for (i in 0 until 8){
            socketRecAudio.add(DatagramSocket(incPort + i))
        }
        println("\n[Line: ${LineNumberGetter()}] Listening on Ports $txt -> ${txt.toInt() + 7} and $txt2 for Audio and Hyper IMU data.")

        println("\nWould you like to start in DEMO MODE?: ")
        val demotxt = readLine() ?: "no"

        when (demotxt){
            "No","no","n","N" ->{
                DEMO = false
                println("If you would like to start DEMO MODE press 'P' at any time.")
            }
            "yes", "Yes", "y", "Y" -> {
                DEMO = true
                println("If you would like to quit DEMO MODE press 'Q' at any time.")
            }
        }
    }

    /**
     * This FUNCTION returns host name and host IP as opInfo data class
     */
    fun getHost(): opInfo{
        val _self = opInfo(hostName.toString())
        _self.OperatorIP = hostAdd.toString()

        return _self
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
        frame = JFrame("KeyListener Example")           //Set-up title for separate window
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
     * This FUNCTION initializes Key listeners for Demonstrating Spatial Audio without
     * requiring the need for GPS coordinates.
     *
     * Note:
     *      PTT won't work without the window being visible.
     *      Setting frame.isVisible = false will deactivate PTT functionality
     *      In the final version, after integration with ATAK or Android, JFrame will not be used.
     */
    fun initSADemo(Left: Char, Right: Char, Front: Char, Behind: Char, Around: Char, Reset: Char){
        frame.addKeyListener(object : KeyListener {
            override fun keyTyped(ke: KeyEvent) {           // Detects key typed
            }
            override fun keyPressed(ke: KeyEvent) {         // Detects Key Pressed
                when (ke.keyChar) {
                    Left -> directionDemo = 1
                    Right -> directionDemo = 2
                    Front -> directionDemo = 3
                    Behind -> directionDemo = 4
                    Around -> directionDemo = 5
                    Reset -> directionDemo = 0
                    'q' -> DEMO = false
                    'p' -> DEMO = true
                }
            }
            override fun keyReleased(ke: KeyEvent) {        // Detects if key pressed was released
            }
        })
    }

    /**
     * This FUNCTION sends Operator join requests to all operators on MultiCast network.
     */
    fun sendRequest(_self: opInfo, portConnect: Int){
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
                _self.OperatorPort = portAudio.toString()
                allocatePort(hostName.toString(), portAudio.toString(), hostAdd.toString())
                selfAdded = true
                _self.isActive = true // Will always be true
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
            if(!_self.isActive){
                _self.isActive = true
            }
            opDetected = false
            timeOutOp = false
        }
    }

    /**
     * This FUNCTION receives Operator join requests that were sent over the Multicast network.
     */
    fun receiveOP(_self: opInfo){
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
                        if (opIP != _self.OperatorIP) {  // New operator is not self
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
                            notifyMe()
                        }

                        /* Determine whether to take initial Port
                         * Will only be used if port initial Port has left server and removed from portsAudio set
                         */
                        if (!portsAudio.contains(portAudio.toString()) && !selfAdded) {
                            portsAudio.add(portAudio.toString())
                            _self.OperatorPort = portAudio.toString()
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
                     *
                     *          Note: Self will be added within a random interval between 1 - 4 seconds.
                     *          This is to ensure the correct allocation for each operator if they happen
                     *          to join the server at the same moment.
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
                                        portAudio += 1
                                    } else if ((portAudio - incPort) >= 8){
                                        println("[Line: ${LineNumberGetter()}] There are currently 8 operators on the server.")
                                        println("[Line: ${LineNumberGetter()}] Unable to join.")
                                        break
                                    }
                                }
                                portsAudio.add(portAudio.toString())
                                _self.OperatorPort = portAudio.toString()
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
    fun sendData(_self: opInfo, portString: Int){
        Thread.sleep(2000)
        // Obtain GPS data from Hyper IMU
        val myData = getData(_self, IMUSocket)

        // Time since joined server
        _self.activeTime += 1
//        println("[Line: ${LineNumberGetter()}] Host: Time-${_self.activeTime} portsAudio: $portsAudio addresses: $addresses operators: $operators")


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

                if ((_self.activeTime - operators[key]?.activeTime!!) - operators[key]?.offset!! > 1 && operators[key]?.OperatorName != _self.OperatorName) {
                    operators[key]!!.isActive = false
                    portsAudio.remove(operators[key]!!.OperatorPort)
                    addresses.remove(operators[key]?.OperatorIP)
                    operators.remove(key)
                    notifyMe()
//                    println("[Line: ${LineNumberGetter()}] PortsAudio: $portsAudio addresses: $addresses operators: $operators")
                }
                try {
                    if (operators[key]?.OperatorName != _self.OperatorName) {
//                        println("[Line: ${LineNumberGetter()}] Op active? ${operators[key]?.isActive} Time Active: ${operators[key]?.activeTime} offset: ${(_self.activeTime - operators[key]?.activeTime!!.toInt()) - operators[key]!!.offset}")
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
    fun receiveData(_self: opInfo){
        val buffer2 = ByteArray(1024)
        val response2 = DatagramPacket(buffer2, 1024)

        stringSocket.receive(response2)

        val data2 = response2.data
        val dataString = String(data2, 0, data2.size)
//        println("[Line: ${LineNumberGetter()}] Printing received response: $dataString")

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

                // Calculate Azimuth between self and operator
                operators[key]?.OperatorAzimuth = AzimuthCalc(
                    _self.OperatorLongitude,
                    _self.OperatorLatitude,
                    operators[key]!!.OperatorLongitude,
                    operators[key]!!.OperatorLatitude,
                    _self.OperatorNose
                )

                //Calculate distance between self and operator
                operators[key]?.OperatorDistance = OperatorDistance(
                    _self.OperatorLongitude,
                    _self.OperatorLatitude,
                    operators[key]!!.OperatorLongitude,
                    operators[key]!!.OperatorLatitude
                )
            }
        }

        if (!portsAudio.contains(opPort)) {
            if(!opDetected && !operators.containsKey(opPort)){
                Timer().schedule(timerTask {
                    opNotFound = true
                }, 1000 * 5)
            }
        }
        if (opNotFound){
            portsAudio.add(opPort)
            addresses.add(opIP)
            allocatePort(opName, opPort, opIP)
            opNotFound = false
        }

        operatorTimeOut(_self, opName)
    }

    /**
     * This FUNCTION sends TargetDataLine audio over Multicast network.
     */
    fun sendAudio(){
        numBytesRead = mic.read(data, 0, buffer)

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
    fun recAudio(_self: opInfo, operator: String){
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
        var opSocket = DatagramSocket()
        var opOutput = ByteArrayOutputStream()
        var startOutputSize = 0
        var call: Int = 0
        var demoAzimuth = 0.0
        var audioReceived: Boolean
        var update: Int = 0
        var k: Int = 0

        //Direct to the correct Port the operator is sending audio on and allocate Data to the correct buffer
        for (i in potentialOP.indices){
            when (operator) {
                potentialOP[i] -> {
                    k = i
                    opSocket = socketRecAudio[i]
                    opOutput = outDataBuffer[i]
                }
            }
        }

        //Creates a timer for when to move past a .receive() call
        opSocket.setSoTimeout(250)
        while (true) {

            audioReceived = false

            try {
                //Receive audio on connected port
                opSocket.receive(responseRec)

                //Update variable to true if socket does not time out
                audioReceived = true

                //Write audio to specified ByteArrayOutputStream()
                opOutput.write(responseRec.data, 0, responseRec.data.size)

                //Assign current size of ByteArrayOutputStream() to a variable
                val currentOutputSize = opOutput.size()
                //Process audio whenever enough data has been generated
                if(currentOutputSize - startOutputSize >= 1024) {

                    when (DEMO) {
                        false -> {
                            try {
                                //Send buffer data and Azimuth to SpatialAudioFormat Function for audio processing
                                SpatialAudioFormat(opOutput, operators[operator]!!.OperatorAzimuth)
                            } catch (e: java.lang.NullPointerException) {
                                //If no Azimuth data is being sent, default azimuth to 0.0
                                if (call == 0) {
                                    println("[Line: ${LineNumberGetter()}] Not receiving Azimuth data from $operator! Azimuth set to 0.0!")
                                }
                                call = 1
                                SpatialAudioFormat(opOutput, 0.0)
                            }

                            startOutputSize = currentOutputSize

                            //Reset ByteArrayOutputStream() to allow for new data
                            //Prevents repeating of data
                            opOutput.reset()
                        }

                        //Demonstrate audio processing in 4 cardinal directions.
                        true -> {
                            when (directionDemo){
                                1 -> SpatialAudioFormat(opOutput, 270.0)
                                2 -> SpatialAudioFormat(opOutput, 90.0)
                                3 -> SpatialAudioFormat(opOutput, 0.0)
                                4 -> SpatialAudioFormat(opOutput, 180.0)
                                5 -> {
                                    demoAzimuth += 5.0
                                    SpatialAudioFormat(opOutput, demoAzimuth)
                                    if (demoAzimuth >= 360) {
                                        demoAzimuth = 0.0
                                    }
                                }
                            }

                            //Reset buffersize offset
                            startOutputSize = currentOutputSize

                            //Reset ByteArrayOutputStream() to allow for new data
                            //Prevents repeating of data
                            opOutput.reset()
                        }
                    }

                    /*
                     * Reset Buffer to prevent delay in audio.
                     *
                     * NOTE: One problem did occur due to not resetting the value of statOutputSize. This prevented the
                     * Buffer from successfully resetting and caused the buffer to regain its original size.
                     */
                } else if (startOutputSize >= 32768){
                    startOutputSize = 0
                }
            } catch (e: SocketTimeoutException){
                if(!audioReceived && update != 1 && portsAudio.size > 1) {
                    if(operators[potentialOP[k]] != null && operators[potentialOP[k]]?.OperatorName != _self.OperatorName ) {
                        println("${operators[potentialOP[k]]?.OperatorName} is no longer sending audio.")
                    }
                    update = 1
                }
                opOutput.reset()
                startOutputSize = 0
            }
        }
    }

    /**
     * This FUNCTION Suspend designated audio thread to prevent the continuous sending of audio.
     * It functions by pressing/releasing the designate PTT key.
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
     * This Function simply starts the list of Threads provided.
     *
     * If not starting a single thread, pass "null" for _thread.
     */
    fun startThread(Threads: List<Thread>, _thread: Thread?){
        for (element in Threads) {
            element.start()
        }

        //If _thread is not null, start _thread
        _thread?.start()
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
    private fun getData(_self: opInfo, IMUSocket: DatagramSocket): List<Double>? {
        val azimuthData = arrayOf("", "", "", "", "", "")
        var Longitude: Double
        var Latitude: Double
        var Nose: Double

        val buffer2 = ByteArray(1024)
        val packet = DatagramPacket(buffer2, buffer2.size)
        IMUSocket.setSoTimeout(5000)
        //Listen for packet on port 9000

        try {
            IMUSocket.receive(packet)

            notified = false
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

                _self.OperatorLongitude = Longitude
                _self.OperatorLatitude = Latitude
                _self.OperatorNose = Nose
            } catch (e: NumberFormatException) {

                println("[Line: ${LineNumberGetter()}] Caught NumberFormatException.")
                println("[Line: ${LineNumberGetter()}] " + e.message)
                Longitude = 0.0
                Latitude = 0.0
                Nose = 0.0
            }

            return listOf(Longitude, Latitude, Nose)
        } catch (e: SocketTimeoutException){
            if(!notified) {
                println("\nNot receiving own GPS data!")
                println("Ensure Hyper IMU is running properly and communicating on the correct port.")
                println(infoString)
                notified = true
            }
        }

        return listOf(0.0,0.0,0.0)
    }

    /**
     * PRIVATE: This FUNCTION detects if an operator has left the Multicast network.
     */
    private fun operatorTimeOut(_self: opInfo, Name: String) {
        for(key in operators.keys){
            if (operators[key]!!.OperatorName == Name) {
                if(!operators[key]!!.isActive) {
                    operators[key]?.isActive = true
                }
                operators[key]!!.activeTime += 1

                operators[key]!!.offset = _self.activeTime - operators[key]!!.activeTime
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
        buffer3D = alGenBuffers()

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

        //Assigns the source to the specified buffer: buffer3D
        AL10.alSourcei(source, AL10.AL_BUFFER, buffer3D)

        //Sets the gain of the source.
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
                    AL10.alSource3f(source, AL10.AL_POSITION, x * (r / 3), 1f, y)
                }

                //Behind listener
                else if ((azimuth > Quad2 && azimuth < Quad3) || (azimuth >= Quad3 && azimuth < Quad4)){
                    AL10.alSource3f(source, AL10.AL_POSITION, x * (r / 3), 1f, y * r)
                }
            } else break
        }

        //Delete all AL Sources and AL Buffers. This prevents the loss of audio and delay between audio buffers.
        AL10.alDeleteSources(source)
        alDeleteBuffers(buffer3D)
    }


    /**
     * PRIVATE: This FUNCTION will remove an operator and disassociate them from their port if their
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
     * PRIVATE: This FUNCTION will assign each operator their unique Longitude and Latitude data
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
     * PRIVATE: This FUNCTION will allocate an operators Name, Port and IP appropriately according to their received data.
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
     * PRIVATE: This FUNCTION will allocate an operators Name, Port and IP appropriately according to their received data.
     */
    private fun allocateOPS(Name: String, Port: String, IP: String){
        for(i in 0 until potentialOP.size) {
            when (Port.toInt()) {
                incPort + i -> {
                    currentOps[potentialOP[i]] = opInfo(OperatorName = Name)
                    currentOps[potentialOP[i]]?.OperatorPort = Port
                    currentOps[potentialOP[i]]?.OperatorIP = IP
                }
            }
        }
    }

    /**
     * PRIVATE: This FUNCTION will utilize the GPS data held in the operators DATA CLASS to calculate their azimuth.
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
     * PRIVATE: This FUNCTION will utilize the GPS data held in the operators DATA CLASS to calculate their distance. The
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
     * PRIVATE: This FUNCTION notifies the user when a new operator has joined the server and on what port.
     */
    private fun notifyMe(){
        if (currentOps.size < operators.size) {
            println("[Line: ${LineNumberGetter()}] Comparator size: ${comparator}")
            if (portsAudio.size > comparator){
                for (key in operators.keys){
                    if (!currentOps.contains(key)){
                        println("\nA new operator has joined the server!")
                        println("$key:  ${operators[key]?.OperatorName} on Port: ${operators[key]?.OperatorPort}.\n")
                        allocateOPS(operators[key]!!.OperatorName, operators[key]!!.OperatorPort, operators[key]!!.OperatorIP )
                        comparator += 1
                    }
                }
            }
        } else if (currentOps.size > operators.size) {
            if (portsAudio.size < comparator){
                for (key in currentOps.keys){
                    if (!operators.contains(key)){
                        println("\n$key:  ${currentOps[key]?.OperatorName} on Port: ${currentOps[key]?.OperatorPort} has left the server!\n")
                        currentOps.remove(key)
                        comparator -= 1
                    }
                }
            }
        }
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